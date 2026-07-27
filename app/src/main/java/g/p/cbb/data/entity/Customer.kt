package g.p.cbb.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val phone: String,
    val address: String = "",
    val totalBalance: Double = 0.0, // Negative for Debit, Positive for Credit
    val reminderTime: Long? = null
)
