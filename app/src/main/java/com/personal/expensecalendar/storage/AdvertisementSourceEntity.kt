package com.personal.expensecalendar.storage

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Identifies filtered messages without retaining their original text or deleting saved data. */
@Entity(tableName = "advertisement_sources")
data class AdvertisementSourceEntity(
    @PrimaryKey val sourceFingerprint: String,
)
