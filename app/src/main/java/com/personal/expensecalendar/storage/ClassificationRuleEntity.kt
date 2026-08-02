package com.personal.expensecalendar.storage

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "classification_rules",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["categoryId"]),
        Index(value = ["normalizedPhrase"], unique = true),
    ],
)
data class ClassificationRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val categoryId: Long,
    val phrase: String,
    val normalizedPhrase: String,
    @ColumnInfo(defaultValue = "1")
    val isActive: Boolean = true,
)
