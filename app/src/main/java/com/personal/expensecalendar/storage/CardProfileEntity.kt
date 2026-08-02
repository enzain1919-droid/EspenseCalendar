package com.personal.expensecalendar.storage

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "card_profiles",
    indices = [Index(value = ["normalizedName"], unique = true)],
)
data class CardProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val displayName: String,
    val normalizedName: String,
    @ColumnInfo(defaultValue = "1")
    val isActive: Boolean = true,
)
