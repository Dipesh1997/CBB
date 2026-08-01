package g.p.cbb.utils

import android.accounts.Account
import android.content.Context
import android.util.Log
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.api.services.sheets.v4.model.ValueRange
import dagger.hilt.android.qualifiers.ApplicationContext
import g.p.cbb.data.dao.*
import g.p.cbb.data.entity.*
import g.p.cbb.repository.CbbRepository
import g.p.cbb.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: CbbRepository,
    private val customerDao: CustomerDao,
    private val transactionDao: TransactionDao,
    private val settings: SettingsRepository,
    private val authManager: GoogleAuthManager,
    private val tombstoneDao: TombstoneDao,
    private val activityLogDao: ActivityLogDao
) {
    private val transport = NetHttpTransport()
    private val jsonFactory = GsonFactory.getDefaultInstance()
    private val syncLock = Mutex()

    private fun getSheetsService(email: String): Sheets {
        val credential = GoogleAccountCredential.usingOAuth2(context, listOf(SheetsScopes.SPREADSHEETS, DriveScopes.DRIVE_FILE))
        val targetEmail = email.trim()
        val account = findSystemAccount(targetEmail)
        if (account != null) {
            credential.selectedAccount = account
        } else {
            credential.selectedAccountName = targetEmail
        }
        return Sheets.Builder(transport, jsonFactory, credential)
            .setApplicationName("Udaari Ledger")
            .build()
    }

    private fun getDriveService(email: String): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(context, listOf(SheetsScopes.SPREADSHEETS, DriveScopes.DRIVE_FILE))
        val targetEmail = email.trim()
        val account = findSystemAccount(targetEmail)
        if (account != null) {
            credential.selectedAccount = account
        } else {
            credential.selectedAccountName = targetEmail
        }
        return Drive.Builder(transport, jsonFactory, credential)
            .setApplicationName("Udaari Ledger")
            .build()
    }

    private fun findSystemAccount(email: String): Account? {
        return try {
            val am = android.accounts.AccountManager.get(context)
            val accounts = am.getAccountsByType("com.google")
            val exactMatch = accounts.find { it.name.equals(email, ignoreCase = true) }
            if (exactMatch != null) return exactMatch
            if (accounts.size == 1) return accounts[0]
            null
        } catch (e: Exception) {
            Log.e("CloudSync", "Error searching system accounts: ${e.message}")
            null
        }
    }

    suspend fun fullSync() = syncLock.withLock {
        withContext(Dispatchers.IO) {
            val email = authManager.userEmail.value ?: return@withContext
            
            val sheets = getSheetsService(email)
            val drive = getDriveService(email)

            try {
                var spreadsheetId = settings.getSpreadsheetId()
                if (spreadsheetId == null) {
                    spreadsheetId = findOrCreateSpreadsheet(drive, sheets)
                    settings.saveSpreadsheetId(spreadsheetId)
                }

                GoogleSheetsHelper.setupSheets(sheets, spreadsheetId)

                pushCustomers(sheets, spreadsheetId)
                pushTransactions(sheets, spreadsheetId, drive)
                pushHistory(sheets, spreadsheetId)
                pushTombstones(sheets, spreadsheetId)

                pullCustomers(sheets, spreadsheetId)
                pullTransactions(sheets, spreadsheetId)
                
                Log.i("CloudSync", "Sync Completed for $email")
                Log.i("CloudSync", "BROWSER URL: https://docs.google.com/spreadsheets/d/$spreadsheetId/edit")
            } catch (e: UserRecoverableAuthIOException) {
                throw e
            } catch (e: Exception) {
                Log.e("CloudSync", "Sync Failed: ${e.message}")
                throw e 
            }
        }
    }

    private suspend fun pushCustomers(sheets: Sheets, spreadsheetId: String) {
        val unsynced = customerDao.getUnsyncedCustomers()
        unsynced.forEach { customer ->
            val serverId = customer.serverId ?: UUID.randomUUID().toString()
            if (customer.serverId == null) customerDao.updateServerId(customer.id, serverId)
            val row = listOf(customer.id.toString(), customer.name, customer.phone, customer.address, customer.totalBalance.toString(), customer.isBadDebt.toString(), customer.createdBy, customer.lastUpdated.toString(), serverId)
            if (updateOrAppendRow(sheets, spreadsheetId, "Customers", serverId, row, 8)) {
                customerDao.markSynced(customer.id, serverId)
            }
        }
    }

    private suspend fun pushTransactions(sheets: Sheets, spreadsheetId: String, drive: Drive) {
        val unsynced = transactionDao.getUnsyncedTransactions()
        unsynced.forEach { tx ->
            val serverId = tx.serverId ?: UUID.randomUUID().toString()
            if (tx.serverId == null) transactionDao.updateServerId(tx.id, serverId)
            
            val customerServerId = customerDao.getCustomerById(tx.customerId)?.serverId ?: return@forEach

            var driveFileId = tx.driveFileId ?: ""
            tx.attachmentPath?.let { path ->
                val file = java.io.File(path)
                if (file.exists()) {
                    driveFileId = GoogleDriveHelper.uploadFile(drive, file, null)
                    transactionDao.updateDriveFileId(tx.id, driveFileId)
                }
            }
            
            val preview = if (driveFileId.isNotEmpty()) "=IMAGE(\"https://drive.google.com/thumbnail?id=$driveFileId\")" else ""
            val link = if (driveFileId.isNotEmpty()) "=HYPERLINK(\"https://drive.google.com/file/d/$driveFileId/view\", \"View\")" else ""

            val row = listOf(tx.id.toString(), customerServerId, tx.amount.toString(), tx.type.name, tx.timestamp.toString(), tx.note, preview, link, driveFileId, tx.parentServerId ?: "", tx.createdBy, tx.lastUpdated.toString(), serverId)
            if (updateOrAppendRow(sheets, spreadsheetId, "Transactions", serverId, row, 12)) {
                transactionDao.markSynced(tx.id, serverId)
            }
        }
    }

    private suspend fun pushHistory(sheets: Sheets, spreadsheetId: String) {
        val unsynced = activityLogDao.getUnsyncedLogs()
        unsynced.forEach { log ->
            val serverId = log.serverId ?: UUID.randomUUID().toString()
            if (log.serverId == null) activityLogDao.updateServerId(log.id, serverId)
            val row = listOf(log.id.toString(), log.timestamp.toString(), log.description, log.isCloudUpdate.toString(), serverId)
            if (updateOrAppendRow(sheets, spreadsheetId, "History", serverId, row, 4)) {
                activityLogDao.markSynced(log.id, serverId)
            }
        }
    }

    private suspend fun pushTombstones(sheets: Sheets, spreadsheetId: String) {
        val unsynced = tombstoneDao.getUnsyncedTombstones()
        unsynced.forEach { ts ->
            ts.originalServerId?.let { serverId ->
                val sheetName = when (ts.tableName) {
                    "customers" -> "Customers"
                    "transactions" -> "Transactions"
                    else -> null
                }
                val colIndex = when (ts.tableName) {
                    "customers" -> 8
                    "transactions" -> 12
                    else -> -1
                }
                if (sheetName != null && colIndex != -1) deleteRowByServerId(sheets, spreadsheetId, sheetName, serverId, colIndex)
            }
            val row = listOf(ts.summary, ts.tableName, ts.originalServerId ?: "", ts.timestamp.toString(), ts.contentJson)
            sheets.spreadsheets().values().append(spreadsheetId, "Trash!A1", ValueRange().setValues(listOf(row))).setValueInputOption("USER_ENTERED").execute()
            tombstoneDao.markSynced(ts.id)
        }
    }

    private suspend fun updateOrAppendRow(sheets: Sheets, spreadsheetId: String, sheetName: String, serverId: String, row: List<Any>, serverIdColIndex: Int): Boolean {
        try {
            val values = sheets.spreadsheets().values().get(spreadsheetId, "$sheetName!A:Z").execute().getValues()
            var rowIndex = -1
            if (values != null) {
                for (i in values.indices) {
                    if (values[i].size > serverIdColIndex && values[i][serverIdColIndex].toString() == serverId) {
                        rowIndex = i + 1
                        break
                    }
                }
            }
            if (rowIndex != -1) {
                sheets.spreadsheets().values().update(spreadsheetId, "$sheetName!A$rowIndex", ValueRange().setValues(listOf(row))).setValueInputOption("USER_ENTERED").execute()
            } else {
                sheets.spreadsheets().values().append(spreadsheetId, "$sheetName!A1", ValueRange().setValues(listOf(row))).setValueInputOption("USER_ENTERED").execute()
            }
            return true
        } catch (e: Exception) { return false }
    }

    private suspend fun deleteRowByServerId(sheets: Sheets, spreadsheetId: String, sheetName: String, serverId: String, serverIdColIndex: Int) {
        try {
            val values = sheets.spreadsheets().values().get(spreadsheetId, "$sheetName!A:Z").execute().getValues() ?: return
            for (i in values.indices) {
                if (values[i].size > serverIdColIndex && values[i][serverIdColIndex].toString() == serverId) {
                    sheets.spreadsheets().values().clear(spreadsheetId, "$sheetName!A${i+1}:Z${i+1}", com.google.api.services.sheets.v4.model.ClearValuesRequest()).execute()
                    break
                }
            }
        } catch (e: Exception) {}
    }

    private suspend fun pullCustomers(sheets: Sheets, spreadsheetId: String) {
        val rows = sheets.spreadsheets().values().get(spreadsheetId, "Customers!A2:I").execute().getValues() ?: return
        rows.forEach { row ->
            if (row.size < 9) return@forEach
            val sid = row[8].toString()
            val last = row[7].toString().toLongOrNull() ?: 0L
            val local = customerDao.getCustomerByServerId(sid)
            if (local == null || last > local.lastUpdated) {
                customerDao.insertCustomer(Customer(id = local?.id ?: 0, name = row[1].toString(), phone = row[2].toString(), address = row[3].toString(), totalBalance = row[4].toString().toDoubleOrNull() ?: 0.0, isBadDebt = row[5].toString().toBoolean(), createdBy = row[6].toString(), lastUpdated = last, syncStatus = 0, serverId = sid))
            }
        }
    }

    private suspend fun pullTransactions(sheets: Sheets, spreadsheetId: String) {
        val rows = sheets.spreadsheets().values().get(spreadsheetId, "Transactions!A2:M").execute().getValues() ?: return
        rows.forEach { row ->
            if (row.size < 13) return@forEach
            val sid = row[12].toString()
            val last = row[11].toString().toLongOrNull() ?: 0L
            val cid = row[1].toString()
            val cust = customerDao.getCustomerByServerId(cid) ?: return@forEach
            val local = transactionDao.getTransactionByServerId(sid)
            if (local == null || last > local.lastUpdated) {
                transactionDao.insertTransaction(Transaction(id = local?.id ?: 0, customerId = cust.id, amount = row[2].toString().toDoubleOrNull() ?: 0.0, type = try { TransactionType.valueOf(row[3].toString()) } catch (e: Exception) { TransactionType.DEBIT }, timestamp = row[4].toString().toLongOrNull() ?: 0L, note = row[5].toString(), attachmentPath = null, createdBy = row[10].toString(), lastUpdated = last, syncStatus = 0, serverId = sid, driveFileId = row[8].toString().takeIf { it.isNotEmpty() }, parentServerId = row[9].toString().takeIf { it.isNotEmpty() }))
            }
        }
    }

    private fun findOrCreateSpreadsheet(drive: Drive, sheets: Sheets): String {
        val files = drive.files().list().setQ("name = 'Udaari_Database' and mimeType = 'application/vnd.google-apps.spreadsheet' and trashed = false").execute()
        if (files.files != null && files.files.isNotEmpty()) return files.files[0].id
        return sheets.spreadsheets().create(com.google.api.services.sheets.v4.model.Spreadsheet().setProperties(com.google.api.services.sheets.v4.model.SpreadsheetProperties().setTitle("Udaari_Database"))).execute().spreadsheetId
    }

    suspend fun inviteCollaborator(email: String) = withContext(Dispatchers.IO) {
        val spreadsheetId = settings.getSpreadsheetId() ?: return@withContext
        val drive = getDriveService(authManager.userEmail.value ?: return@withContext)
        GoogleDriveHelper.shareWithUser(drive, spreadsheetId, email)
    }
}
