package com.nuvio.tv.backup

import android.app.Activity
import android.os.Bundle
import android.util.Log
import org.json.JSONObject
import java.io.File

/** Shell-only maintenance entry point. No credentials or file contents are logged. */
class AppDataBackupActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val backupDir = getExternalFilesDir(null)?.let { File(it, BACKUP_DIRECTORY) }
        if (backupDir == null) {
            Log.e(TAG, "External app storage is unavailable")
            finish()
            return
        }
        backupDir.mkdirs()

        val action = intent?.action
        Thread({
            val status = try {
                val result = when (action) {
                    ACTION_EXPORT -> AppDataBackup.export(this, File(backupDir, ARCHIVE_NAME))
                    ACTION_VERIFY -> AppDataBackup.verify(this, File(backupDir, ARCHIVE_NAME))
                    ACTION_RESTORE -> AppDataBackup.restore(this, File(backupDir, RESTORE_NAME))
                    else -> error("Unknown backup action")
                }
                result.put("ok", true).put("action", action)
            } catch (e: Exception) {
                Log.e(TAG, "Backup maintenance failed", e)
                JSONObject().put("ok", false).put("action", action)
                    .put("error", e.message ?: e.javaClass.simpleName)
            }
            try {
                val temporary = File(backupDir, "status.json.partial")
                temporary.writeText(status.toString())
                check(temporary.renameTo(File(backupDir, "status.json"))) {
                    "Could not publish backup status"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Could not write backup status", e)
            }
            runOnUiThread { finish() }
        }, "nuvio-backup").start()
    }

    companion object {
        const val ACTION_EXPORT = "com.nuvio.tv.backup.EXPORT"
        const val ACTION_VERIFY = "com.nuvio.tv.backup.VERIFY"
        const val ACTION_RESTORE = "com.nuvio.tv.backup.RESTORE"
        const val BACKUP_DIRECTORY = "nuvio-backup"
        const val ARCHIVE_NAME = "nuvio-app-data.zip"
        const val RESTORE_NAME = "restore.zip"
        private const val TAG = "AppDataBackup"
    }
}
