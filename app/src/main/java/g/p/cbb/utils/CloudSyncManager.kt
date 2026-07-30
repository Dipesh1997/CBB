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
    private val suggestionDao: ProductSuggestionDao,
    private val settings: SettingsRepository,
    private val authManager: GoogleAuthManager,
    private val tombstoneDao: TombstoneDao,
    private val activityLogDao: ActivityLogDao,
    private val billItemDao: BillItemDao
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
            Log.d("CloudSync", "Linked system account: ${account.name}")
        } else {
            Log.w("CloudSync", "System account not found for $targetEmail. Using name-only fallback.")
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
            am.getAccountsByType("com.google").find { 
                it.name.equals(email, ignoreCase = true) 
            }
        } catch (e: Exception) {
            Log.e("CloudSync", "Error searching system accounts: ${e.message}")
            null
        }
    }

    suspend fun fullSync() = syncLock.withLock {
        withContext(Dispatchers.IO) {
            val email = authManager.userEmail.value ?: run {
                Log.w("CloudSync", "Sync skipped: User not signed in")
                return@withContext
            }
            
            // Debug: Log all accounts visible to the app
            try {
                val am = android.accounts.AccountManager.get(context)
                val accounts = am.accounts
                Log.d("CloudSync", "Visible Accounts Count: ${accounts.size}")
                accounts.forEach { acc ->
                    Log.d("CloudSync", "Account: ${acc.name} (${acc.type})")
                }
            } catch (e: Exception) {
                Log.e("CloudSync", "Error listing accounts: ${e.message}")
            }

            val sheets = getSheetsService(email)
            val drive = getDriveService(email)

            Log.d("CloudSync", "Starting sync for $email")
            try {
                var spreadsheetId = settings.getSpreadsheetId()
                if (spreadsheetId == null) {
                    Log.d("CloudSync", "Spreadsheet ID not found in settings, searching in Drive...")
                    spreadsheetId = findOrCreateSpreadsheet(drive, sheets)
                    settings.saveSpreadsheetId(spreadsheetId)
                    Log.i("CloudSync", "New Spreadsheet initialized: $spreadsheetId")
                } else {
                    Log.d("CloudSync", "Using existing Spreadsheet ID: $spreadsheetId")
                }

                // Ensure all sheets (Customers, Transactions, BillItems, etc.) exist
                GoogleSheetsHelper.setupSheets(sheets, spreadsheetId)

                Log.d("CloudSync", "Pushing Customers...")
                pushCustomers(sheets, spreadsheetId)
                
                Log.d("CloudSync", "Pushing Transactions...")
                pushTransactions(sheets, spreadsheetId, drive)
                
                Log.d("CloudSync", "Pushing Bill Items...")
                pushBillItems(sheets, spreadsheetId)

                Log.d("CloudSync", "Pushing Catalog...")
                pushCatalog(sheets, spreadsheetId)

                Log.d("CloudSync", "Pushing History...")
                pushHistory(sheets, spreadsheetId)

                Log.d("CloudSync", "Pushing Deletions...")
                pushTombstones(sheets, spreadsheetId)

                Log.d("CloudSync", "Pulling Data...")
                pullCustomers(sheets, spreadsheetId)
                pullTransactions(sheets, spreadsheetId)
                pullBillItems(sheets, spreadsheetId)
                pullCatalog(sheets, spreadsheetId)
                
                Log.i("CloudSync", "Sync Completed successfully for $email")
            } catch (e: UserRecoverableAuthIOException) {
                Log.w("CloudSync", "User consent required: ${e.message}")
                throw e
            } catch (e: Exception) {
                Log.e("CloudSync", "Sync Failed: ${e.message}")
                e.printStackTrace()
                throw e 
            }
        }
    }

    private suspend fun pushCustomers(sheets: Sheets, spreadsheetId: String) {
        val unsynced = customerDao.getUnsyncedCustomers()
        unsynced.forEach { customer ->
            val serverId = customer.serverId ?: UUID.randomUUID().toString()
            if (customer.serverId == null) {
                customerDao.updateServerId(customer.id, serverId)
            }
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
            
            val updated = updateOrAppendRow(sheets, spreadsheetId, "Customers", serverId, row, 8)
            if (updated) {
                customerDao.markSynced(customer.id, serverId)
            }
        }
    }

    private suspend fun pushTransactions(sheets: Sheets, spreadsheetId: String, drive: Drive) {
        val unsynced = transactionDao.getUnsyncedTransactions()
        unsynced.forEach { tx ->
            val serverId = tx.serverId ?: UUID.randomUUID().toString()
            if (tx.serverId == null) {
                transactionDao.updateServerId(tx.id, serverId)
            }
            
            val customerServerId = customerDao.getCustomerById(tx.customerId)?.serverId ?: ""
            if (customerServerId.isEmpty()) {
                Log.w("CloudSync", "Skipping transaction ${tx.id}: Customer ServerID not found.")
                return@forEach
            }

            var imagePreview = ""
            var viewLink = ""
            var driveFileId = tx.driveFileId ?: ""
            
            tx.attachmentPath?.let { path ->
                val file = java.io.File(path)
                if (file.exists()) {
                    driveFileId = GoogleDriveHelper.uploadFile(drive, file, null)
                    transactionDao.updateDriveFileId(tx.id, driveFileId)
                }
            }
            
            if (driveFileId.isNotEmpty()) {
                imagePreview = "=IMAGE(\"https://drive.google.com/thumbnail?id=$driveFileId\")"
                viewLink = "=HYPERLINK(\"https://drive.google.com/file/d/$driveFileId/view\", \"View Attachment\")"
            }

            val row = listOf(
                tx.id.toString(),
                customerServerId,
                tx.amount.toString(),
                tx.type.name,
                tx.timestamp.toString(),
                tx.note,
                imagePreview,
                viewLink,
                driveFileId,
                tx.createdBy,
                tx.lastUpdated.toString(),
                serverId
            )
            val updated = updateOrAppendRow(sheets, spreadsheetId, "Transactions", serverId, row, 11)
            if (updated) {
                transactionDao.markSynced(tx.id, serverId)
            }
        }
    }

    private suspend fun pushCatalog(sheets: Sheets, spreadsheetId: String) {
        val unsynced = suggestionDao.getUnsyncedSuggestions()
        unsynced.forEach { item ->
            val serverId = item.serverId ?: UUID.randomUUID().toString()
            if (item.serverId == null) {
                suggestionDao.updateServerId(item.id, serverId)
            }
            val row = listOf(
                item.id.toString(),
                item.name,
                item.lastPrice.toString(),
                item.shortcut ?: "",
                item.units ?: "",
                item.createdBy,
                item.lastUpdated.toString(),
                serverId
            )
            val updated = updateOrAppendRow(sheets, spreadsheetId, "Catalog", serverId, row, 7)
            if (updated) {
                suggestionDao.markSynced(item.id, serverId)
            }
        }
    }

    private suspend fun pushHistory(sheets: Sheets, spreadsheetId: String) {
        val unsynced = activityLogDao.getUnsyncedLogs()
        unsynced.forEach { log ->
            val serverId = log.serverId ?: UUID.randomUUID().toString()
            if (log.serverId == null) {
                activityLogDao.updateServerId(log.id, serverId)
            }
            val row = listOf(
                log.id.toString(),
                log.timestamp.toString(),
                log.description,
                log.isCloudUpdate.toString(),
                serverId
            )
            val updated = updateOrAppendRow(sheets, spreadsheetId, "History", serverId, row, 4)
            if (updated) {
                activityLogDao.markSynced(log.id, serverId)
            }
        }
    }

    private suspend fun pushBillItems(sheets: Sheets, spreadsheetId: String) {
        val unsynced = billItemDao.getUnsyncedBillItems()
        unsynced.forEach { item ->
            val serverId = item.serverId ?: UUID.randomUUID().toString()
            if (item.serverId == null) {
                billItemDao.updateServerId(item.id, serverId)
            }

            val txServerId = transactionDao.getTransactionById(item.transactionId)?.serverId ?: ""
            if (txServerId.isEmpty()) {
                Log.w("CloudSync", "Skipping bill item ${item.id}: Transaction ServerID not found.")
                return@forEach
            }

            val row = listOf(
                item.id.toString(),
                txServerId,
                item.productName,
                item.price.toString(),
                item.lastUpdated.toString(),
                serverId
            )
            val updated = updateOrAppendRow(sheets, spreadsheetId, "BillItems", serverId, row, 5)
            if (updated) {
                billItemDao.markSynced(item.id, serverId)
            }
        }
    }

    private suspend fun pushTombstones(sheets: Sheets, spreadsheetId: String) {
        val unsynced = tombstoneDao.getUnsyncedTombstones()
        unsynced.forEach { ts ->
            // 1. Delete from original sheet if serverId exists
            ts.originalServerId?.let { serverId ->
                val sheetName = when (ts.tableName) {
                    "customers" -> "Customers"
                    "transactions" -> "Transactions"
                    "product_suggestions" -> "Catalog"
                    "bill_items" -> "BillItems"
                    else -> null
                }
                val colIndex = when (ts.tableName) {
                    "customers" -> 8
                    "transactions" -> 11
                    "product_suggestions" -> 7
                    "bill_items" -> 5
                    else -> -1
                }
                if (sheetName != null && colIndex != -1) {
                    deleteRowByServerId(sheets, spreadsheetId, sheetName, serverId, colIndex)
                }
            }

            // 2. Add to Trash sheet
            val row = listOf(
                ts.summary,
                ts.tableName,
                ts.originalServerId ?: "",
                ts.timestamp.toString(),
                ts.contentJson
            )
            val body = ValueRange().setValues(listOf(row))
            sheets.spreadsheets().values()
                .append(spreadsheetId, "Trash!A1", body)
                .setValueInputOption("USER_ENTERED")
                .execute()
            tombstoneDao.markSynced(ts.id)
        }
    }

    private suspend fun deleteRowByServerId(
        sheets: Sheets,
        spreadsheetId: String,
        sheetName: String,
        serverId: String,
        serverIdColumnIndex: Int
    ) {
        try {
            val range = "$sheetName!A:Z"
            val response = sheets.spreadsheets().values().get(spreadsheetId, range).execute()
            val values = response.getValues() ?: return

            var rowIndex = -1
            for (i in values.indices) {
                val currentRow = values[i]
                if (currentRow.size > serverIdColumnIndex && currentRow[serverIdColumnIndex].toString() == serverId) {
                    rowIndex = i
                    break
                }
            }

            if (rowIndex != -1) {
                // Clear the row content
                val clearRange = "$sheetName!A${rowIndex + 1}:Z${rowIndex + 1}"
                sheets.spreadsheets().values().clear(spreadsheetId, clearRange, com.google.api.services.sheets.v4.model.ClearValuesRequest()).execute()
                Log.d("CloudSync", "Cleared row $rowIndex in $sheetName for deleted record $serverId")
            }
        } catch (e: Exception) {
            Log.e("CloudSync", "Error deleting row in $sheetName: ${e.message}")
        }
    }

    private suspend fun updateOrAppendRow(
        sheets: Sheets, 
        spreadsheetId: String, 
        sheetName: String, 
        serverId: String, 
        row: List<Any>,
        serverIdColumnIndex: Int
    ): Boolean {
        return try {
            val range = "$sheetName!A:Z"
            val response = sheets.spreadsheets().values().get(spreadsheetId, range).execute()
            val values = response.getValues()
            
            var rowIndex = -1
            if (values != null) {
                for (i in values.indices) {
                    val currentRow = values[i]
                    if (currentRow.size > serverIdColumnIndex && currentRow[serverIdColumnIndex].toString() == serverId) {
                        rowIndex = i + 1 // 1-based index
                        break
                    }
                }
            }

            if (rowIndex != -1) {
                // Update existing
                val updateRange = "$sheetName!A$rowIndex"
                val body = ValueRange().setValues(listOf(row))
                sheets.spreadsheets().values()
                    .update(spreadsheetId, updateRange, body)
                    .setValueInputOption("USER_ENTERED")
                    .execute()
            } else {
                // Append new
                val body = ValueRange().setValues(listOf(row))
                sheets.spreadsheets().values()
                    .append(spreadsheetId, "$sheetName!A1", body)
                    .setValueInputOption("USER_ENTERED")
                    .execute()
            }
            true
        } catch (e: Exception) {
            Log.e("CloudSync", "Error updating/appending row in $sheetName: ${e.message}")
            false
        }
    }

    private suspend fun pullCustomers(sheets: Sheets, spreadsheetId: String) {
        val response = sheets.spreadsheets().values().get(spreadsheetId, "Customers!A2:I").execute()
        val rows = response.getValues() ?: return
        rows.forEach { row ->
            if (row.size < 9) return@forEach
            val serverId = row[8].toString()
            val lastUpdated = row[7].toString().toLongOrNull() ?: 0L
            val local = customerDao.getCustomerByServerId(serverId)
            if (local == null || lastUpdated > local.lastUpdated) {
                val customer = Customer(
                    id = local?.id ?: 0,
                    name = row[1].toString(),
                    phone = row[2].toString(),
                    address = row[3].toString(),
                    totalBalance = row[4].toString().toDoubleOrNull() ?: 0.0,
                    isBadDebt = row[5].toString().toBoolean(),
                    createdBy = row[6].toString(),
                    lastUpdated = lastUpdated,
                    syncStatus = 0,
                    serverId = serverId
                )
                customerDao.insertCustomer(customer)
                if (local == null) repository.logCloudActivity("Cloud Sync: Added customer ${customer.name}")
            }
        }
    }

    private suspend fun pullTransactions(sheets: Sheets, spreadsheetId: String) {
        val response = sheets.spreadsheets().values().get(spreadsheetId, "Transactions!A2:L").execute()
        val rows = response.getValues() ?: return
        rows.forEach { row ->
            if (row.size < 12) return@forEach
            val serverId = row[11].toString()
            val lastUpdated = row[10].toString().toLongOrNull() ?: 0L
            val customerServerId = row[1].toString()
            
            val localCustomer = customerDao.getCustomerByServerId(customerServerId) ?: return@forEach // Cannot pull tx without customer
            val local = transactionDao.getTransactionByServerId(serverId)
            
            if (local == null || lastUpdated > local.lastUpdated) {
                val tx = Transaction(
                    id = local?.id ?: 0,
                    customerId = localCustomer.id,
                    amount = row[2].toString().toDoubleOrNull() ?: 0.0,
                    type = try { TransactionType.valueOf(row[3].toString()) } catch (e: Exception) { TransactionType.DEBIT },
                    timestamp = row[4].toString().toLongOrNull() ?: 0L,
                    note = row[5].toString(),
                    attachmentPath = null, 
                    createdBy = row[9].toString(),
                    lastUpdated = lastUpdated,
                    syncStatus = 0,
                    serverId = serverId,
                    driveFileId = row[8].toString().takeIf { it.isNotEmpty() }
                )
                transactionDao.insertTransaction(tx)
            }
        }
    }

    private suspend fun pullCatalog(sheets: Sheets, spreadsheetId: String) {
        val response = sheets.spreadsheets().values().get(spreadsheetId, "Catalog!A2:H").execute()
        val rows = response.getValues() ?: return
        rows.forEach { row ->
            if (row.size < 8) return@forEach
            val serverId = row[7].toString()
            val lastUpdated = row[6].toString().toLongOrNull() ?: 0L
            val local = suggestionDao.getSuggestionByServerId(serverId)
            if (local == null || lastUpdated > local.lastUpdated) {
                val item = ProductSuggestion(
                    id = local?.id ?: 0,
                    name = row[1].toString(),
                    lastPrice = row[2].toString().toDoubleOrNull() ?: 0.0,
                    shortcut = row[3].toString(),
                    units = row[4].toString(),
                    createdBy = row[5].toString(),
                    lastUpdated = lastUpdated,
                    syncStatus = 0,
                    serverId = serverId
                )
                suggestionDao.upsertSuggestion(item)
            }
        }
    }

    private suspend fun pullBillItems(sheets: Sheets, spreadsheetId: String) {
        val response = sheets.spreadsheets().values().get(spreadsheetId, "BillItems!A2:F").execute()
        val rows = response.getValues() ?: return
        rows.forEach { row ->
            if (row.size < 6) return@forEach
            val serverId = row[5].toString()
            val lastUpdated = row[4].toString().toLongOrNull() ?: 0L
            val txServerId = row[1].toString()

            val localTx = transactionDao.getTransactionByServerId(txServerId) ?: return@forEach
            val local = billItemDao.getBillItemByServerId(serverId)
            
            if (local == null || lastUpdated > local.lastUpdated) {
                val item = BillItem(
                    id = local?.id ?: 0,
                    transactionId = localTx.id,
                    productName = row[2].toString(),
                    price = row[3].toString().toDoubleOrNull() ?: 0.0,
                    lastUpdated = lastUpdated,
                    syncStatus = 0,
                    serverId = serverId
                )
                billItemDao.insertBillItems(listOf(item))
            }
        }
    }

    private fun findOrCreateSpreadsheet(drive: Drive, sheets: Sheets): String {
        val files = drive.files().list()
            .setQ("name = \u0027Udaari_Database\u0027 and mimeType = \u0027application/vnd.google-apps.spreadsheet\u0027 and trashed = false")
            .execute()
        if (files.files != null && files.files.isNotEmpty()) return files.files[0].id
        val newSheet = com.google.api.services.sheets.v4.model.Spreadsheet()
            .setProperties(com.google.api.services.sheets.v4.model.SpreadsheetProperties().setTitle("Udaari_Database"))
        return sheets.spreadsheets().create(newSheet).execute().spreadsheetId
    }

    suspend fun inviteCollaborator(email: String) = withContext(Dispatchers.IO) {
        val spreadsheetId = settings.getSpreadsheetId() ?: return@withContext
        val drive = getDriveService(authManager.userEmail.value ?: return@withContext)
        GoogleDriveHelper.shareWithUser(drive, spreadsheetId, email)
    }
}
