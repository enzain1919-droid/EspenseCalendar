package com.personal.expensecalendar.sms

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.provider.BaseColumns
import android.provider.Telephony
import com.personal.expensecalendar.storage.TransactionDao
import com.personal.expensecalendar.storage.TransactionEntity
import com.personal.expensecalendar.storage.AdvertisementSourceEntity
import com.personal.expensecalendar.storage.CardProfileDao
import com.personal.expensecalendar.storage.CategoryDao
import com.personal.expensecalendar.storage.MajorCategory
import com.personal.expensecalendar.categories.CategoryRepository
import com.personal.expensecalendar.categories.MerchantClassifier

data class SmsRecord(
    val id: Long,
    val sender: String,
    val receivedAtMillis: Long,
    val body: String,
    val transport: MessageTransport = MessageTransport.SMS,
)

enum class MessageTransport {
    SMS,
    MMS,
}

data class SmsInboxScan(
    val range: MonthRange,
    val scannedCount: Int,
    val smsScannedCount: Int,
    val mmsScannedCount: Int,
    val candidates: List<SmsRecord>,
    val advertisementFingerprints: List<String>,
)

data class PaymentPreview(
    val sourceFingerprint: String,
    val cardName: String,
    val merchant: String,
    val amountWon: Long,
    val status: PaymentStatus,
    val occurredAtMillis: Long,
)

data class SmsImportResult(
    val range: MonthRange,
    val scannedCount: Int,
    val smsScannedCount: Int,
    val mmsScannedCount: Int,
    val candidateCount: Int,
    val importedCount: Int,
    val duplicateCount: Int,
    val unparsedCount: Int,
    val deletedCount: Int,
    val totalSavedCount: Int,
    val payments: List<PaymentPreview>,
)

data class UnparsedPaymentMessage(
    val record: SmsRecord,
    val sourceFingerprint: String,
    val draft: PaymentReviewDraft,
)

data class MessageReviewScanResult(
    val range: MonthRange,
    val scannedCount: Int,
    val candidateCount: Int,
    val messages: List<UnparsedPaymentMessage>,
)

internal object MessageReviewCandidateSelector {
    fun select(
        candidates: List<SmsRecord>,
        detectionPatterns: List<CardDetectionPattern>,
        deletedFingerprints: Set<String>,
        existingFingerprints: Set<String>,
        parser: PaymentMessageParser = PaymentMessageParser(),
    ): List<UnparsedPaymentMessage> = candidates.mapNotNull { record ->
        if (PaymentCandidateMatcher.isAdvertisement(record.body)) return@mapNotNull null
        val fingerprint = MessageFingerprint.create(record)
        if (fingerprint in deletedFingerprints || fingerprint in existingFingerprints) {
            return@mapNotNull null
        }
        if (parser.parse(record, detectionPatterns) != null) return@mapNotNull null
        UnparsedPaymentMessage(
            record = record,
            sourceFingerprint = fingerprint,
            draft = parser.createReviewDraft(record, detectionPatterns),
        )
    }
}

