package g.p.cbb.utils

import android.accounts.Account
import android.content.Context
import android.util.Log
import android.widget.Toast
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

// Absolute Master Spreadsheet Lock - v21 Replica
private const val FIXED_SPREADSHEET_ID = "1tTnbqhjkKLSvQxm3rI-rHCue_oRhWIjgzgZQsySuR58"

@Singleton
class CloudSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val customerDao: CustomerDao,
    private val transactionDao: TransactionDao,
    private val authManager: GoogleAuthManager,
    private val tombstoneDao: TombstoneDao,
    private val activityLogDao: ActivityLogDao,
    private val settingsRepository: g.p.cbb.repository.SettingsRepository
) {
    private val transport = NetHttpTransport()
    private val jsonFactory = GsonFactory.getDefaultInstance()
    private val syncLock = Mutex()

    private fun getSheetsService(email: String): Sheets {
        val token = authManager.accessToken.value
        val requestInitializer = if (!token.isNullOrEmpty()) {
            com.google.api.client.http.HttpRequestInitializer { request ->
                request.headers.authorization = "Bearer $token"
            }
        } else {
            GoogleAccountCredential.usingOAuth2(context, listOf(SheetsScopes.SPREADSHEETS, DriveScopes.DRIVE_FILE, DriveScopes.DRIVE_METADATA_READONLY)).apply {
                val targetEmail = email.trim()
                val account = findSystemAccount(targetEmail)
                if (account != null) selectedAccount = account else selectedAccountName = targetEmail
            }
        }
        return Sheets.Builder(transport, jsonFactory, requestInitializer)
            .setApplicationName("Udaari Ledger")
            .build()
    }

    private fun getDriveService(email: String): Drive {
        val token = authManager.accessToken.value
        val requestInitializer = if (!token.isNullOrEmpty()) {
            com.google.api.client.http.HttpRequestInitializer { request ->
                request.headers.authorization = "Bearer $token"
            }
        } else {
            GoogleAccountCredential.usingOAuth2(context, listOf(SheetsScopes.SPREADSHEETS, DriveScopes.DRIVE_FILE, DriveScopes.DRIVE_METADATA_READONLY)).apply {
                val targetEmail = email.trim()
                val account = findSystemAccount(targetEmail)
                if (account != null) selectedAccount = account else selectedAccountName = targetEmail
            }
        }
        return Drive.Builder(transport, jsonFactory, requestInitializer)
            .setApplicationName("Udaari Ledger")
            .build()
    }

    private fun findSystemAccount(email: String): Account? {
        return try {
            val am = android.accounts.AccountManager.get(context)
            val accounts = am.getAccountsByType("com.google")
            accounts.find { it.name.equals(email, ignoreCase = true) } ?: accounts.firstOrNull()
        } catch (e: Exception) {
            Log.e("CloudSync", "Error searching system accounts: ${e.message}")
            null
        }
    }

    suspend fun fullSync(forcePull: Boolean = false) = syncLock.withLock {
        withContext(Dispatchers.IO) {
            Log.i("CloudSync", "--- MASTER v21 REPLICA SYNC START (ForcePull: $forcePull) ---")
            
            val am = android.accounts.AccountManager.get(context)
            val accounts = try { am.getAccountsByType("com.google") } catch (e: Exception) { emptyArray() }
            
            var email = authManager.userEmail.value
            if (accounts.isNotEmpty()) {
                val match = accounts.find { it.name.equals(email, ignoreCase = true) }
                if (match != null) {
                    email = match.name
                } else {
                    email = accounts[0].name
                    authManager.forceAccountLink(email)
                    Log.i("CloudSync", "Auto-linked device Google account: $email")
                }
            } else if (email.isNullOrEmpty()) {
                throw Exception("No Google account found on device. Please add a Google account in phone settings.")
            }
            
            val sheets = getSheetsService(email)
            val drive = getDriveService(email)

            try {
                val spreadsheetId = FIXED_SPREADSHEET_ID
                require(spreadsheetId.length > 5) { "FATAL: Spreadsheet ID is invalid!" }
                
                // Perfect Parity: Ensure headers are 13 columns for Transactions
                GoogleSheetsHelper.setupSheets(sheets, spreadsheetId)

                // Push phase
                pushCustomers(sheets, spreadsheetId)
                pushTransactions(sheets, spreadsheetId, drive)
                pushHistory(sheets, spreadsheetId)
                pushTombstones(sheets, spreadsheetId)

                // Pull phase
                val pulledCustomers = pullCustomers(sheets, spreadsheetId, forcePull)
                val pulledTransactions = pullTransactions(sheets, spreadsheetId, drive, forcePull)
                val pulledTrash = pullTombstones(sheets, spreadsheetId)
                
                if (pulledTransactions > 0 || pulledCustomers > 0) {
                    NotificationHelper.showSyncUpdateNotification(
                        context,
                        "Udaari Ledger: Database Updated",
                        "Synced $pulledTransactions transaction(s) and $pulledCustomers customer(s) from team sync."
                    )
                }

                Log.i("CloudSync", "Sync Completed: $pulledCustomers customers, $pulledTransactions transactions, $pulledTrash trash items pulled for $email")
            } catch (e: UserRecoverableAuthIOException) {
                throw e
            } catch (e: com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAuthIOException) {
                val cause = e.cause
                if (cause is com.google.android.gms.auth.UserRecoverableAuthException) {
                    throw UserRecoverableAuthIOException(cause)
                }
                throw e
            } catch (e: com.google.android.gms.auth.UserRecoverableAuthException) {
                throw UserRecoverableAuthIOException(e)
            } catch (e: com.google.api.client.googleapis.json.GoogleJsonResponseException) {
                throw e
            } catch (e: Exception) {
                val cause = e.cause
                if (cause is UserRecoverableAuthIOException) {
                    throw cause
                }
                if (cause is com.google.android.gms.auth.UserRecoverableAuthException) {
                    throw UserRecoverableAuthIOException(cause)
                }
                if (cause is com.google.api.client.googleapis.json.GoogleJsonResponseException) {
                    throw cause
                }
                Log.e("CloudSync", "Sync Fatal Error: ${e.message}")
                e.printStackTrace()
                throw e 
            }
        }
    }

    private suspend fun pushCustomers(sheets: Sheets, spreadsheetId: String) {
        val unsynced = customerDao.getUnsyncedCustomers()
        unsynced.forEach { customer ->
            val serverId = customer.serverId ?: UUID.randomUUID().toString()
            if (customer.serverId == null) customerDao.updateServerId(customer.id, serverId)
            val row = listOf(
                customer.id.toString(),
                customer.name,
                customer.phone,
                customer.address,
                customer.totalBalance.toString(),
                customer.isBadDebt.toString(),
                customer.createdBy,
                customer.lastUpdated.toString(),
                serverId
            )
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
                    val uploadFile = ImageUtils.getCompressedFileForUpload(context, file)
                    driveFileId = GoogleDriveHelper.uploadFile(drive, uploadFile, null)
                    transactionDao.updateDriveFileId(tx.id, driveFileId)
                }
            }
            
            // Web Parity: Col 6: Image, Col 7: Link, Col 8: DriveID, Col 9: ParentID
            val preview = if (driveFileId.isNotEmpty()) "=IMAGE(\"https://drive.google.com/thumbnail?id=$driveFileId\")" else ""
            val link = if (driveFileId.isNotEmpty()) "=HYPERLINK(\"https://drive.google.com/file/d/$driveFileId/view\", \"View\")" else ""

            val row = listOf(
                tx.id.toString(), 
                customerServerId, 
                tx.amount.toString(), 
                tx.type.name, 
                tx.timestamp.toString(), 
                tx.note, 
                preview, 
                link, 
                driveFileId, 
                tx.parentServerId ?: "", 
                tx.createdBy, 
                tx.lastUpdated.toString(), 
                serverId
            )
            
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
            val targetSid = serverId.trim()
            if (values != null) {
                for (i in values.indices) {
                    if (values[i].size > serverIdColIndex && values[i][serverIdColIndex].toString().trim() == targetSid) {
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
        } catch (e: Exception) { 
            Log.e("CloudSync", "Error updating $sheetName: ${e.message}")
            return false 
        }
    }

    private suspend fun deleteRowByServerId(sheets: Sheets, spreadsheetId: String, sheetName: String, serverId: String, serverIdColIndex: Int) {
        try {
            val values = sheets.spreadsheets().values().get(spreadsheetId, "$sheetName!A:Z").execute().getValues() ?: return
            val targetSid = serverId.trim()
            for (i in values.indices) {
                if (values[i].size > serverIdColIndex && values[i][serverIdColIndex].toString().trim() == targetSid) {
                    sheets.spreadsheets().values().clear(spreadsheetId, "$sheetName!A${i+1}:Z${i+1}", com.google.api.services.sheets.v4.model.ClearValuesRequest()).execute()
                    break
                }
            }
        } catch (e: Exception) {
             Log.e("CloudSync", "Error deleting from $sheetName: ${e.message}")
        }
    }

    private suspend fun pullCustomers(sheets: Sheets, spreadsheetId: String, forcePull: Boolean = false): Int {
        val rawValues = sheets.spreadsheets().values().get(spreadsheetId, "Customers!A:Z").execute().getValues() ?: return 0
        if (rawValues.size <= 1) return 0
        val rows = rawValues.drop(1)
        var count = 0
        rows.forEach { row ->
            if (row.size < 2) return@forEach
            val idStr = row.getOrNull(0)?.toString()?.trim() ?: ""
            val name = row.getOrNull(1)?.toString()?.trim() ?: ""
            if (name.isEmpty()) return@forEach

            val phone = row.getOrNull(2)?.toString()?.trim() ?: ""
            val address = row.getOrNull(3)?.toString()?.trim() ?: ""
            val totalBalance = row.getOrNull(4)?.toString()?.trim()?.toDoubleOrNull() ?: 0.0
            val isBadDebt = row.getOrNull(5)?.toString()?.trim()?.lowercase() == "true"
            val createdBy = row.getOrNull(6)?.toString()?.trim() ?: "unknown"
            val last = row.getOrNull(7)?.toString()?.trim()?.toLongOrNull() ?: System.currentTimeMillis()
            val sid = row.getOrNull(8)?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: "cust_$idStr"

            val local = customerDao.getCustomerByServerId(sid) 
                ?: customerDao.getCustomersListSync().find { it.name.equals(name, ignoreCase = true) }

            if (forcePull || local == null || last > local.lastUpdated) {
                val customer = Customer(
                    id = local?.id ?: 0,
                    name = name,
                    phone = phone,
                    address = address,
                    totalBalance = totalBalance,
                    isBadDebt = isBadDebt,
                    createdBy = createdBy,
                    lastUpdated = last,
                    syncStatus = 0,
                    serverId = sid
                )
                customerDao.insertCustomer(customer)
                count++
            }
        }
        return count
    }

    private suspend fun pullTransactions(sheets: Sheets, spreadsheetId: String, drive: Drive, forcePull: Boolean = false): Int {
        val rawValues = sheets.spreadsheets().values().get(spreadsheetId, "Transactions!A:Z").execute().getValues() ?: return 0
        if (rawValues.size <= 1) return 0
        val rows = rawValues.drop(1)
        var count = 0
        rows.forEach { row ->
            if (row.size < 3) return@forEach
            val txIdStr = row.getOrNull(0)?.toString()?.trim() ?: ""
            val cid = row.getOrNull(1)?.toString()?.trim() ?: ""
            if (cid.isEmpty()) return@forEach
            
            val cust = customerDao.getCustomerByServerId(cid)
                ?: customerDao.getCustomersListSync().find { it.id.toString() == cid || it.serverId == cid }
                ?: return@forEach

            val amount = row.getOrNull(2)?.toString()?.trim()?.toDoubleOrNull() ?: 0.0
            val typeStr = row.getOrNull(3)?.toString()?.trim() ?: "DEBIT"
            val type = try { TransactionType.valueOf(typeStr.uppercase()) } catch (e: Exception) { TransactionType.DEBIT }
            val timestamp = row.getOrNull(4)?.toString()?.trim()?.toLongOrNull() ?: System.currentTimeMillis()
            val note = row.getOrNull(5)?.toString() ?: ""
            val driveFileId = row.getOrNull(8)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            val parentServerId = row.getOrNull(9)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            val createdBy = row.getOrNull(10)?.toString()?.trim() ?: "unknown"
            val last = row.getOrNull(11)?.toString()?.trim()?.toLongOrNull() ?: timestamp
            val sid = row.getOrNull(12)?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: "tx_$txIdStr"

            val local = transactionDao.getTransactionByServerId(sid)

            if (forcePull || local == null || last > local.lastUpdated) {
                var localAttachmentPath = local?.attachmentPath
                val localFileExists = ImageResolver.getLocalFile(localAttachmentPath) != null
                if (!localFileExists && !driveFileId.isNullOrEmpty()) {
                    try {
                        val attachmentFolder = StorageManager.getAttachmentFolder(context)
                        val targetFile = java.io.File(attachmentFolder, "receipt_${driveFileId.trim()}.jpg")
                        if (!targetFile.exists() || targetFile.length() == 0L) {
                            val outputStream = java.io.FileOutputStream(targetFile)
                            drive.files().get(driveFileId.trim()).executeMediaAndDownloadTo(outputStream)
                            outputStream.close()
                        }
                        if (targetFile.exists() && targetFile.length() > 0) {
                            localAttachmentPath = targetFile.absolutePath
                        }
                    } catch (e: Exception) {
                        Log.w("CloudSync", "Failed to download receipt image $driveFileId: ${e.message}")
                    }
                }

                val parentTx = parentServerId?.let { transactionDao.getTransactionByServerId(it) }

                val tx = Transaction(
                    id = local?.id ?: 0,
                    customerId = cust.id,
                    amount = amount,
                    type = type,
                    timestamp = timestamp,
                    note = note,
                    attachmentPath = localAttachmentPath,
                    createdBy = createdBy,
                    lastUpdated = last,
                    syncStatus = 0,
                    serverId = sid,
                    driveFileId = driveFileId,
                    parentTransactionId = parentTx?.id ?: local?.parentTransactionId,
                    parentServerId = parentServerId
                )
                transactionDao.insertTransaction(tx)
                count++
            }
        }
        return count
    }

    private suspend fun pullTombstones(sheets: Sheets, spreadsheetId: String): Int {
        val rawValues = try {
            sheets.spreadsheets().values().get(spreadsheetId, "Trash!A:Z").execute().getValues()
        } catch (e: Exception) {
            null
        } ?: return 0

        if (rawValues.size <= 1) return 0
        val rows = rawValues.drop(1)
        var count = 0
        rows.forEach { row ->
            if (row.size < 3) return@forEach
            val summary = row.getOrNull(0)?.toString()?.trim() ?: ""
            val tableName = row.getOrNull(1)?.toString()?.trim() ?: ""
            val serverId = row.getOrNull(2)?.toString()?.trim() ?: ""
            val timestamp = row.getOrNull(3)?.toString()?.trim()?.toLongOrNull() ?: System.currentTimeMillis()
            val contentJson = row.getOrNull(4)?.toString() ?: ""

            if (serverId.isEmpty() || tableName.isEmpty()) return@forEach

            val existingTombstone = tombstoneDao.getTombstoneByServerId(serverId)
            if (existingTombstone == null) {
                tombstoneDao.insertTombstone(
                    Tombstone(
                        tableName = tableName,
                        originalServerId = serverId,
                        summary = summary,
                        contentJson = contentJson,
                        timestamp = timestamp,
                        syncStatus = 0
                    )
                )
            }

            // Delete from local DB if still present
            if (tableName == "transactions") {
                val localTx = transactionDao.getTransactionByServerId(serverId)
                if (localTx != null) {
                    val reverseAmount = if (localTx.type == TransactionType.CREDIT) localTx.amount else -localTx.amount
                    customerDao.updateBalance(localTx.customerId, reverseAmount, System.currentTimeMillis())
                    transactionDao.deleteTransaction(localTx)
                    count++
                }
            } else if (tableName == "customers") {
                val localCust = customerDao.getCustomerByServerId(serverId)
                if (localCust != null) {
                    customerDao.deleteCustomer(localCust)
                    count++
                }
            }
        }
        return count
    }

    suspend fun inviteCollaborator(email: String) = withContext(Dispatchers.IO) {
        val emailAddr = authManager.userEmail.value ?: return@withContext
        val drive = getDriveService(emailAddr)
        val targetSheetId = settingsRepository.getSpreadsheetId() ?: FIXED_SPREADSHEET_ID
        GoogleDriveHelper.shareWithUser(drive, targetSheetId, email)
    }
}
