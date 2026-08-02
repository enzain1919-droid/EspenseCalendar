package com.personal.expensecalendar.storage

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "card_detection_rules",
    foreignKeys = [
        ForeignKey(
            entity = CardProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["cardProfileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["cardProfileId"]),
        Index(value = ["normalizedPhrase"], unique = true),
    ],
)
data class CardDetectionRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val cardProfileId: Long,
    val phrase: String,
    val normalizedPhrase: String,
    @ColumnInfo(defaultValue = "1")
    val isActive: Boolean = true,
)
