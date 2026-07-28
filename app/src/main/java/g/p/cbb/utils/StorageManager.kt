package g.p.cbb.utils

import android.content.Context
import android.os.Environment
import java.io.File

object StorageManager {
    private const val ROOT_FOLDER = "udaari"
    private const val BACKUP_FOLDER = "backups"
    private const val STATEMENT_FOLDER = "statements"
    private const val INVOICE_FOLDER = "invoices"

    fun getUdaariRoot(context: Context): File {
        val publicDocs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val udaariRoot = File(publicDocs, ROOT_FOLDER)
        if (!udaariRoot.exists()) {
            udaariRoot.mkdirs()
        }
        return udaariRoot
    }

    fun getBackupFolder(context: Context): File = getSubFolder(context, BACKUP_FOLDER)
    fun getStatementFolder(context: Context): File = getSubFolder(context, STATEMENT_FOLDER)
    fun getInvoiceFolder(context: Context): File = getSubFolder(context, INVOICE_FOLDER)

    private fun getSubFolder(context: Context, name: String): File {
        val root = getUdaariRoot(context)
        val subFolder = File(root, name)
        if (!subFolder.exists()) {
            subFolder.mkdirs()
        }
        return subFolder
    }
}
