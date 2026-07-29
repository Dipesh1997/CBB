package g.p.cbb.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "product_suggestions")
data class ProductSuggestion(
    @PrimaryKey
    val name: String,
    val lastPrice: Double,
    val shortcut: String? = null,
    val units: String? = null
)