class SmsRepository(
    private val contentResolver: ContentResolver,
) {
    fun scanInbox(range: MonthRange): SmsInboxScan {
        val smsRecords = scanSmsInbox(range)
        val mmsRecords = scanMmsInbox(range)
        val allRecords = (smsRecords + mmsRecords)
            .sortedByDescending(SmsRecord::receivedAtMillis)

        return SmsInboxScan(
            range = range,
            scannedCount = allRecords.size,
            smsScannedCount = smsRecords.size,
            mmsScannedCount = mmsRecords.size,
            candidates = allRecords.filter { PaymentCandidateMatcher.isCandidate(it.body) },
            advertisementFingerprints = allRecords
                .filter { PaymentCandidateMatcher.isAdvertisement(it.body) }
                .map(MessageFingerprint::create),
        )
    }

    private fun scanSmsInbox(range: MonthRange): List<SmsRecord> {
        val projection = arrayOf(
            BaseColumns._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.DATE,
            Telephony.Sms.BODY,
        )
        val selection = "${Telephony.Sms.DATE} >= ? AND ${Telephony.Sms.DATE} < ?"
        val selectionArgs = arrayOf(
            range.startInclusiveMillis.toString(),
            range.endExclusiveMillis.toString(),
        )

        val records = mutableListOf<SmsRecord>()

        contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${Telephony.Sms.DATE} DESC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(BaseColumns._ID)
            val senderIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)

            while (cursor.moveToNext()) {
                val body = cursor.getString(bodyIndex).orEmpty()
                records += SmsRecord(
                    id = cursor.getLong(idIndex),
                    sender = cursor.getString(senderIndex).orEmpty(),
                    receivedAtMillis = cursor.getLong(dateIndex),
                    body = body,
                )
            }
        }
        return records
    }

    private fun scanMmsInbox(range: MonthRange): List<SmsRecord> {
        val projection = arrayOf(
            BaseColumns._ID,
            Telephony.Mms.DATE,
        )
        val startSeconds = range.startInclusiveMillis / 1_000L
        val endSeconds = range.endExclusiveMillis / 1_000L
        val selection = "${Telephony.Mms.DATE} >= ? AND ${Telephony.Mms.DATE} < ?"
        val records = mutableListOf<SmsRecord>()

        contentResolver.query(
            Telephony.Mms.Inbox.CONTENT_URI,
            projection,
            selection,
            arrayOf(startSeconds.toString(), endSeconds.toString()),
            "${Telephony.Mms.DATE} DESC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(BaseColumns._ID)
            val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Mms.DATE)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val body = readMmsText(id)
                if (body.isBlank()) continue
                records += SmsRecord(
                    id = id,
                    sender = readMmsSender(id),
                    receivedAtMillis = normalizeMmsTimestamp(cursor.getLong(dateIndex)),
                    body = body,
                    transport = MessageTransport.MMS,
                )
            }
        }
        return records
    }

    private fun readMmsText(mmsId: Long): String {
        val partUri = Uri.parse("content://mms/part")
        val projection = arrayOf(BaseColumns._ID, "ct", "text", "_data")
        val parts = mutableListOf<String>()
        contentResolver.query(
            partUri,
            projection,
            "mid = ? AND ct = ?",
            arrayOf(mmsId.toString(), "text/plain"),
            null,
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(BaseColumns._ID)
            val textIndex = cursor.getColumnIndexOrThrow("text")
            val dataIndex = cursor.getColumnIndexOrThrow("_data")
            while (cursor.moveToNext()) {
                val inlineText = cursor.getString(textIndex).orEmpty()
                val text = if (cursor.getString(dataIndex).isNullOrBlank()) {
                    inlineText
                } else {
                    val itemUri = ContentUris.withAppendedId(partUri, cursor.getLong(idIndex))
                    runCatching {
                        contentResolver.openInputStream(itemUri)
                            ?.bufferedReader()
                            ?.use { it.readText() }
                            .orEmpty()
                    }.getOrDefault(inlineText)
                }
                if (text.isNotBlank()) parts += text.trim()
            }
        }
        return parts.joinToString("\n")
    }

    private fun readMmsSender(mmsId: Long): String {
        val addressUri = Uri.parse("content://mms/$mmsId/addr")
        contentResolver.query(
            addressUri,
            arrayOf("address", "type"),
            null,
            null,
            null,
        )?.use { cursor ->
            val addressIndex = cursor.getColumnIndexOrThrow("address")
            val typeIndex = cursor.getColumnIndexOrThrow("type")
            while (cursor.moveToNext()) {
                if (cursor.getInt(typeIndex) == MMS_FROM_ADDRESS_TYPE) {
                    return cursor.getString(addressIndex).orEmpty()
                }
            }
        }
        return "MMS"
    }

    private fun normalizeMmsTimestamp(value: Long): Long =
        if (value < 10_000_000_000L) value * 1_000L else value

    private companion object {
        const val MMS_FROM_ADDRESS_TYPE = 137
    }
}

