package com.jvoice.core.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.jvoice.core.firebase.FirebaseAvailability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * The AI Shorts studio server - the studioApi Cloud Function behind
 * jvoicetelugu.com/api/... (firebase/functions/studio.js). The same server the
 * website's studio uses: script (OpenAI), voice and voice cloning
 * (ElevenLabs), talking avatar (HeyGen), stock visuals (Pexels) and the MP4
 * render (Creatomate). Every call carries the signed-in staff member's
 * Firebase ID token; the server refuses anyone who is not J Voice staff.
 */
object StudioApi {

    private const val TAG = "StudioApi"
    private const val BASE = "https://jvoicetelugu.com"

    /** POSTs [body] (or GETs when null) and returns the JSON answer, or a failure with the server's sentence. */
    suspend fun call(path: String, body: JSONObject? = null, timeoutMs: Int = 120_000): Result<JSONObject> {
        if (!FirebaseAvailability.isAvailable) return Result.failure(Exception("Not connected to J Voice."))
        val token = runCatching {
            FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token
        }.getOrNull() ?: return Result.failure(Exception("You are signed out. Sign in again."))

        return withContext(Dispatchers.IO) {
            try {
                val conn = (URL(BASE + path).openConnection() as HttpURLConnection).apply {
                    requestMethod = if (body == null) "GET" else "POST"
                    connectTimeout = 20_000
                    readTimeout = timeoutMs
                    setRequestProperty("Authorization", "Bearer $token")
                    if (body != null) {
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json")
                    }
                }
                if (body != null) conn.outputStream.use { it.write(body.toString().toByteArray()) }
                val code = conn.responseCode
                val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                val json = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
                if (code in 200..299) Result.success(json)
                else Result.failure(Exception(json.optString("error").ifBlank { "J Voice studio did not answer ($code)." }))
            } catch (e: Exception) {
                Log.w(TAG, "$path failed: ${e.message}")
                Result.failure(Exception("No connection to the J Voice studio. Check the internet and try again."))
            }
        }
    }
}
