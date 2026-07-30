package g.p.cbb.data.dao

import androidx.room.*
import g.p.cbb.data.entity.Tombstone

@Dao
interface TombstoneDao {
    @Insert
    suspend fun insertTombstone(tombstone: Tombstone)

    @Query("SELECT * FROM tombstones WHERE syncStatus = 1")
    suspend fun getUnsyncedTombstones(): List<Tombstone>

    @Query("UPDATE tombstones SET syncStatus = 0 WHERE id = :id")
    suspend fun markSynced(id: Long)
}
