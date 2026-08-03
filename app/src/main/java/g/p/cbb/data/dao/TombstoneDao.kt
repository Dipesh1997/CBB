package g.p.cbb.data.dao

import androidx.room.*
import g.p.cbb.data.entity.Tombstone
import kotlinx.coroutines.flow.Flow

@Dao
interface TombstoneDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTombstone(tombstone: Tombstone)

    @Query("SELECT * FROM tombstones WHERE syncStatus = 1")
    suspend fun getUnsyncedTombstones(): List<Tombstone>

    @Query("SELECT * FROM tombstones ORDER BY timestamp DESC")
    fun getAllTombstones(): Flow<List<Tombstone>>

    @Query("SELECT * FROM tombstones WHERE originalServerId = :serverId LIMIT 1")
    suspend fun getTombstoneByServerId(serverId: String): Tombstone?

    @Query("UPDATE tombstones SET syncStatus = 0 WHERE id = :id")
    suspend fun markSynced(id: Long)

    @Delete
    suspend fun deleteTombstone(tombstone: Tombstone)
}
