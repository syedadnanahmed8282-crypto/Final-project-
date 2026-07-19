package com.example.data.repository

import com.example.data.dao.DailyEntryDao
import com.example.data.model.DailyEntry
import kotlinx.coroutines.flow.Flow

class DailyEntryRepository(private val dailyEntryDao: DailyEntryDao) {
    val allEntries: Flow<List<DailyEntry>> = dailyEntryDao.getAllEntries()

    suspend fun insert(entry: DailyEntry) {
        dailyEntryDao.insertEntry(entry)
    }

    suspend fun update(entry: DailyEntry) {
        dailyEntryDao.updateEntry(entry)
    }

    suspend fun delete(entry: DailyEntry) {
        dailyEntryDao.deleteEntry(entry)
    }

    suspend fun deleteById(id: Int) {
        dailyEntryDao.deleteEntryById(id)
    }

    suspend fun clearAll() {
        dailyEntryDao.clearAllEntries()
    }
}
