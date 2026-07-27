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

    @Query("DELETE FROM activity_logs")
    suspend fun clearLogs()
}
