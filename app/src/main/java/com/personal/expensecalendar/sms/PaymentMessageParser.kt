package com.personal.expensecalendar.sms

import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

enum class PaymentStatus {
    APPROVED,
    CANCELED,
}

data class ParsedPayment(
    val cardName: String,
    val merchant: String,
    val amountWon: Long,
    val status: PaymentStatus,
    val occurredAtMillis: Long,
)

data class PaymentReviewDraft(
    val cardName: String?,
    val merchant: String,
    val amountWon: Long?,
    val status: PaymentStatus,
    val occurredAtMillis: Long,
) {
    val missingReasons: List<String>
        get() = buildList {
            if (cardName == null) add("카드 미식별")
            if (amountWon == null) add("금액 미식별")
        }
}

class PaymentMessageParser(
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    private val amountPattern = Regex("([0-9][0-9,]*)\\s*원")
    private val dateTimePattern = Regex(
        "(\\d{1,2})/(\\d{1,2})[,]?\\s*(오전|오후)?\\s*(\\d{1,2}):(\\d{2})",
    )
    private val koreanDatePattern = Regex("(\\d{1,2})월\\s*(\\d{1,2})일")
    private val highwayUsagePattern = Regex("하이패스\\s+\\d+건\\s+[0-9][0-9,]*\\s*원")
    private val directHyundaiMerchantPattern = Regex("님\\s*(.+?)\\s+([0-9][0-9,]*)\\s*원")

    fun parse(
        record: SmsRecord,
        detectionPatterns: List<CardDetectionPattern> = CardDetector.defaultPatterns,
    ): ParsedPayment? {
        val draft = createReviewDraft(record, detectionPatterns)
        val cardName = draft.cardName ?: return null
        val amount = draft.amountWon ?: return null

        return ParsedPayment(
            cardName = cardName,
            merchant = draft.merchant,
            amountWon = amount,
            status = draft.status,
            occurredAtMillis = draft.occurredAtMillis,
        )
    }

    fun createReviewDraft(
        record: SmsRecord,
        detectionPatterns: List<CardDetectionPattern> = CardDetector.defaultPatterns,
    ): PaymentReviewDraft {
        val detectedCard = CardDetector.detect(record.body, detectionPatterns)
            .takeUnless { it == CardDetector.UNKNOWN_CARD }
        val amount = amountPattern.find(record.body)
            ?.groupValues
            ?.get(1)
            ?.replace(",", "")
            ?.toLongOrNull()
        return PaymentReviewDraft(
            cardName = detectedCard,
            merchant = findMerchant(record.body),
            amountWon = amount,
            status = if (
                record.body.contains("승인취소") ||
                record.body.contains("취소")
            ) {
                PaymentStatus.CANCELED
            } else {
                PaymentStatus.APPROVED
            },
            occurredAtMillis = findOccurredAt(record.body, record.receivedAtMillis),
        )
    }

    private fun findOccurredAt(body: String, receivedAtMillis: Long): Long {
        val receivedAt = Instant.ofEpochMilli(receivedAtMillis).atZone(zoneId)
        val dateTimeMatch = dateTimePattern.find(body)
        val koreanDateMatch = koreanDatePattern.find(body)
        val month: Int
        val day: Int
        val hour: Int
        val minute: Int
        if (dateTimeMatch != null) {
            month = dateTimeMatch.groupValues[1].toInt()
            day = dateTimeMatch.groupValues[2].toInt()
            val meridiem = dateTimeMatch.groupValues[3]
            val parsedHour = dateTimeMatch.groupValues[4].toInt()
            hour = when (meridiem) {
                "오전" -> if (parsedHour == 12) 0 else parsedHour
                "오후" -> if (parsedHour == 12) 12 else parsedHour + 12
                else -> parsedHour
            }
            minute = dateTimeMatch.groupValues[5].toInt()
        } else if (koreanDateMatch != null) {
            month = koreanDateMatch.groupValues[1].toInt()
            day = koreanDateMatch.groupValues[2].toInt()
            hour = receivedAt.hour
            minute = receivedAt.minute
        } else {
            return receivedAtMillis
        }

        return runCatching {
            var parsed = ZonedDateTime.of(
                LocalDateTime.of(receivedAt.year, month, day, hour, minute),
                zoneId,
            )
            if (parsed.isAfter(receivedAt.plusDays(45))) {
                parsed = parsed.minusYears(1)
            }
            parsed.toInstant().toEpochMilli()
        }.getOrDefault(receivedAtMillis)
    }

    private fun findMerchant(body: String): String {
        if (highwayUsagePattern.containsMatchIn(body)) return "하이패스"

        if (body.contains("[현대카드]")) {
            directHyundaiMerchantPattern.find(body)
                ?.groupValues
                ?.get(1)
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?.let(::normalizeMerchant)
                ?.let { return it }
        }

        val lines = body.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toList()
        val dateLineIndex = lines.indexOfFirst(dateTimePattern::containsMatchIn)
        if (dateLineIndex >= 0) {
            lines.drop(dateLineIndex + 1)
                .firstOrNull { !isMetadataLine(it) }
                ?.let(::normalizeMerchant)
                ?.let { return it }
        }

        val amountLineIndex = lines.indexOfFirst(amountPattern::containsMatchIn)
        if (amountLineIndex > 0) {
            lines.take(amountLineIndex)
                .firstOrNull { !isMetadataLine(it) }
                ?.let(::normalizeMerchant)
                ?.let { return it }
        }

        return "가맹점 미확인"
    }

    private fun isMetadataLine(line: String): Boolean =
        line == "[Web발신]" ||
            line.startsWith("보낸사람 :") ||
            line.startsWith("보낸 사람 :") ||
            line.contains("로카 X 세라젬", ignoreCase = true) ||
            line.contains("LGU+ M Ed3(통할2.0)", ignoreCase = true) ||
            line.startsWith("[현대카드]") ||
            line.startsWith("[롯데카드]") ||
            line.startsWith("누적") ||
            line.contains("일시불") ||
            amountPattern.containsMatchIn(line) ||
            dateTimePattern.containsMatchIn(line) ||
            Regex("^[가-힣]\\*+[가-힣]").containsMatchIn(line)

    private fun normalizeMerchant(value: String): String = value
        .replace(Regex("^\\[Web발신]\\s*"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
}

object MessageFingerprint {
    fun create(record: SmsRecord): String {
        val transportPrefix = if (record.transport == MessageTransport.SMS) {
            ""
        } else {
            "${record.transport.name}\u0000"
        }
        val source = "$transportPrefix${record.sender}\u0000${record.receivedAtMillis}\u0000${record.body}"
        return MessageDigest.getInstance("SHA-256")
            .digest(source.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
