package g.p.cbb.data.dao

import androidx.room.*
import g.p.cbb.data.entity.Customer
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY name ASC")
    fun getAllCustomers(): Flow<List<Customer>>

    @Query("SELECT * FROM customers")
    suspend fun getCustomersListSync(): List<Customer>

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun getCustomerById(id: Long): Customer?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: Customer): Long

    @Update
    suspend fun updateCustomer(customer: Customer)

    @Delete
    suspend fun deleteCustomer(customer: Customer)

    @Query("SELECT * FROM customers WHERE syncStatus = 1")
    suspend fun getUnsyncedCustomers(): List<Customer>

    @Query("SELECT * FROM customers WHERE serverId = :serverId LIMIT 1")
    suspend fun getCustomerByServerId(serverId: String): Customer?

    @Query("UPDATE customers SET totalBalance = totalBalance + :amount, lastUpdated = :timestamp, syncStatus = 1 WHERE id = :customerId")
    suspend fun updateBalance(customerId: Long, amount: Double, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE customers SET syncStatus = 0, serverId = :serverId WHERE id = :localId")
    suspend fun markSynced(localId: Long, serverId: String)

    @Query("UPDATE customers SET syncStatus = 1 WHERE syncStatus = 0")
    suspend fun markAllAsUnsynced()

    @Query("UPDATE customers SET serverId = :serverId WHERE id = :id")
    suspend fun updateServerId(id: Long, serverId: String)
}
