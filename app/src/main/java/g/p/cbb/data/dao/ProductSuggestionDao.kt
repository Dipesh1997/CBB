package g.p.cbb.data.dao

import androidx.room.*
import g.p.cbb.data.entity.ProductSuggestion
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductSuggestionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSuggestion(suggestion: ProductSuggestion)

    @Query("SELECT * FROM product_suggestions WHERE name = :name AND (units = :units OR (units IS NULL AND :units IS NULL)) LIMIT 1")
    suspend fun getSuggestionByNameAndUnits(name: String, units: String?): ProductSuggestion?

    @Query("SELECT * FROM product_suggestions WHERE shortcut = :shortcut LIMIT 1")
    suspend fun getSuggestionByShortcut(shortcut: String): ProductSuggestion?

    @Query("SELECT * FROM product_suggestions ORDER BY name ASC")
    fun getAllSuggestions(): Flow<List<ProductSuggestion>>

    @Update
    suspend fun updateSuggestion(suggestion: ProductSuggestion)

    @Delete
    suspend fun deleteSuggestion(suggestion: ProductSuggestion)

    @Query("SELECT * FROM product_suggestions WHERE syncStatus = 1")
    suspend fun getUnsyncedSuggestions(): List<ProductSuggestion>

    @Query("SELECT * FROM product_suggestions WHERE serverId = :serverId LIMIT 1")
    suspend fun getSuggestionByServerId(serverId: String): ProductSuggestion?

    @Query("UPDATE product_suggestions SET syncStatus = 0, serverId = :serverId WHERE id = :localId")
    suspend fun markSynced(localId: Long, serverId: String)

    @Query("UPDATE product_suggestions SET syncStatus = 1 WHERE syncStatus = 0")
    suspend fun markAllAsUnsynced()

    @Query("UPDATE product_suggestions SET serverId = :serverId WHERE id = :id")
    suspend fun updateServerId(id: Long, serverId: String)
}
