package ru.scalejournal.util

import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {

    data class Info(val tag: String, val apkUrl: String, val sizeBytes: Long)

    fun latest(token: String, onResult: (Result<Info?>) -> Unit) {
        Thread {
            var connection: HttpURLConnection? = null
            try {
                val repo = "${LogConfig.REPO_OWNER}/${LogConfig.REPO_NAME}"
                val url = URL("https://api.github.com/repos/$repo/releases/latest")
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    if (token.isNotBlank()) setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "scalejournal-android")
                    connectTimeout = 15000
                    readTimeout = 15000
                }
                when (val code = connection.responseCode) {
                    404 -> onResult(Result.success(null))
                    in 200..299 -> {
                        val json = JSONObject(connection.inputStream.bufferedReader().readText())
                        val tag = json.getString("tag_name")
                        val assets = json.getJSONArray("assets")
                        var apkUrl: String? = null
                        var size = 0L
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            if (asset.getString("name").endsWith(".apk", ignoreCase = true)) {
                                apkUrl = asset.getString("browser_download_url")
                                size = asset.getLong("size")
                                break
                            }
                        }
                        if (apkUrl == null) {
                            onResult(Result.success(null))
                        } else {
                            onResult(Result.success(Info(tag, apkUrl, size)))
                        }
                    }
                    else -> {
                        val error = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                        onResult(Result.failure(IOException("GitHub $code: ${error.take(200)}")))
                    }
                }
            } catch (e: Exception) {
                onResult(Result.failure(e))
            } finally {
                connection?.disconnect()
            }
        }.start()
    }

    fun download(token: String, url: String, expectedSize: Long, target: File, onResult: (Result<File>) -> Unit) {
        Thread {
            var connection: HttpURLConnection? = null
            try {
                target.parentFile?.mkdirs()
                target.delete()
                connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    if (token.isNotBlank()) setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("User-Agent", "scalejournal-android")
                    connectTimeout = 15000
                    readTimeout = 120000
                }
                if (connection.responseCode !in 200..299) {
                    throw IOException("HTTP ${connection.responseCode}")
                }
                connection.inputStream.use { input ->
                    target.outputStream().use { output -> input.copyTo(output, 1 shl 16) }
                }
                if (expectedSize > 0 && target.length() != expectedSize) {
                    throw IOException("Размер файла не совпадает: ${target.length()} вместо $expectedSize байт")
                }
                onResult(Result.success(target))
            } catch (e: Exception) {
                target.delete()
                onResult(Result.failure(e))
            } finally {
                connection?.disconnect()
            }
        }.start()
    }
}
