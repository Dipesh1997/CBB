package g.p.cbb.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "product_suggestions",
    indices = [
        Index(value = ["name", "units"], unique = true),
        Index(value = ["shortcut"], unique = true)
    ]
)
data class ProductSuggestion(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val lastPrice: Double,
    val shortcut: String? = null,
    val units: String? = null
)
