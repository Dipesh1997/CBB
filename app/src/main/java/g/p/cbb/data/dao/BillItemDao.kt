package g.p.cbb.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import g.p.cbb.data.entity.BillItem

@Dao
interface BillItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBillItems(items: List<BillItem>)

    @Query("SELECT * FROM bill_items WHERE transactionId = :transactionId")
    suspend fun getBillItemsForTransaction(transactionId: Long): List<BillItem>

    @Query("SELECT * FROM bill_items WHERE syncStatus = 1")
    suspend fun getUnsyncedBillItems(): List<BillItem>

    @Query("UPDATE bill_items SET syncStatus = 0, serverId = :serverId WHERE id = :localId")
    suspend fun markSynced(localId: Long, serverId: String)

    @Query("UPDATE bill_items SET serverId = :serverId WHERE id = :id")
    suspend fun updateServerId(id: Long, serverId: String)

    @Query("SELECT * FROM bill_items WHERE serverId = :serverId LIMIT 1")
    suspend fun getBillItemByServerId(serverId: String): BillItem?

    @Query("UPDATE bill_items SET syncStatus = 1 WHERE syncStatus = 0")
    suspend fun markAllAsUnsynced()
}
