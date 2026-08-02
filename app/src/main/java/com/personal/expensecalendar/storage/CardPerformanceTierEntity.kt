package com.personal.expensecalendar.storage

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "card_performance_tiers",
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
        Index(value = ["cardProfileId", "minimumSpendWon"], unique = true),
    ],
)
data class CardPerformanceTierEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val cardProfileId: Long,
    val minimumSpendWon: Long,
    val benefitWon: Long,
)
