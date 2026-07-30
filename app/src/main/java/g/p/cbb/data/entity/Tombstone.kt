package g.p.cbb.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tombstones")
data class Tombstone(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tableName: String,
    val originalServerId: String?,
    val summary: String,
    val contentJson: String,
    val timestamp: Long = System.currentTimeMillis(),
    val syncStatus: Int = 1
)
