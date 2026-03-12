package com.example.billing.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String
)

@Entity(tableName = "records")
data class RecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val categoryId: Long,
    val type: RecordType,
    val note: String,
    val timestamp: Long
)

enum class RecordType { EXPENSE, INCOME }

@Serializable
data class BackupData(
    val categories: List<BackupCategory>,
    val records: List<BackupRecord>
)

@Serializable
data class BackupCategory(val id: Long, val name: String, val emoji: String)

@Serializable
data class BackupRecord(
    val amount: Double,
    val categoryId: Long,
    val type: String,
    val note: String,
    val timestamp: Long
)
