package com.jvoice.core.reader

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.jvoice.core.firebase.FirebaseAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Other readers' current display names, by device id, live from
 * `readers/{deviceId}/name`.
 *
 * A comment stores the name its author had *when they posted*. That snapshot
 * is what the desk sees and what an offline screen falls back to, but it is
 * not what is shown when a fresher name is known: the comments screen asks
 * here for every device in view, and a reader who renames themselves is
 * renamed on every comment they ever made, on every phone, without a single
 * comment document being rewritten. Rewriting would have meant letting
 * anonymous clients edit comment documents, which is a rule nobody wants.
 *
 * One value listener per device, kept for the life of the process. The set is
 * as large as the distinct commenters a reader has scrolled past, which is
 * small, and RTDB listeners are cheap.
 */
object ReaderDirectory {

    private const val TAG = "ReaderDirectory"
    private const val NODE = "readers"

    private val _names = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Device id to current name. Absent while unknown or blank on the server. */
    val names: StateFlow<Map<String, String>> = _names.asStateFlow()

    private val listeners = mutableMapOf<String, Pair<DatabaseReference, ValueEventListener>>()

    /** Starts following every id in [deviceIds] that is not already followed. */
    fun watch(deviceIds: Collection<String>) {
        if (!FirebaseAvailability.isAvailable) return
        val root = FirebaseDatabase.getInstance().reference.child(NODE)
        for (id in deviceIds) {
            if (id.isBlank() || id == "unknown" || id in listeners) continue
            val ref = root.child(id).child("name")
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val name = snapshot.getValue(String::class.java).orEmpty().trim()
                    _names.update { if (name.isEmpty()) it - id else it + (id to name) }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.w(TAG, "name for $id unreadable: ${error.message}")
                }
            }
            ref.addValueEventListener(listener)
            listeners[id] = ref to listener
        }
    }
}
