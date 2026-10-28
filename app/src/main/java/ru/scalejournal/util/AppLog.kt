package ru.scalejournal.util

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

object AppLog {
    private const val MAX_LINES = 4000
    private const val MAX_FILE_BYTES = 1_000_000L
    private val lines = ArrayDeque<String>()
    private var file: File? = null
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    fun init(context: Context) {
        file = File(context.filesDir, "scalejournal.log")
        file?.let { f ->
            runCatching {
                if (f.length() > MAX_FILE_BYTES) f.writeText("")
                if (f.length() > 0) {
                    f.readText().lines().takeLast(800).forEach { lines.addLast(it) }
                }
            }
        }
        log("APP", "=== запуск ===")
    }

    @Synchronized
    fun log(tag: String, message: String) {
        val line = "${fmt.format(Date())} $tag: $message"
        lines.addLast(line)
        while (lines.size > MAX_LINES) lines.removeFirst()
        file?.let { f -> runCatching { f.appendText(line + "\n") } }
        Log.d("Scale/$tag", message)
    }

    @Synchronized
    fun dump(header: String): String = header + "\n" + lines.joinToString("\n") + "\n"
}

fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it) }
