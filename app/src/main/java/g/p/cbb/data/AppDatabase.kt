package g.p.cbb.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.TypeConverter
import g.p.cbb.data.dao.*
import g.p.cbb.data.entity.*

@Database(
    entities = [
        Customer::class, 
        Transaction::class, 
        BillItem::class, 
        ActivityLog::class, 
        ProductSuggestion::class,
        Tombstone::class
    ], 
    version = 15, 
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun customerDao(): CustomerDao
    abstract fun transactionDao(): TransactionDao
    abstract fun billItemDao(): BillItemDao
    abstract fun activityLogDao(): ActivityLogDao
    abstract fun productSuggestionDao(): ProductSuggestionDao
    abstract fun tombstoneDao(): TombstoneDao
}

class Converters {
    @TypeConverter
    fun fromTransactionType(value: TransactionType) = value.name

    @TypeConverter
    fun toTransactionType(value: String) = TransactionType.valueOf(value)
}
