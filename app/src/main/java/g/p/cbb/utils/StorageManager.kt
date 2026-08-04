package g.p.cbb.utils

import android.content.Context
import android.os.Environment
import java.io.File

object StorageManager {
    private const val ROOT_FOLDER = "udaari"
    private const val BACKUP_FOLDER = "backups"
    private const val STATEMENT_FOLDER = "statements"
    private const val INVOICE_FOLDER = "invoices"
    private const val ATTACHMENT_FOLDER = "attachments"
    private const val PROFILE_FOLDER = "profiles"

    fun getUdaariRoot(context: Context): File {
        val publicDocs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        var udaariRoot = File(publicDocs, ROOT_FOLDER)
        try {
            if (!udaariRoot.exists()) {
                val created = udaariRoot.mkdirs()
                if (!created) {
                    val appDocs = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                    udaariRoot = File(appDocs ?: context.filesDir, ROOT_FOLDER)
                    if (!udaariRoot.exists()) {
                        udaariRoot.mkdirs()
                    }
                }
            }
        } catch (e: Exception) {
            val appDocs = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            udaariRoot = File(appDocs ?: context.filesDir, ROOT_FOLDER)
            if (!udaariRoot.exists()) {
                udaariRoot.mkdirs()
            }
        }
        return udaariRoot
    }

    fun getBackupFolder(context: Context): File = getSubFolder(context, BACKUP_FOLDER)
    fun getStatementFolder(context: Context): File = getSubFolder(context, STATEMENT_FOLDER)
    fun getInvoiceFolder(context: Context): File = getSubFolder(context, INVOICE_FOLDER)
    fun getAttachmentFolder(context: Context): File = getSubFolder(context, ATTACHMENT_FOLDER)
    fun getProfileFolder(context: Context): File = getSubFolder(context, PROFILE_FOLDER)

    private fun getSubFolder(context: Context, name: String): File {
        val root = getUdaariRoot(context)
        val subFolder = File(root, name)
        if (!subFolder.exists()) {
            subFolder.mkdirs()
        }
        return subFolder
    }
}
