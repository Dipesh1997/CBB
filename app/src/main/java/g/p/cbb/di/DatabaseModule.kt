package g.p.cbb.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import g.p.cbb.data.AppDatabase
import g.p.cbb.data.dao.ActivityLogDao
import g.p.cbb.data.dao.BillItemDao
import g.p.cbb.data.dao.ProductSuggestionDao
import g.p.cbb.data.dao.CustomerDao
import g.p.cbb.data.dao.TransactionDao
import g.p.cbb.data.dao.TombstoneDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "cbb_database")
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideCustomerDao(database: AppDatabase): CustomerDao = database.customerDao()

    @Provides
    fun provideTransactionDao(database: AppDatabase): TransactionDao = database.transactionDao()

    @Provides
    fun provideBillItemDao(database: AppDatabase): BillItemDao = database.billItemDao()

    @Provides
    fun provideActivityLogDao(database: AppDatabase): ActivityLogDao = database.activityLogDao()

    @Provides
    fun provideProductSuggestionDao(database: AppDatabase): ProductSuggestionDao = database.productSuggestionDao()

    @Provides
    fun provideTombstoneDao(database: AppDatabase): TombstoneDao = database.tombstoneDao()
}
