package com.jvoice.core.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.jvoice.core.firebase.FirebaseAvailability
import com.jvoice.core.i18n.LocalizedText
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The Firestore handle, the collection names, and the codec helpers every
 * repository shares.
 *
 * ## Offline persistence is on
 *
 * Firestore caches locally and serves reads from that cache when the network is
 * gone, queueing writes until it returns. For a news app used by district
 * reporters on patchy mobile data that is not a nicety — a story filed in a
 * village with no signal has to survive until there is one. Enabled explicitly
 * rather than left to the SDK default so it cannot change under us.
 *
 * ## Why hand-written codecs instead of Firestore's POJO mapping
 *
 * `toObject(NewsArticle::class.java)` would need every model to have a no-arg
 * constructor and mutable fields, which means unpicking the data classes the rest
 * of the app relies on. It also cannot express [LocalizedText] as a nested map
 * without an annotation dance, and it fails silently to null when a field is
 * missing — exactly the case that matters when the schema evolves.
 *
 * So each model gets an explicit `toMap` / `fromMap`. More code, but every field
 * has a stated default and a wrong read is visible in one place.
 */
object Firestore {

    private const val TAG = "Firestore"

    /* ------------------------------------------------------- collection names */

    // news
    const val ARTICLES = "articles"
    const val CATEGORIES = "categories"
    const val NEWS_NOTIFICATIONS = "newsNotifications"

    // study
    const val SUBJECTS = "subjects"
    const val TOPICS = "topics"
    const val EXAM_TRACKS = "examTracks"
    const val STUDY_ARTICLES = "studyArticles"
    const val QUESTIONS = "questions"
    const val QUIZZES = "quizzes"
    const val EXAMS = "exams"

    @Volatile
    private var instance: FirebaseFirestore? = null

    /**
     * The Firestore handle, or null when Firebase is not configured.
     *
     * Null rather than throwing: callers are repositories that must degrade to an
     * empty list, not crash a screen.
     */
    fun db(): FirebaseFirestore? {
        if (!FirebaseAvailability.isAvailable) return null
        instance?.let { return it }
        return synchronized(this) {
            instance ?: runCatching {
                FirebaseFirestore.getInstance().apply {
                    firestoreSettings = FirebaseFirestoreSettings.Builder()
                        // Unbounded local cache. The whole dataset here is text -
                        // a few thousand articles and questions - so there is no
                        // reason to evict any of it on a modern phone.
                        .setLocalCacheSettings(
                            PersistentCacheSettings.newBuilder()
                                .setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                                .build()
                        )
                        .build()
                }
            }.onFailure {
                Log.e(TAG, "Could not obtain Firestore: ${it.message}", it)
            }.getOrNull().also { instance = it }
        }
    }

    /**
     * Attaches a collection listener that maps each document through [decode] and
     * hands the whole list to [onChange].
     *
     * Returns null when Firebase is unavailable, in which case [onChange] is never
     * called and the caller keeps its initial empty list.
     *
     * Decoding failures are logged and skipped rather than failing the batch: one
     * malformed document written by hand in the console must not blank an entire
     * screen.
     */
    fun <T> listen(
        collection: String,
        decode: (id: String, data: Map<String, Any?>) -> T?,
        onChange: (List<T>) -> Unit
    ): ListenerRegistration? = listen(collection, decode, onChange, narrow = null)

