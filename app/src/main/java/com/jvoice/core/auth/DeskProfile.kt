package com.jvoice.core.auth

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.jvoice.core.firebase.FirebaseAvailability
import kotlinx.coroutines.tasks.await

/**
 * The signed-in staff member's own profile: photo, name, phone and password.
 *
 * Name, phone and photo live on `users/{uid}` - the database rules let an
 * account write those three fields of its own record and nothing else (role
 * and the on/off switch stay admin-only). The photo itself goes to Storage
 * under `staff-avatars/{uid}/`, which only that account may write.
 *
 * Every call returns [Result]; a failure carries a sentence for the screen.
 */
object DeskProfile {

    private const val TAG = "DeskProfile"
    private const val AVATAR_MAX_BYTES = 5L * 1024 * 1024

    data class Profile(
        val uid: String,
        val loginId: String,
        val name: String,
        val phone: String,
        val email: String,
        val avatarUrl: String,
        val role: JvRole?
    )

    private fun userRef(uid: String) = FirebaseDatabase.getInstance().reference.child("users").child(uid)

    private fun uid(): String? =
        if (!FirebaseAvailability.isAvailable) null else FirebaseAuth.getInstance().currentUser?.uid

    suspend fun load(): Result<Profile> {
        val uid = uid() ?: return Result.failure(Exception("You are signed out. Sign in again."))
        return try {
            val s = userRef(uid).get().await()
            Result.success(
                Profile(
                    uid = uid,
                    loginId = s.child("loginId").value?.toString().orEmpty(),
                    name = s.child("name").value?.toString().orEmpty(),
                    phone = s.child("phone").value?.toString().orEmpty(),
                    email = s.child("email").value?.toString().orEmpty(),
                    avatarUrl = s.child("avatarUrl").value?.toString().orEmpty(),
                    role = JvRole.fromCode(s.child("role").value?.toString())
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "load failed: ${e.message}")
            Result.failure(Exception("Could not load your profile - check the internet."))
        }
    }

    suspend fun saveDetails(name: String, phone: String): Result<Unit> {
        val uid = uid() ?: return Result.failure(Exception("You are signed out. Sign in again."))
        val cleanName = name.trim()
        val cleanPhone = phone.trim()
        if (cleanName.length < 2) return Result.failure(Exception("Enter your full name."))
        if (cleanPhone.filter { it.isDigit() }.length < 10) return Result.failure(Exception("Enter a 10-digit mobile number."))
        return try {
            userRef(uid).updateChildren(mapOf("name" to cleanName, "phone" to cleanPhone)).await()
            runCatching {
                FirebaseAuth.getInstance().currentUser
                    ?.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(cleanName).build())?.await()
            }
            SessionStore.updateProfile(name = cleanName, phone = cleanPhone)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "saveDetails failed: ${e.message}")
            Result.failure(Exception("Could not save - check the internet and try again."))
        }
    }

    suspend fun uploadPhoto(context: Context, uri: Uri): Result<String> {
        val uid = uid() ?: return Result.failure(Exception("You are signed out. Sign in again."))
        val resolver = context.contentResolver
        val type = resolver.getType(uri)?.takeIf { it.startsWith("image/") }
            ?: return Result.failure(Exception("That file is not a photo."))
        val size = runCatching {
            resolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
        }.getOrNull() ?: -1L
        if (size > AVATAR_MAX_BYTES) return Result.failure(Exception("The photo must be under 5 MB."))
        return try {
            val ref = FirebaseStorage.getInstance().reference
                .child("staff-avatars/$uid/${System.currentTimeMillis()}.img")
            ref.putFile(uri, StorageMetadata.Builder().setContentType(type).build()).await()
            val url = ref.downloadUrl.await().toString()
            userRef(uid).child("avatarUrl").setValue(url).await()
            SessionStore.updateProfile(avatarUrl = url)
            Result.success(url)
        } catch (e: Exception) {
            Log.w(TAG, "uploadPhoto failed: ${e.message}")
            Result.failure(Exception("Could not upload the photo - check the internet and try again."))
        }
    }

    /**
     * Changes the password. Firebase asks for a recent sign-in before a
     * password change, so the current password is checked first - which is
     * also what stops someone holding an unlocked phone from changing it.
     */
    suspend fun changePassword(current: String, new: String): Result<Unit> {
        val user = if (FirebaseAvailability.isAvailable) FirebaseAuth.getInstance().currentUser else null
        val email = user?.email ?: return Result.failure(Exception("You are signed out. Sign in again."))
        if (new.length < 8) return Result.failure(Exception("The new password must be at least 8 characters."))
        if (new == current) return Result.failure(Exception("The new password is the same as the current one."))
        return try {
            user.reauthenticate(EmailAuthProvider.getCredential(email, current)).await()
            user.updatePassword(new).await()
            Result.success(Unit)
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Result.failure(Exception("The current password is wrong."))
        } catch (e: FirebaseAuthWeakPasswordException) {
            Result.failure(Exception("That password is too weak - use at least 8 characters."))
        } catch (e: Exception) {
            Log.w(TAG, "changePassword failed: ${e.message}")
            Result.failure(Exception("Could not change the password - check the internet and try again."))
        }
    }
}
