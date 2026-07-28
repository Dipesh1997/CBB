package g.p.cbb.utils

import android.content.Context
import android.util.Log
import g.p.cbb.data.AppDatabase
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object BackupManager {
    private const val DB_NAME = "cbb_database"

    fun exportDatabase(context: Context, isAuto: Boolean = false): Boolean {
        return try {
            val dbFile = context.getDatabasePath(DB_NAME)
            if (!dbFile.exists()) return false

            val backupFolder = StorageManager.getBackupFolder(context)
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = if (isAuto) "auto_daily_backup.db" else "manual_backup_$timestamp.db"
            
            val destFile = File(backupFolder, fileName)
            copyFile(dbFile, destFile)
            
            // Copy SHM and WAL files if they exist
            val shmFile = File(dbFile.path + "-shm")
            if (shmFile.exists()) copyFile(shmFile, File(destFile.path + "-shm"))
            
            val walFile = File(dbFile.path + "-wal")
            if (walFile.exists()) copyFile(walFile, File(destFile.path + "-wal"))

            // Scan file for visibility
            android.media.MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), null, null)

            Log.d("BackupManager", "Database exported to ${destFile.absolutePath}")
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun importLatestDatabase(context: Context, database: AppDatabase): String? {
        return try {
            val backupFolder = StorageManager.getBackupFolder(context)
            val files = backupFolder.listFiles { file -> file.name.endsWith(".db") }
            if (files.isNullOrEmpty()) return "No backup files found in udaari/backups"

            val latestBackup = files.maxByOrNull { it.lastModified() } ?: return "No valid backup found"

            // Close current database
            database.close()

            val dbFile = context.getDatabasePath(DB_NAME)
            copyFile(latestBackup, dbFile)

            // Copy SHM and WAL if available in backup
            val shmSource = File(latestBackup.path + "-shm")
            if (shmSource.exists()) copyFile(shmSource, File(dbFile.path + "-shm"))
            
            val walSource = File(latestBackup.path + "-wal")
            if (walSource.exists()) copyFile(walSource, File(dbFile.path + "-wal"))

            Log.d("BackupManager", "Database imported from ${latestBackup.absolutePath}")
            null // Success
        } catch (e: Exception) {
            e.printStackTrace()
            "Error during import: ${e.message}"
        }
    }

    private fun copyFile(source: File, dest: File) {
        FileInputStream(source).use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output)
            }
        }
    }
}