    /**
     * As above, but lets the caller narrow the query.
     *
     * ### Why narrowing is not optional for some collections
     *
     * Firestore security rules are a condition the query must *satisfy*, not a
     * filter applied to the results. A rule like
     *
     *     allow read: if resource.data.status == 'PUBLISHED' || isNewsDesk()
     *
     * does NOT quietly hand an anonymous reader the published subset. Firestore
     * has to prove, from the query alone, that every document it could return
     * satisfies the rule — and an unfiltered listen on the whole collection
     * proves nothing, so the entire query is rejected with
     * "Missing or insufficient permissions".
     *
     * The caller therefore has to ask for exactly what it is allowed to see:
     * `whereEqualTo("status", "PUBLISHED")` for an anonymous reader, and the
     * unfiltered collection only once signed in as desk.
     */
    fun <T> listen(
        collection: String,
        decode: (id: String, data: Map<String, Any?>) -> T?,
        onChange: (List<T>) -> Unit,
        narrow: ((Query) -> Query)?
    ): ListenerRegistration? {
        val db = db() ?: return null
        val base: Query = db.collection(collection)
        val query = narrow?.invoke(base) ?: base
        return query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "$collection listener failed: ${error.message}")
                return@addSnapshotListener
            }
            onChange(decodeAll(collection, snapshot, decode))
        }
    }

    private fun <T> decodeAll(
        collection: String,
        snapshot: QuerySnapshot?,
        decode: (id: String, data: Map<String, Any?>) -> T?
    ): List<T> = snapshot?.documents.orEmpty().mapNotNull { doc ->
        try {
            decode(doc.id, doc.data.orEmpty())
        } catch (e: Exception) {
            Log.w(TAG, "Skipping malformed $collection/${doc.id}: ${e.message}")
            null
        }
    }

    /** Fire-and-forget write. Firestore queues it offline; failures are logged. */
    fun set(collection: String, id: String, data: Map<String, Any?>) {
        val db = db() ?: return
        db.collection(collection).document(id).set(data)
            .addOnFailureListener { Log.e(TAG, "set $collection/$id failed: ${it.message}") }
    }

    /**
     * A write the caller waits on, for the places a person is told "saved".
     *
     * A rules denial or a bad document comes back as a failure with the reason.
     * With no connection the write stays queued and is sent when the phone is
     * back online; that is reported as [WriteResult.Queued], not as a failure,
     * because the story is not lost.
     */
    suspend fun setAndWait(collection: String, id: String, data: Map<String, Any?>): WriteResult {
        val db = db() ?: return WriteResult.Failed("Not connected to J Voice.")
        return try {
            val done = withTimeoutOrNull(15_000) {
                db.collection(collection).document(id).set(data).await()
                true
            }
            if (done == true) WriteResult.Saved else WriteResult.Queued
        } catch (e: Exception) {
            Log.e(TAG, "set $collection/$id failed: ${e.message}")
            WriteResult.Failed(
                if (e.message?.contains("PERMISSION_DENIED") == true)
                    "This account is not allowed to save stories. Contact the admin."
                else e.message ?: "Could not save."
            )
        }
    }

    sealed interface WriteResult {
        data object Saved : WriteResult
        data object Queued : WriteResult
        data class Failed(val message: String) : WriteResult
    }

    /** [update] that the caller waits on - see [setAndWait] for what each result means. */
    suspend fun updateAndWait(collection: String, id: String, fields: Map<String, Any?>): WriteResult {
        val db = db() ?: return WriteResult.Failed("Not connected to J Voice.")
        return try {
            val done = withTimeoutOrNull(15_000) {
                db.collection(collection).document(id).update(fields).await()
                true
            }
            if (done == true) WriteResult.Saved else WriteResult.Queued
        } catch (e: Exception) {
            Log.e(TAG, "update $collection/$id failed: ${e.message}")
            WriteResult.Failed(
                if (e.message?.contains("PERMISSION_DENIED") == true)
                    "This account is not allowed to do that. Sign out and in again, or contact the admin."
                else e.message ?: "Could not save."
            )
        }
    }

    /** Partial update. Use for single-field flips so concurrent edits do not clobber. */
    fun update(collection: String, id: String, fields: Map<String, Any?>) {
        val db = db() ?: return
        db.collection(collection).document(id).update(fields)
            .addOnFailureListener { Log.e(TAG, "update $collection/$id failed: ${it.message}") }
    }

    fun delete(collection: String, id: String) {
        val db = db() ?: return
        db.collection(collection).document(id).delete()
            .addOnFailureListener { Log.e(TAG, "delete $collection/$id failed: ${it.message}") }
    }

    /**
     * Deletes a document together with the named subcollections under it.
     *
     * Firestore does not cascade: deleting `articles/x` leaves
     * every document under `articles/x/comments` in place, orphaned and still billed. So the
     * children are fetched and removed in batches first, and the document
     * itself goes last - if anything fails part-way the story is still there
     * to retry against, rather than a headless pile of comments.
     */
    fun deleteWithChildren(collection: String, id: String, vararg subcollections: String) {
        val db = db() ?: return
        val docRef = db.collection(collection).document(id)
        val fetches = subcollections.map { docRef.collection(it).get() }
        com.google.android.gms.tasks.Tasks.whenAllSuccess<com.google.firebase.firestore.QuerySnapshot>(fetches)
            .addOnSuccessListener { snapshots ->
                val docs = snapshots.flatMap { it.documents }
                // A batch holds 500 writes; chunk so a busy story still clears.
                val batches = docs.chunked(450).map { chunk ->
                    db.batch().also { b -> chunk.forEach { b.delete(it.reference) } }.commit()
                }
                com.google.android.gms.tasks.Tasks.whenAll(batches)
                    .addOnSuccessListener { docRef.delete() }
                    .addOnFailureListener { Log.e(TAG, "delete children of $collection/$id failed: ${it.message}") }
            }
            .addOnFailureListener { Log.e(TAG, "fetch children of $collection/$id failed: ${it.message}") }
    }

    /** A server-generated document id, for creating before writing. */
    fun newId(collection: String): String =
        db()?.collection(collection)?.document()?.id
            ?: "local_" + System.currentTimeMillis()
}

