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
}
