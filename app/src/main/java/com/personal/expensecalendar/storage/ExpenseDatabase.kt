package com.personal.expensecalendar.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TransactionEntity::class,
        MonthlyBudgetEntity::class,
        CardProfileEntity::class,
        CardDetectionRuleEntity::class,
        CategoryEntity::class,
        ClassificationRuleEntity::class,
        DeletedSourceEntity::class,
        CardPerformanceTierEntity::class,
        CardPerformanceExclusionEntity::class,
        DeletedTransactionEntity::class,
        AdvertisementSourceEntity::class,
    ],
    version = 15,
    exportSchema = false,
)
abstract class ExpenseDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun monthlyBudgetDao(): MonthlyBudgetDao
    abstract fun cardProfileDao(): CardProfileDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        const val SCHEMA_VERSION = 15

        val BACKUP_TABLES = arrayOf(
            "transactions",
            "monthly_budgets",
            "card_profiles",
            "card_detection_rules",
            "categories",
            "classification_rules",
            "deleted_sources",
            "deleted_transactions",
            "advertisement_sources",
            "card_performance_tiers",
            "card_performance_exclusions",
        )

        @Volatile
        private var instance: ExpenseDatabase? = null

        fun getInstance(context: Context): ExpenseDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                ExpenseDatabase::class.java,
                "expense-calendar.db",
            ).addMigrations(MIGRATION_1_2)
                .addMigrations(MIGRATION_2_3)
                .addMigrations(MIGRATION_3_4)
                .addMigrations(MIGRATION_4_5)
                .addMigrations(MIGRATION_5_6)
                .addMigrations(MIGRATION_6_7)
                .addMigrations(MIGRATION_7_8)
                .addMigrations(MIGRATION_8_9)
                .addMigrations(MIGRATION_9_10)
                .addMigrations(MIGRATION_10_11)
                .addMigrations(MIGRATION_11_12)
                .addMigrations(MIGRATION_12_13)
                .addMigrations(MIGRATION_13_14)
                .addMigrations(MIGRATION_14_15)
                .addCallback(SEED_DEFAULT_CARDS_CALLBACK)
                .build()
                .also { instance = it }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE transactions " +
                        "ADD COLUMN source TEXT NOT NULL DEFAULT 'SMS'",
                )
                db.execSQL(
                    "ALTER TABLE transactions " +
                        "ADD COLUMN memo TEXT NOT NULL DEFAULT ''",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS monthly_budgets (" +
                        "yearMonth TEXT NOT NULL, " +
                        "amountWon INTEGER NOT NULL, " +
                        "PRIMARY KEY(yearMonth))",
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS card_profiles (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "displayName TEXT NOT NULL, " +
                        "normalizedName TEXT NOT NULL, " +
                        "isActive INTEGER NOT NULL DEFAULT 1)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_card_profiles_normalizedName " +
                        "ON card_profiles(normalizedName)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS card_detection_rules (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "cardProfileId INTEGER NOT NULL, " +
                        "phrase TEXT NOT NULL, " +
                        "normalizedPhrase TEXT NOT NULL, " +
                        "isActive INTEGER NOT NULL DEFAULT 1, " +
                        "FOREIGN KEY(cardProfileId) REFERENCES card_profiles(id) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_card_detection_rules_cardProfileId " +
                        "ON card_detection_rules(cardProfileId)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_card_detection_rules_normalizedPhrase " +
                        "ON card_detection_rules(normalizedPhrase)",
                )
                seedDefaultCards(db)
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE transactions " +
                        "ADD COLUMN transactionType TEXT NOT NULL DEFAULT 'EXPENSE'",
                )
                db.execSQL(
                    "ALTER TABLE transactions " +
                        "ADD COLUMN paymentMethod TEXT NOT NULL DEFAULT 'CARD'",
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createCategoryTables(db)
                seedDefaultCategories(db)
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "INSERT OR IGNORE INTO categories " +
                        "(name, normalizedName, isSystem) " +
                        "VALUES ('기타', '기타', 1)",
                )
                db.execSQL(
                    "UPDATE transactions SET categoryName = '기타' " +
                        "WHERE categoryName = '미분류'",
                )
                db.execSQL(
                    "UPDATE OR IGNORE classification_rules " +
                        "SET categoryId = (" +
                        "SELECT id FROM categories WHERE normalizedName = '기타' LIMIT 1) " +
                        "WHERE categoryId IN (" +
                        "SELECT id FROM categories WHERE normalizedName = '미분류')",
                )
                db.execSQL(
                    "DELETE FROM classification_rules WHERE categoryId IN (" +
                        "SELECT id FROM categories WHERE normalizedName = '미분류')",
                )
                db.execSQL("DELETE FROM categories WHERE normalizedName = '미분류'")
                db.execSQL("UPDATE categories SET isSystem = 1 WHERE normalizedName = '기타'")
                seedDefaultCategories(db)
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createCardPerformanceTables(db)
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "INSERT OR IGNORE INTO card_detection_rules " +
                        "(cardProfileId, phrase, normalizedPhrase, isActive) " +
                        "SELECT id, '현대카드 M', '현대카드 m', 1 FROM card_profiles " +
                        "WHERE normalizedName = '현대카드' LIMIT 1",
                )
                seedDefaultCategories(db)
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                seedDefaultPerformanceExclusions(db)
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE transactions " +
                        "ADD COLUMN performanceOverride TEXT NOT NULL DEFAULT 'AUTO'",
                )
                db.execSQL(
                    "UPDATE transactions SET performanceOverride = 'EXCLUDE' " +
                        "WHERE includedInPerformance = 0",
                )
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE transactions " +
                        "ADD COLUMN majorCategory TEXT NOT NULL DEFAULT 'LIVING_EXPENSE'",
                )
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "INSERT OR IGNORE INTO card_detection_rules " +
                        "(cardProfileId, phrase, normalizedPhrase, isActive) " +
                        "SELECT id, '[롯데카드]', '[롯데카드]', 1 FROM card_profiles " +
                        "WHERE normalizedName = '롯데카드' LIMIT 1",
                )
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE transactions " +
                        "ADD COLUMN includedInExpense INTEGER NOT NULL DEFAULT 1",
                )
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS deleted_transactions (" +
                        "sourceFingerprint TEXT NOT NULL, " +
                        "sourceSmsId INTEGER NOT NULL, " +
                        "cardName TEXT NOT NULL, " +
                        "merchant TEXT NOT NULL, " +
                        "amountWon INTEGER NOT NULL, " +
                        "status TEXT NOT NULL, " +
                        "occurredAtMillis INTEGER NOT NULL, " +
                        "categoryName TEXT NOT NULL, " +
                        "majorCategory TEXT NOT NULL, " +
                        "includedInPerformance INTEGER NOT NULL, " +
                        "performanceOverride TEXT NOT NULL, " +
                        "includedInExpense INTEGER NOT NULL, " +
                        "source TEXT NOT NULL, " +
                        "memo TEXT NOT NULL, " +
                        "transactionType TEXT NOT NULL, " +
                        "paymentMethod TEXT NOT NULL, " +
                        "importedAtMillis INTEGER NOT NULL, " +
                        "deletedAtMillis INTEGER NOT NULL, " +
                        "PRIMARY KEY(sourceFingerprint))",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_deleted_transactions_deletedAtMillis " +
                        "ON deleted_transactions(deletedAtMillis)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_deleted_transactions_occurredAtMillis " +
                        "ON deleted_transactions(occurredAtMillis)",
                )
            }
        }

        internal val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS advertisement_sources (" +
                        "sourceFingerprint TEXT NOT NULL, PRIMARY KEY(sourceFingerprint))",
                )
                // Older imports did not retain message bodies. Seed identifiable saved ads;
                // rescanning each month's inbox identifies ads whose marker was elsewhere.
                db.execSQL(
                    "INSERT OR IGNORE INTO advertisement_sources (sourceFingerprint) " +
                        "SELECT sourceFingerprint FROM transactions " +
                        "WHERE source IN ('SMS', 'MMS') AND instr(merchant, '광고') > 0 " +
                        "UNION SELECT sourceFingerprint FROM deleted_transactions " +
                        "WHERE source IN ('SMS', 'MMS') AND instr(merchant, '광고') > 0",
                )
            }
        }

        private val SEED_DEFAULT_CARDS_CALLBACK = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                seedDefaultCards(db)
                seedDefaultCategories(db)
                seedDefaultPerformanceExclusions(db)
            }
        }

        private fun createCardPerformanceTables(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS card_performance_tiers (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "cardProfileId INTEGER NOT NULL, " +
                    "minimumSpendWon INTEGER NOT NULL, " +
                    "benefitWon INTEGER NOT NULL, " +
                    "FOREIGN KEY(cardProfileId) REFERENCES card_profiles(id) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_card_performance_tiers_cardProfileId " +
                    "ON card_performance_tiers(cardProfileId)",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS " +
                    "index_card_performance_tiers_cardProfileId_minimumSpendWon " +
                    "ON card_performance_tiers(cardProfileId, minimumSpendWon)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS card_performance_exclusions (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "cardProfileId INTEGER NOT NULL, " +
                    "phrase TEXT NOT NULL, " +
                    "normalizedPhrase TEXT NOT NULL, " +
                    "isActive INTEGER NOT NULL DEFAULT 1, " +
                    "FOREIGN KEY(cardProfileId) REFERENCES card_profiles(id) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_card_performance_exclusions_cardProfileId " +
                    "ON card_performance_exclusions(cardProfileId)",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS " +
                    "index_card_performance_exclusions_cardProfileId_normalizedPhrase " +
                    "ON card_performance_exclusions(cardProfileId, normalizedPhrase)",
            )
        }

        private fun createCategoryTables(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS categories (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "name TEXT NOT NULL, " +
                    "normalizedName TEXT NOT NULL, " +
                    "isSystem INTEGER NOT NULL DEFAULT 0)",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_categories_normalizedName " +
                    "ON categories(normalizedName)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS classification_rules (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "categoryId INTEGER NOT NULL, " +
                    "phrase TEXT NOT NULL, " +
                    "normalizedPhrase TEXT NOT NULL, " +
                    "isActive INTEGER NOT NULL DEFAULT 1, " +
                    "FOREIGN KEY(categoryId) REFERENCES categories(id) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_classification_rules_categoryId " +
                    "ON classification_rules(categoryId)",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_classification_rules_normalizedPhrase " +
                    "ON classification_rules(normalizedPhrase)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS deleted_sources (" +
                    "sourceFingerprint TEXT NOT NULL, " +
                    "deletedAtMillis INTEGER NOT NULL, " +
                    "PRIMARY KEY(sourceFingerprint))",
            )
        }

        private fun seedDefaultCategories(db: SupportSQLiteDatabase) {
            val categories = listOf(
                Triple("외식비", "외식비", 0),
                Triple("통신비", "통신비", 0),
                Triple("보험비", "보험비", 0),
                Triple("교통비", "교통비", 0),
                Triple("쇼핑", "쇼핑", 0),
                Triple("생활비", "생활비", 0),
                Triple("의료비", "의료비", 0),
                Triple("기타", "기타", 1),
                Triple("식료품", "식료품", 0),
                Triple("주거/공과금", "주거공과금", 0),
                Triple("여가/문화", "여가문화", 0),
                Triple("교육비", "교육비", 0),
            )
            categories.forEach { (name, normalizedName, isSystem) ->
                db.execSQL(
                    "INSERT OR IGNORE INTO categories " +
                        "(name, normalizedName, isSystem) VALUES (?, ?, ?)",
                    arrayOf<Any>(name, normalizedName, isSystem),
                )
            }
            seedDefaultClassificationRules(db)
        }

        private fun seedDefaultClassificationRules(db: SupportSQLiteDatabase) {
            val rules = mapOf(
                "외식비" to listOf(
                    "우아한형제들", "배달의민족", "배민", "요기요", "쿠팡이츠",
                    "스타벅스", "투썸플레이스", "이디야", "메가커피", "컴포즈커피",
                    "맥도날드", "버거킹", "롯데리아", "교촌", "BHC", "BBQ",
                    "식당", "음식점", "카페", "커피",
                ),
                "통신비" to listOf(
                    "LG유플러스통신", "SK텔레콤", "KT통신요금", "KT알뜰폰",
                    "알뜰폰", "헬로모바일", "세븐모바일", "리브모바일", "통신요금",
                ),
                "보험비" to listOf(
                    "건강보험공단", "국민건강보험", "국민연금", "지역연금",
                    "삼성생명", "한화생명", "교보생명", "현대해상",
                    "DB손해보험", "메리츠화재", "보험료",
                ),
                "교통비" to listOf(
                    "카카오T", "UT택시", "티머니", "코레일", "SRT", "하이패스",
                    "한국도로공사", "주유소", "SK에너지", "GS칼텍스",
                    "S-OIL", "현대오일뱅크", "택시", "기아오토큐", "오토큐",
                    "자동차정비", "카센터",
                ),
                "쇼핑" to listOf(
                    "네이버페이", "네이버파이낸", "쿠팡", "11번가", "G마켓",
                    "옥션", "무신사", "올리브영", "다이소", "백화점", "쇼핑",
                ),
                "생활비" to listOf(
                    "CU", "GS25", "세븐일레븐", "이마트24", "생활용품",
                    "세탁", "미용실", "헤어", "정수기",
                ),
                "의료비" to listOf(
                    "병원", "의원", "약국", "치과", "한의원", "건강검진",
                    "안과", "정형외과",
                ),
                "식료품" to listOf(
                    "이마트", "홈플러스", "롯데마트", "하나로마트", "마켓컬리",
                    "컬리", "슈퍼마켓", "식자재",
                ),
                "주거공과금" to listOf(
                    "관리비", "한국전력", "전기요금", "도시가스", "수도요금",
                    "아파트관리", "월세",
                ),
                "여가문화" to listOf(
                    "노래연습장", "노래방", "CGV", "메가박스", "롯데시네마",
                    "넷플릭스", "유튜브프리미엄", "멜론", "지니뮤직",
                    "게임", "골프", "여행", "호텔", "숙박",
                ),
                "교육비" to listOf(
                    "학원", "교보문고", "알라딘", "예스24", "교육",
                    "수강료", "학교", "어린이집",
                ),
            )
            rules.forEach { (categoryNormalizedName, phrases) ->
                phrases.forEach { phrase ->
                    db.execSQL(
                        "INSERT OR IGNORE INTO classification_rules " +
                            "(categoryId, phrase, normalizedPhrase, isActive) " +
                            "SELECT id, ?, ?, 1 FROM categories " +
                            "WHERE normalizedName = ? LIMIT 1",
                        arrayOf<Any>(
                            phrase,
                            normalizeCategoryRule(phrase),
                            categoryNormalizedName,
                        ),
                    )
                }
            }
        }

        private fun normalizeCategoryRule(value: String): String = value
            .trim()
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]"), "")

        private fun seedDefaultCards(db: SupportSQLiteDatabase) {
            db.execSQL(
                "INSERT OR IGNORE INTO card_profiles " +
                    "(id, displayName, normalizedName, isActive) " +
                    "VALUES (1, '롯데카드', '롯데카드', 1)",
            )
            db.execSQL(
                "INSERT OR IGNORE INTO card_profiles " +
                    "(id, displayName, normalizedName, isActive) " +
                    "VALUES (2, '현대카드', '현대카드', 1)",
            )
            db.execSQL(
                "INSERT OR IGNORE INTO card_detection_rules " +
                    "(cardProfileId, phrase, normalizedPhrase, isActive) " +
                    "VALUES (1, '로카 X 세라젬', '로카 x 세라젬', 1)",
            )
            db.execSQL(
                "INSERT OR IGNORE INTO card_detection_rules " +
                    "(cardProfileId, phrase, normalizedPhrase, isActive) " +
                    "VALUES (1, '[롯데카드]', '[롯데카드]', 1)",
            )
            db.execSQL(
                "INSERT OR IGNORE INTO card_detection_rules " +
                    "(cardProfileId, phrase, normalizedPhrase, isActive) " +
                    "VALUES (2, 'LGU+ M Ed3(통할2.0)', 'lgu+ m ed3(통할2.0)', 1)",
            )
            db.execSQL(
                "INSERT OR IGNORE INTO card_detection_rules " +
                    "(cardProfileId, phrase, normalizedPhrase, isActive) " +
                    "VALUES (2, '[현대카드]', '[현대카드]', 1)",
            )
            db.execSQL(
                "INSERT OR IGNORE INTO card_detection_rules " +
                    "(cardProfileId, phrase, normalizedPhrase, isActive) " +
                    "VALUES (2, '현대카드 M', '현대카드 m', 1)",
            )
        }

        private fun seedDefaultPerformanceExclusions(db: SupportSQLiteDatabase) {
            listOf("건강보험공단", "LG유플러스통신").forEach { phrase ->
                db.execSQL(
                    "INSERT OR IGNORE INTO card_performance_exclusions " +
                        "(cardProfileId, phrase, normalizedPhrase, isActive) " +
                        "SELECT id, ?, ?, 1 FROM card_profiles " +
                        "WHERE normalizedName = '현대카드' LIMIT 1",
                    arrayOf<Any>(phrase, normalizeCategoryRule(phrase)),
                )
            }
        }
    }
}