/* ============================================================ codec helpers */

/**
 * [LocalizedText] as a Firestore map.
 *
 * Stored as `{en, te}` rather than two sibling fields (`titleEn`, `titleTe`)
 * because it keeps the pair together: a query or a rule that touches the field
 * gets both halves, and adding a third language later is a schema addition rather
 * than a rename of every field in the collection.
 */
fun LocalizedText.toMap(): Map<String, Any?> = mapOf("en" to en, "te" to te)

/**
 * Reads a [LocalizedText] back.
 *
 * A missing field becomes [LocalizedText.EMPTY], and a plain string becomes an
 * English-only value — the second case matters because it is what a human typing
 * into the Firestore console produces, and the alternative is losing their text.
 */
fun localizedFrom(value: Any?): LocalizedText = when (value) {
    null -> LocalizedText.EMPTY
    is String -> LocalizedText(en = value, te = "")
    is Map<*, *> -> LocalizedText(
        en = value["en"] as? String ?: "",
        te = value["te"] as? String ?: ""
    )
    else -> LocalizedText.EMPTY
}

fun List<LocalizedText>.toMapList(): List<Map<String, Any?>> = map { it.toMap() }

fun localizedListFrom(value: Any?): List<LocalizedText> =
    (value as? List<*>).orEmpty().map { localizedFrom(it) }

/* -------------------------------------------------------- scalar field readers */

fun Map<String, Any?>.str(key: String, default: String = ""): String =
    this[key] as? String ?: default

fun Map<String, Any?>.strOrNull(key: String): String? =
    (this[key] as? String)?.takeIf { it.isNotBlank() }

fun Map<String, Any?>.bool(key: String, default: Boolean = false): Boolean =
    when (val v = this[key]) {
        is Boolean -> v
        is Number -> v.toInt() != 0
        is String -> v.equals("true", ignoreCase = true) || v == "1"
        else -> default
    }

fun Map<String, Any?>.int(key: String, default: Int = 0): Int =
    (this[key] as? Number)?.toInt() ?: default

fun Map<String, Any?>.long(key: String, default: Long = 0L): Long =
    (this[key] as? Number)?.toLong() ?: default

fun Map<String, Any?>.longOrNull(key: String): Long? = (this[key] as? Number)?.toLong()

fun Map<String, Any?>.strList(key: String): List<String> =
    (this[key] as? List<*>).orEmpty().mapNotNull { it as? String }

/**
 * Reads an enum by name, falling back to [default] for an unknown value.
 *
 * Unknown rather than invalid on purpose: a status string this build does not
 * recognise means the document was written by a newer version, and treating it as
 * the default beats dropping the document entirely.
 */
inline fun <reified E : Enum<E>> Map<String, Any?>.enum(key: String, default: E): E {
    val raw = this[key] as? String ?: return default
    return enumValues<E>().firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: default
}
