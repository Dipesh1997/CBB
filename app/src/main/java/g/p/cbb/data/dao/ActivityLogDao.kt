package g.p.cbb.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import g.p.cbb.data.entity.ActivityLog
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityLogDao {
    @Insert
    suspend fun insertLog(log: ActivityLog)

    @Query("SELECT * FROM activity_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<ActivityLog>>

    @Query("SELECT COUNT(*) FROM activity_logs WHERE isRead = 0")
    fun getUnreadCount(): Flow<Int>

    @Query("UPDATE activity_logs SET isRead = 1 WHERE isRead = 0")
    suspend fun markAllAsRead()

    @Query("DELETE FROM activity_logs")
    suspend fun clearLogs()

    @Query("SELECT * FROM activity_logs WHERE syncStatus = 1")
    suspend fun getUnsyncedLogs(): List<ActivityLog>

    @Query("UPDATE activity_logs SET syncStatus = 0, serverId = :serverId WHERE id = :id")
    suspend fun markSynced(id: Long, serverId: String)

    @Query("UPDATE activity_logs SET syncStatus = 1 WHERE syncStatus = 0")
    suspend fun markAllAsUnsynced()

    @Query("UPDATE activity_logs SET serverId = :serverId WHERE id = :id")
    suspend fun updateServerId(id: Long, serverId: String)
}
