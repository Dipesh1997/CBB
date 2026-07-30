package g.p.cbb.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "activity_logs")
data class ActivityLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val description: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isCloudUpdate: Boolean = false,
    val isRead: Boolean = true,
    val syncStatus: Int = 1,
    val serverId: String? = null
)
