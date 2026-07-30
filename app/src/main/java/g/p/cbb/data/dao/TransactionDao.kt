package g.p.cbb.data.dao

import androidx.room.*
import g.p.cbb.data.entity.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE customerId = :customerId ORDER BY timestamp DESC")
    fun getTransactionsForCustomer(customerId: Long): Flow<List<Transaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction): Long

    @Delete
    suspend fun deleteTransaction(transaction: Transaction)
    @Query("SELECT * FROM transactions WHERE parentTransactionId = :parentId")
    suspend fun getLinkedTransactions(parentId: Long): List<Transaction>

    @Query("SELECT * FROM transactions WHERE syncStatus = 1")
    suspend fun getUnsyncedTransactions(): List<Transaction>

    @Query("UPDATE transactions SET syncStatus = 0, serverId = :serverId WHERE id = :localId")
    suspend fun markSynced(localId: Long, serverId: String)

    @Query("UPDATE transactions SET syncStatus = 1 WHERE syncStatus = 0")
    suspend fun markAllAsUnsynced()

    @Query("UPDATE transactions SET serverId = :serverId WHERE id = :id")
    suspend fun updateServerId(id: Long, serverId: String)

    @Query("UPDATE transactions SET driveFileId = :fileId WHERE id = :id")
    suspend fun updateDriveFileId(id: Long, fileId: String)

    @Query("SELECT * FROM transactions WHERE customerId = :customerId")
    suspend fun getTransactionsForCustomerList(customerId: Long): List<Transaction>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionById(id: Long): Transaction?

    @Query("SELECT * FROM transactions WHERE serverId = :serverId LIMIT 1")
    suspend fun getTransactionByServerId(serverId: String): Transaction?
}
