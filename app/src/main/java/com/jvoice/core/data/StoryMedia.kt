package com.jvoice.core.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.jvoice.core.firebase.FirebaseAvailability
import kotlinx.coroutines.tasks.await

/**
 * What the reporter's story form needs from the server beyond the story itself:
 * uploading photos and videos from the phone, and translating the copy between
 * Telugu and English.
 *
 * Uploads go to `news-photos/` and `news-videos/` - the same folders, size
 * limits and download-URL scheme the console uses (J Voice web/src/store/
 * media.js) - so a story filed from either place looks the same to readers.
 */
object StoryMedia {

    private const val TAG = "StoryMedia"

    const val PHOTOS = "news-photos"
    const val VIDEOS = "news-videos"

    private const val PHOTO_MAX_BYTES = 15L * 1024 * 1024
    private const val VIDEO_MAX_BYTES = 300L * 1024 * 1024

    /**
     * Uploads one picked file and returns its download URL.
     *
     * The content type comes from the phone's own record of the file, not the
     * name: the storage rules check it (`image/...` or `video/...`), and a
     * gallery file often has no useful extension.
     *
     * @param onProgress fraction 0..1 as bytes go up.
     */
    suspend fun upload(
        context: Context,
        uri: Uri,
        folder: String,
        onProgress: (Float) -> Unit = {}
    ): Result<String> {
        if (!FirebaseAvailability.isAvailable) return Result.failure(Exception("Not connected to J Voice."))
        val resolver = context.contentResolver
        val isPhoto = folder == PHOTOS
        val type = resolver.getType(uri)?.takeIf { it.startsWith(if (isPhoto) "image/" else "video/") }
            ?: return Result.failure(Exception(if (isPhoto) "That file is not a photo." else "That file is not a video."))

        val (name, size) = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { c ->
                if (c.moveToFirst()) {
                    (c.getString(0) ?: "file") to (if (c.isNull(1)) -1L else c.getLong(1))
                } else {
                    "file" to -1L
                }
            } ?: ("file" to -1L)
        val max = if (isPhoto) PHOTO_MAX_BYTES else VIDEO_MAX_BYTES
        if (size > max) {
            return Result.failure(Exception(if (isPhoto) "Photos must be under 15 MB." else "Videos must be under 300 MB."))
        }

        val safe = name.replace(Regex("[^A-Za-z0-9._-]+"), "_").takeLast(80)
        val ref = FirebaseStorage.getInstance().reference.child("$folder/${System.currentTimeMillis()}-$safe")
        return try {
            val task = ref.putFile(uri, StorageMetadata.Builder().setContentType(type).build())
            task.addOnProgressListener { snap ->
                if (snap.totalByteCount > 0) onProgress(snap.bytesTransferred.toFloat() / snap.totalByteCount)
            }
            task.await()
            Result.success(ref.downloadUrl.await().toString())
        } catch (e: Exception) {
            Log.w(TAG, "upload to $folder failed: ${e.message}")
            val denied = e.message?.contains("permission", ignoreCase = true) == true
            Result.failure(
                Exception(
                    if (denied) "This account is not allowed to upload. Sign out and in again, or ask the admin."
                    else "Upload failed - check the internet and try again."
                )
            )
        }
    }

    /**
     * Translates [texts] from [from] to [to] ("en" / "te") through the
     * translateText function. Blank entries come back blank.
     */
    suspend fun translate(texts: List<String>, from: String, to: String): Result<List<String>> {
        if (!FirebaseAvailability.isAvailable) return Result.failure(Exception("Not connected to J Voice."))
        return try {
            val result = FirebaseFunctions.getInstance("asia-south1")
                .getHttpsCallable("translateText")
                .call(mapOf("texts" to texts, "from" to from, "to" to to))
                .await()
            val out = ((result.getData() as? Map<*, *>)?.get("texts") as? List<*>)
                ?.map { it?.toString().orEmpty() }
                ?: return Result.failure(Exception("Translation came back empty."))
            Result.success(out)
        } catch (e: Exception) {
            Log.w(TAG, "translate failed: ${e.message}")
            val message = when {
                e is FirebaseFunctionsException && e.code == FirebaseFunctionsException.Code.UNAVAILABLE ->
                    "Translation is not available right now. Try again."
                e is FirebaseFunctionsException && !e.message.isNullOrBlank() -> e.message!!
                else -> "Could not translate - check the internet and try again."
            }
            Result.failure(Exception(message))
        }
    }
}
