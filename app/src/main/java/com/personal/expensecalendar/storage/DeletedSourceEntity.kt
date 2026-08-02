package com.personal.expensecalendar.storage

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "deleted_sources")
data class DeletedSourceEntity(
    @PrimaryKey
    val sourceFingerprint: String,
    val deletedAtMillis: Long,
)
