package g.p.cbb.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import g.p.cbb.data.dao.ActivityLogDao
import g.p.cbb.data.dao.BillItemDao
import g.p.cbb.data.dao.CustomerDao
import g.p.cbb.data.dao.TransactionDao
import g.p.cbb.data.entity.ActivityLog
import g.p.cbb.data.entity.BillItem
import g.p.cbb.data.entity.Customer
import g.p.cbb.data.entity.Transaction
import g.p.cbb.data.entity.TransactionType
import androidx.room.TypeConverter

@Database(entities = [Customer::class, Transaction::class, BillItem::class, ActivityLog::class], version = 5, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun customerDao(): CustomerDao
    abstract fun transactionDao(): TransactionDao
    abstract fun billItemDao(): BillItemDao
    abstract fun activityLogDao(): ActivityLogDao
}

class Converters {
    @TypeConverter
    fun fromTransactionType(value: TransactionType) = value.name

    @TypeConverter
    fun toTransactionType(value: String) = TransactionType.valueOf(value)
}
