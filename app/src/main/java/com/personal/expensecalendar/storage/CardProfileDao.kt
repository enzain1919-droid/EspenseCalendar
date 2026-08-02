package com.personal.expensecalendar.storage

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction

data class CardProfileWithRules(
    @Embedded
    val card: CardProfileEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "cardProfileId",
    )
    val rules: List<CardDetectionRuleEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "cardProfileId",
    )
    val performanceTiers: List<CardPerformanceTierEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "cardProfileId",
    )
    val performanceExclusions: List<CardPerformanceExclusionEntity>,
)

data class ActiveCardDetectionRule(
    val cardName: String,
    val phrase: String,
)

@Dao
interface CardProfileDao {
    @Transaction
    @Query("SELECT * FROM card_profiles ORDER BY id")
    suspend fun findAllWithRules(): List<CardProfileWithRules>

    @Query(
        "SELECT card_profiles.displayName AS cardName, card_detection_rules.phrase AS phrase " +
            "FROM card_detection_rules " +
            "INNER JOIN card_profiles ON card_profiles.id = card_detection_rules.cardProfileId " +
            "WHERE card_profiles.isActive = 1 AND card_detection_rules.isActive = 1 " +
            "ORDER BY card_detection_rules.id",
    )
    suspend fun findActiveDetectionRules(): List<ActiveCardDetectionRule>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCard(card: CardProfileEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRule(rule: CardDetectionRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPerformanceTier(tier: CardPerformanceTierEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPerformanceExclusion(exclusion: CardPerformanceExclusionEntity): Long

    @Delete
    suspend fun deleteCard(card: CardProfileEntity)

    @Delete
    suspend fun deleteRule(rule: CardDetectionRuleEntity)

    @Delete
    suspend fun deletePerformanceTier(tier: CardPerformanceTierEntity)

    @Delete
    suspend fun deletePerformanceExclusion(exclusion: CardPerformanceExclusionEntity)

    @Transaction
    suspend fun insertCardWithRule(
        card: CardProfileEntity,
        phrase: String,
        normalizedPhrase: String,
    ) {
        val cardId = insertCard(card)
        insertRule(
            CardDetectionRuleEntity(
                cardProfileId = cardId,
                phrase = phrase,
                normalizedPhrase = normalizedPhrase,
            ),
        )
    }
}
