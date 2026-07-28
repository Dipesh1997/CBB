package g.p.cbb.data.dao

import androidx.room.*
import g.p.cbb.data.entity.ProductSuggestion
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductSuggestionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSuggestion(suggestion: ProductSuggestion)

    @Query("SELECT * FROM product_suggestions ORDER BY name ASC")
    fun getAllSuggestions(): Flow<List<ProductSuggestion>>

    @Update
    suspend fun updateSuggestion(suggestion: ProductSuggestion)

    @Delete
    suspend fun deleteSuggestion(suggestion: ProductSuggestion)
}
