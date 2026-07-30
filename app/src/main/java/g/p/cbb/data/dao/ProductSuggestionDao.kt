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
}
