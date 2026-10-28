package ru.scalejournal.util

import android.util.Base64
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object GitHubLogUploader {

    fun upload(token: String, fileName: String, content: String, onResult: (Result<String>) -> Unit) {
        Thread {
            var connection: HttpURLConnection? = null
            try {
                val repo = "${LogConfig.REPO_OWNER}/${LogConfig.REPO_NAME}"
                val path = "logs/$fileName"
                val url = URL("https://api.github.com/repos/$repo/contents/$path")
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "PUT"
                    doOutput = true
                    setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "scalejournal-android")
                    setRequestProperty("Content-Type", "application/json")
                    connectTimeout = 15000
                    readTimeout = 15000
                }
                val body = JSONObject()
                    .put("message", "debug log $fileName")
                    .put(
                        "content",
                        Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP),
                    )
                    .toString()
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                if (code == 200 || code == 201) {
                    onResult(Result.success("Лог отправлен: $repo/$path"))
                } else {
                    val error = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    onResult(Result.failure(IOException("GitHub $code: ${error.take(300)}")))
                }
            } catch (e: Exception) {
                onResult(Result.failure(e))
            } finally {
                connection?.disconnect()
            }
        }.start()
    }
}
