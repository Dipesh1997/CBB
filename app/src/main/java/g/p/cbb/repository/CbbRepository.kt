package g.p.cbb.repository

import g.p.cbb.data.AppDatabase
import g.p.cbb.data.dao.*
import g.p.cbb.data.entity.*
import g.p.cbb.utils.BackupManager
import g.p.cbb.utils.GoogleAuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CbbRepository @Inject constructor(
    private val database: AppDatabase,
    private val customerDao: CustomerDao,
    private val transactionDao: TransactionDao,
    private val billItemDao: BillItemDao,
    private val activityLogDao: ActivityLogDao,
    private val productSuggestionDao: ProductSuggestionDao,
    private val tombstoneDao: TombstoneDao,
    private val authManager: GoogleAuthManager
) {
    private fun getCurrentUser(): String = authManager.userEmail.value ?: "admin"

    fun getAllCustomers(): Flow<List<Customer>> = customerDao.getAllCustomers()

    fun getDatabase(): AppDatabase = database

    suspend fun getCustomerById(id: Long): Customer? = customerDao.getCustomerById(id)

    suspend fun addCustomer(customer: Customer): Long = withContext(Dispatchers.IO) {
        val syncCustomer = customer.copy(
            createdBy = getCurrentUser(),
            lastUpdated = System.currentTimeMillis(),
            syncStatus = 1
        )
        val id = customerDao.insertCustomer(syncCustomer)
        logActivity("Added Customer: ${customer.name}")
        id
    }

    suspend fun updateCustomer(customer: Customer) = withContext(Dispatchers.IO) {
        val syncCustomer = customer.copy(
            lastUpdated = System.currentTimeMillis(),
            syncStatus = 1
        )
        customerDao.updateCustomer(syncCustomer)
        logActivity("Updated Customer: ${customer.name}")
    }

    suspend fun updateCustomerReminder(customerId: Long, reminderTime: Long?) = withContext(Dispatchers.IO) {
        val customer = customerDao.getCustomerById(customerId)
        customer?.let {
            customerDao.updateCustomer(it.copy(reminderTime = reminderTime, lastUpdated = System.currentTimeMillis(), syncStatus = 1))
            val logMsg = if (reminderTime != null) "Set Reminder for ${it.name}" else "Cancelled Reminder for ${it.name}"
            logActivity(logMsg)
        }
    }

    suspend fun deleteCustomer(customer: Customer) = withContext(Dispatchers.IO) {
        // 1. Fetch all transactions and bill items for cascading tombstone creation
        val transactions = transactionDao.getTransactionsForCustomerList(customer.id)
        transactions.forEach { tx ->
            val items = billItemDao.getBillItemsForTransaction(tx.id)
            items.forEach { item ->
                tombstoneDao.insertTombstone(Tombstone(
                    tableName = "bill_items",
                    originalServerId = item.serverId,
                    summary = "Item: ${item.productName} (from ${tx.type} ₹${tx.amount} of ${customer.name})",
                    contentJson = com.google.gson.Gson().toJson(item)
                ))
            }
            tombstoneDao.insertTombstone(Tombstone(
                tableName = "transactions",
                originalServerId = tx.serverId,
                summary = "Transaction: ${tx.type} ₹${tx.amount} for ${customer.name}",
                contentJson = com.google.gson.Gson().toJson(tx)
            ))
        }

        // 2. Tombstone the customer itself
        val json = com.google.gson.Gson().toJson(customer)
        tombstoneDao.insertTombstone(Tombstone(
            tableName = "customers", 
            originalServerId = customer.serverId, 
            summary = "Customer: ${customer.name} (${customer.phone})",
            contentJson = json
        ))
        
        // 3. Delete from DB (CASCADE will handle children locally, but we've already tracked them for Sheets)
        customerDao.deleteCustomer(customer)
        logActivity("Deleted Customer: ${customer.name}")
    }

    fun getTransactionsForCustomer(customerId: Long): Flow<List<Transaction>> =
        transactionDao.getTransactionsForCustomer(customerId)

    suspend fun addTransaction(
        transaction: Transaction, 
        timestamp: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        val syncTransaction = transaction.copy(
            timestamp = timestamp,
            createdBy = getCurrentUser(),
            lastUpdated = System.currentTimeMillis(),
            syncStatus = 1
        )
        val transactionId = transactionDao.insertTransaction(syncTransaction)
        val balanceChange = if (transaction.type == TransactionType.CREDIT) -transaction.amount else transaction.amount
        val updateTime = System.currentTimeMillis()
        customerDao.updateBalance(transaction.customerId, balanceChange, updateTime)
        
        val customer = customerDao.getCustomerById(transaction.customerId)
        logActivity("Added ${transaction.type} of ₹${transaction.amount} for ${customer?.name ?: "Unknown"}")
    }

    suspend fun updateTransaction(oldTransaction: Transaction, newTransaction: Transaction) = withContext(Dispatchers.IO) {
        val syncTime = System.currentTimeMillis()
        val syncTransaction = newTransaction.copy(
            lastUpdated = syncTime,
            syncStatus = 1
        )
        
        val reverseAmount = if (oldTransaction.type == TransactionType.CREDIT) oldTransaction.amount else -oldTransaction.amount
        customerDao.updateBalance(oldTransaction.customerId, reverseAmount, syncTime)

        val newAmount = if (syncTransaction.type == TransactionType.CREDIT) -syncTransaction.amount else syncTransaction.amount
        customerDao.updateBalance(syncTransaction.customerId, newAmount, syncTime)

        transactionDao.insertTransaction(syncTransaction)

        val customer = customerDao.getCustomerById(syncTransaction.customerId)
        logActivity("Updated ${syncTransaction.type} for ${customer?.name ?: "Unknown"}: ₹${oldTransaction.amount} -> ₹${syncTransaction.amount}")
    }

    suspend fun deleteTransaction(transaction: Transaction) = withContext(Dispatchers.IO) {
        val syncTime = System.currentTimeMillis()
        val reverseAmount = if (transaction.type == TransactionType.CREDIT) transaction.amount else -transaction.amount
        customerDao.updateBalance(transaction.customerId, reverseAmount, syncTime)
        
        // Tracking transaction deletion
        val json = com.google.gson.Gson().toJson(transaction)
        val summary = "Transaction: ${transaction.type} ₹${transaction.amount} (${transaction.note})"
        tombstoneDao.insertTombstone(Tombstone(
            tableName = "transactions", 
            originalServerId = transaction.serverId, 
            summary = summary,
            contentJson = json
        ))
        transactionDao.deleteTransaction(transaction)
        val customer = customerDao.getCustomerById(transaction.customerId)
        logActivity("Deleted ${transaction.type} of ₹${transaction.amount} for ${customer?.name ?: "Unknown"}")
    }

    suspend fun getLinkedTransactions(parentId: Long): List<Transaction> =
        transactionDao.getLinkedTransactions(parentId)

    suspend fun restoreLatestBackup(context: android.content.Context): String? {
        return BackupManager.importLatestDatabase(context, database)
    }

    fun getActivityLogs(): Flow<List<ActivityLog>> = activityLogDao.getAllLogs()

    fun getUnreadLogCount(): Flow<Int> = activityLogDao.getUnreadCount()

    suspend fun markLogsAsRead() {
        activityLogDao.markAllAsRead()
    }

    suspend fun logCloudActivity(description: String) {
        activityLogDao.insertLog(ActivityLog(description = description, isCloudUpdate = true, isRead = false))
    }

    suspend fun markAllDataAsUnsynced() = withContext(Dispatchers.IO) {
        customerDao.markAllAsUnsynced()
        transactionDao.markAllAsUnsynced()
        activityLogDao.markAllAsUnsynced()
        logActivity("Marked all data for Cloud Sync")
    }

    private suspend fun logActivity(description: String) {
        activityLogDao.insertLog(ActivityLog(description = description))
    }
}
