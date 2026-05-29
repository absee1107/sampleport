package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val amount: Double,
    val type: String, // "EXPENSE", "SAVING", "EARNING"
    val category: String,
    val dateMillis: Long,
    val description: String,
    val party: String = "" // Paid to / Received from
)
