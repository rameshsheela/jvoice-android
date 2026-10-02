package com.jvoice.core.data

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.jvoice.core.auth.JvRole
import com.jvoice.core.firebase.FirebaseAvailability
import com.jvoice.news.data.model.Reporter
import com.jvoice.news.data.model.User
import com.jvoice.news.data.model.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The desk's people, read from the Realtime Database `users/` tree.
 *
 * ## Why here and not Firestore
 *
 * The accounts already exist in RTDB — that is where authentication put them,
 * along with `role`, `isLogin` and `forceLogoutAt`. Mirroring them into Firestore
 * as well would mean two copies of the same list that can disagree about who is a
 * reporter, so the people screens read the original.
 *
 * ## This list is usually empty, and that is correct
 *
 * `users/` denies collection reads to everyone except news/study/super admins
 * (see `database.rules.json`). A reporter attaching this listener gets a
 * permission error, the list stays empty, and the screens that use it are screens
 * a reporter cannot reach anyway. The failure is logged at debug level rather than
 * warned about, because for most signed-in users it is the expected outcome.
 */
object StaffDirectory {

    private const val TAG = "StaffDirectory"

    private val _staff = MutableStateFlow<List<User>>(emptyList())

    /** Every desk account, as the news module's [User]. */
    val staff: StateFlow<List<User>> = _staff.asStateFlow()

    private val _reporters = MutableStateFlow<List<Reporter>>(emptyList())

    /** The reporters among them, as the news module's [Reporter]. */
    val reporters: StateFlow<List<Reporter>> = _reporters.asStateFlow()

    private var listener: ValueEventListener? = null

    fun start() {
        if (listener != null || !FirebaseAvailability.isAvailable) return
        val ref = FirebaseDatabase.getInstance().reference.child("users")

        val l = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val people = snapshot.children.mapNotNull { toUser(it) }
                _staff.value = people
                _reporters.value = people
                    .filter { it.role == UserRole.REPORTER }
                    .map {
                        Reporter(
                            userId = it.id,
                            name = it.name,
                            assignedLocation = it.location,
                            isActive = it.isActive,
                            avatarUrl = it.avatarUrl,
                            loginId = it.loginId
                        )
                    }
            }

            override fun onCancelled(error: DatabaseError) {
                // Expected for non-admins - see the class note.
                Log.d(TAG, "users/ not readable for this role: ${error.message}")
                _staff.value = emptyList()
                _reporters.value = emptyList()
            }
        }
        ref.addValueEventListener(l)
        listener = l
    }

    private fun toUser(snapshot: DataSnapshot): User? {
        val uid = snapshot.key ?: return null
        val roleCode = snapshot.child("role").value?.toString()
        // A record whose role does not resolve to a news-module role is not
        // dropped: it is a real account, just one this module does not surface.
        // Returning null keeps it out of the news people screens.
        val newsRole = JvRole.fromCode(roleCode)?.newsRole ?: return null
        return User(
            id = uid,
            name = snapshot.child("name").value?.toString()
                ?: snapshot.child("loginId").value?.toString()
                ?: uid,
            email = snapshot.child("email").value?.toString().orEmpty(),
            phone = snapshot.child("phone").value?.toString() ?: "",
            role = newsRole,
            location = snapshot.child("location").value?.toString() ?: "",
            avatarUrl = snapshot.child("avatarUrl").value?.toString().orEmpty(),
            isActive = when (val v = snapshot.child("isLogin").value) {
                null -> true
                is Boolean -> v
                else -> v.toString() != "false"
            },
            joinedOn = snapshot.child("joinedOn").value?.toString() ?: "",
            loginId = snapshot.child("loginId").value?.toString().orEmpty()
        )
    }

    /** Admin action: flip an account's kill-switch. */
    fun setActive(uid: String, active: Boolean) {
        if (!FirebaseAvailability.isAvailable) return
        FirebaseDatabase.getInstance().reference
            .child("users").child(uid).child("isLogin").setValue(active)
            .addOnFailureListener { Log.w(TAG, "setActive failed: ${it.message}") }
    }

    /** Admin action: change a role. Rules permit this for super_admin only. */
    fun setRole(uid: String, role: JvRole) {
        if (!FirebaseAvailability.isAvailable) return
        FirebaseDatabase.getInstance().reference
            .child("users").child(uid).child("role").setValue(role.code)
            .addOnFailureListener { Log.w(TAG, "setRole failed: ${it.message}") }
    }

    /** Admin action: end every live session for an account, immediately. */
    fun forceLogout(uid: String) {
        if (!FirebaseAvailability.isAvailable) return
        FirebaseDatabase.getInstance().reference
            .child("users").child(uid).child("forceLogoutAt")
            .setValue(System.currentTimeMillis())
            .addOnFailureListener { Log.w(TAG, "forceLogout failed: ${it.message}") }
    }

    fun updateProfile(uid: String, name: String, email: String, location: String) {
        if (!FirebaseAvailability.isAvailable) return
        FirebaseDatabase.getInstance().reference.child("users").child(uid)
            .updateChildren(mapOf("name" to name, "email" to email, "location" to location))
            .addOnFailureListener { Log.w(TAG, "updateProfile failed: ${it.message}") }
    }
}
