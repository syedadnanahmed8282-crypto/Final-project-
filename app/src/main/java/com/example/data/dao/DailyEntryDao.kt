package com.example.data.dao

import androidx.room.*
import com.example.data.model.DailyEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyEntryDao {
    @Query("SELECT * FROM daily_entries ORDER BY dateMillis DESC, id DESC")
    fun getAllEntries(): Flow<List<DailyEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: DailyEntry)

    @Update
    suspend fun updateEntry(entry: DailyEntry)

    @Delete
    suspend fun deleteEntry(entry: DailyEntry)

    @Query("DELETE FROM daily_entries WHERE id = :id")
    suspend fun deleteEntryById(id: Int)

    @Query("DELETE FROM daily_entries")
    suspend fun clearAllEntries()
}
