package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_entries")
data class DailyEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val dateMillis: Long,             // Date selection timestamp
    val quantity: Int = 0,            // Piece quantity (Production)
    val expense: Double = 0.0,        // Expense amount
    val note: String = "",            // Remarks/Description
    val folderName: String = "সাধারণ হিসাব", // Folder/Account name
    val income: Double = 0.0,         // Income amount (Manual)
    val category: String = "অন্যান্য",  // Category (food, transport, salary, gift, etc.)
    val isIncome: Boolean = false     // True if this is an income entry, False if expense/production
)
