package de.dh.raaps.common.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * A simple, thread-safe logger that persists logs to a file in the app's private storage.
 * It uses a single-threaded executor to ensure that file I/O doesn't block the calling thread
 * and to prevent concurrent write issues.
 */
object PersistentLogger {
    private const val LOG_FILE_NAME = "persistent_debug_logs.txt"
    private var logFile: File? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    /**
     * Initializes the logger with the application context.
     * Should be called in Application.onCreate().
     */
    fun init(context: Context) {
        if (logFile == null) {
            // Use external files dir (Download subfolder) so it's accessible via USB/File Explorer
            // without needing storage permissions.
            val baseDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                ?: context.filesDir
            
            logFile = File(baseDir, LOG_FILE_NAME)
            log("PersistentLogger", "------------------------------------------------------------")
            log("PersistentLogger", "Logger initialized at ${logFile?.absolutePath}")
        }
    }

    /**
     * Logs a message to both Logcat and the persistent log file.
     */
    fun log(tag: String, message: String) {
        // Log to Logcat immediately
        Log.d(tag, message)

        // Queue the file write to avoid blocking the caller
        executor.execute {
            try {
                logFile?.let { file ->
                    val timestamp = timeFormat.format(Date())
                    FileWriter(file, true).use { writer ->
                        writer.write("[$timestamp] [$tag] $message\n")
                    }
                }
            } catch (e: Exception) {
                Log.e("PersistentLogger", "Failed to write to log file", e)
            }
        }
    }

    /**
     * Clears the log file.
     */
    fun clearLogs() {
        executor.execute {
            logFile?.delete()
            log("PersistentLogger", "Logs cleared")
        }
    }

    /**
     * Returns the log file for sharing or reading.
     */
    fun getLogFile(): File? = logFile
}
