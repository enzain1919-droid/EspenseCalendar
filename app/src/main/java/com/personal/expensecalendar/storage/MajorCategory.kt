package com.personal.expensecalendar.storage

enum class MajorCategory(val displayName: String) {
    LIVING_EXPENSE("생활비"),
    FIXED_EXPENSE("고정비"),
    JINYOUNG_ALLOWANCE("진영 용돈"),
    ;

    companion object {
        fun fromStored(value: String): MajorCategory =
            entries.firstOrNull { it.name == value } ?: LIVING_EXPENSE
    }
}
