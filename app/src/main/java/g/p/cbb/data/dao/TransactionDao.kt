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
}