class SmsImportRepository(
    private val smsRepository: SmsRepository,
    private val transactionDao: TransactionDao,
    private val cardProfileDao: CardProfileDao,
    private val categoryDao: CategoryDao,
    private val parser: PaymentMessageParser = PaymentMessageParser(),
) {
    suspend fun importCurrentMonth(range: MonthRange): SmsImportResult {
        val scan = smsRepository.scanInbox(range)
        rememberAdvertisements(scan)
        val detectionPatterns = activeDetectionPatterns()
        val analyzedCandidates = scan.candidates.map { record ->
            AnalyzedCandidate(
                record = record,
                payment = parser.parse(record, detectionPatterns),
                fingerprint = MessageFingerprint.create(record),
            )
        }
        val parsedCandidates = analyzedCandidates.mapNotNull { candidate ->
            candidate.payment?.let { payment ->
                Triple(candidate.record, payment, candidate.fingerprint)
            }
        }
        val deletedFingerprints = transactionDao.findDeletedSourceFingerprints().toHashSet()
        val existingFingerprints = transactionDao.findAllSourceFingerprints().toHashSet()
        val parsed = parsedCandidates.filterNot { (_, _, fingerprint) ->
            fingerprint in deletedFingerprints
        }
        val categoryPatterns = CategoryRepository(categoryDao).activePatterns()
        val now = System.currentTimeMillis()
        val entities = parsed.map { (record, payment, fingerprint) ->
            TransactionEntity(
                sourceSmsId = record.id,
                sourceFingerprint = fingerprint,
                cardName = payment.cardName,
                merchant = payment.merchant,
                amountWon = payment.amountWon,
                status = payment.status.name,
                occurredAtMillis = payment.occurredAtMillis,
                categoryName = MerchantClassifier.classify(payment.merchant, categoryPatterns) ?: "기타",
                majorCategory = MajorCategory.LIVING_EXPENSE.name,
                source = record.transport.name,
                importedAtMillis = now,
            )
        }
        val insertedIds = if (entities.isEmpty()) {
            emptyList()
        } else {
            transactionDao.insertAll(entities)
        }
        val importedCount = insertedIds.count { it != -1L }

        return SmsImportResult(
            range = scan.range,
            scannedCount = scan.scannedCount,
            smsScannedCount = scan.smsScannedCount,
            mmsScannedCount = scan.mmsScannedCount,
            candidateCount = scan.candidates.size,
            importedCount = importedCount,
            duplicateCount = parsed.size - importedCount,
            unparsedCount = analyzedCandidates.count { candidate ->
                candidate.payment == null &&
                    candidate.fingerprint !in deletedFingerprints &&
                    candidate.fingerprint !in existingFingerprints
            },
            deletedCount = parsedCandidates.size - parsed.size,
            totalSavedCount = transactionDao.countAll(),
            payments = parsed.map { (_, payment, fingerprint) ->
                PaymentPreview(
                    sourceFingerprint = fingerprint,
                    cardName = payment.cardName,
                    merchant = payment.merchant,
                    amountWon = payment.amountWon,
                    status = payment.status,
                    occurredAtMillis = payment.occurredAtMillis,
                )
            },
        )
    }

    suspend fun findUnparsedMessages(range: MonthRange): MessageReviewScanResult {
        val scan = smsRepository.scanInbox(range)
        rememberAdvertisements(scan)
        val detectionPatterns = activeDetectionPatterns()
        val deletedFingerprints = transactionDao.findDeletedSourceFingerprints().toHashSet()
        val existingFingerprints = transactionDao.findAllSourceFingerprints().toHashSet()
        val messages = MessageReviewCandidateSelector.select(
            candidates = scan.candidates,
            detectionPatterns = detectionPatterns,
            deletedFingerprints = deletedFingerprints,
            existingFingerprints = existingFingerprints,
            parser = parser,
        )
        return MessageReviewScanResult(
            range = range,
            scannedCount = scan.scannedCount,
            candidateCount = scan.candidates.size,
            messages = messages,
        )
    }

    private suspend fun rememberAdvertisements(scan: SmsInboxScan) {
        if (scan.advertisementFingerprints.isEmpty()) return
        transactionDao.rememberAdvertisementSources(
            scan.advertisementFingerprints.map(::AdvertisementSourceEntity),
        )
    }

    private suspend fun activeDetectionPatterns(): List<CardDetectionPattern> =
        cardProfileDao.findActiveDetectionRules().map { rule ->
            CardDetectionPattern(
                cardName = rule.cardName,
                phrase = rule.phrase,
            )
        }
}

private data class AnalyzedCandidate(
    val record: SmsRecord,
    val payment: ParsedPayment?,
    val fingerprint: String,
)
