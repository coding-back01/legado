package io.legado.app.model.read

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

@Parcelize
data class ReadingTimeState(
    var version: Int = CURRENT_VERSION,
    var recentLogSecondsPerUnit: Double = 0.0,
    var recentLogMad: Double = 0.0,
    var recentEvidenceMillis: Long = 0L,
    var longTermLogSecondsPerUnit: Double = 0.0,
    var longTermLogMad: Double = 0.0,
    var longTermEvidenceMillis: Long = 0L,
    var acceptedSampleCount: Int = 0,
    var totalEffectiveReadingMillis: Long = 0L,
    var highestChapterCoordinate: Double = 0.0,
    var bookIdentityHash: Long = 0L,
    var tocChapterCount: Int = 0,
    var tocPrefixHash: Long = 0L,
    var sourceLastModified: Long = 0L,
) : Parcelable {

    fun isChapterQualified(): Boolean {
        return version == CURRENT_VERSION &&
                secondsPerVisibleUnit() > 0.0 &&
                effectiveEvidenceMillis() >= MIN_EFFECTIVE_EVIDENCE_MILLIS
    }

    fun isContentQualified(): Boolean = isChapterQualified()

    fun secondsPerVisibleUnit(): Double {
        val logRate = when {
            recentEvidenceMillis > 0L && recentLogSecondsPerUnit.isFinite() -> {
                recentLogSecondsPerUnit
            }

            longTermEvidenceMillis > 0L && longTermLogSecondsPerUnit.isFinite() -> {
                longTermLogSecondsPerUnit
            }

            else -> return 0.0
        }
        return exp(logRate)
    }

    fun effectiveEvidenceMillis(): Long {
        return recentEvidenceMillis + min(longTermEvidenceMillis, LONG_CONFIDENCE_CAP_MILLIS)
    }

    companion object {
        const val CURRENT_VERSION = 2
        const val MIN_EFFECTIVE_EVIDENCE_MILLIS = 60_000L
        private const val LONG_CONFIDENCE_CAP_MILLIS = 300_000L
    }
}

data class ReadingTimePosition(
    val chapterIndex: Int,
    val chapterProgress: Double,
) {
    val normalizedProgress: Double
        get() = chapterProgress.coerceIn(0.0, 1.0)

    val chapterCoordinate: Double
        get() = chapterIndex.coerceAtLeast(0) + normalizedProgress
}

data class ReadingTimeAnchor(
    val position: ReadingTimePosition,
    val visibleTextUnits: Int,
    val textReliability: Double = 1.0,
)

object VisibleTextUnits {

    fun count(text: CharSequence): Int {
        var count = 0
        var index = 0
        while (index < text.length) {
            val first = text[index]
            val codePoint = if (Character.isHighSurrogate(first) &&
                index + 1 < text.length && Character.isLowSurrogate(text[index + 1])
            ) {
                Character.toCodePoint(first, text[index + 1]).also { index++ }
            } else {
                first.code
            }
            if (isVisible(codePoint)) count++
            index++
        }
        return count
    }

    private fun isVisible(codePoint: Int): Boolean {
        if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) return false
        return when (Character.getType(codePoint).toByte()) {
            Character.CONTROL,
            Character.FORMAT,
            Character.LINE_SEPARATOR,
            Character.PARAGRAPH_SEPARATOR,
            Character.SPACE_SEPARATOR -> false

            else -> true
        }
    }
}

enum class ReadingTimeEstimateMode {
    CHAPTER,
    HYBRID_CONTENT,
    FULL_CONTENT,
}

sealed class ReadingTimeEstimate {
    data object Unavailable : ReadingTimeEstimate()
    data object Learning : ReadingTimeEstimate()
    data class Ready(
        val remainingSeconds: Double,
        val mode: ReadingTimeEstimateMode,
    ) : ReadingTimeEstimate()
}

data class ReadingTimeAdvanceResult(
    val estimate: ReadingTimeEstimate,
    val sampleAccepted: Boolean,
    val sampleWeight: Double = 0.0,
)

data class ReadingTimeDiagnostics(
    val secondsPerVisibleUnit: Double,
    val speedConfidence: Double,
    val remainingConfidence: Double,
    val effectiveEvidenceMillis: Long,
    val recentEvidenceMillis: Long,
    val longTermEvidenceMillis: Long,
    val recentLogMad: Double,
    val lastSampleWeight: Double,
    val speedReason: ReadingTimeConfidenceReason,
    val remainingReason: ReadingTimeConfidenceReason,
)

enum class ReadingTimeConfidenceReason {
    NONE,
    NO_EVIDENCE,
    INSUFFICIENT_EVIDENCE,
    HIGH_DISPERSION,
    UNCERTAIN_REMAINING_AMOUNT,
}

class ReadingTimeIndexSnapshot private constructor(
    val rawLengths: IntArray,
    val visibleLengths: IntArray,
    private val estimatedVisibleLengths: DoubleArray,
    private val estimatedVisiblePrefix: DoubleArray,
    private val exactVisiblePrefix: IntArray,
    private val contentCountPrefix: IntArray,
    val medianRawLength: Int,
    val knownContentCount: Int,
    val knownVisibleCount: Int,
    val contentChapterCount: Int,
    val remainingConfidence: Double,
    val mode: ReadingTimeEstimateMode,
    val bookIdentityHash: Long,
    val tocPrefixHash: Long,
) {

    val chapterCount: Int
        get() = rawLengths.size

    fun remainingChapterUnits(position: ReadingTimePosition): Double {
        if (chapterCount == 0) return 0.0
        return max(0.0, chapterCount - position.chapterCoordinate)
    }

    fun estimatedVisibleLength(index: Int): Double {
        return estimatedVisibleLengths.getOrElse(index) { 0.0 }
    }

    fun contentCoordinate(position: ReadingTimePosition): Double? {
        val index = position.chapterIndex
        if (index !in estimatedVisibleLengths.indices) return null
        val chapterLength = estimatedVisibleLengths[index]
        if (chapterLength <= 0.0) return null
        return estimatedVisiblePrefix[index] + chapterLength * position.normalizedProgress
    }

    fun hasUnknownContentBetween(startChapter: Int, endChapter: Int): Boolean {
        if (startChapter !in visibleLengths.indices || endChapter !in visibleLengths.indices) {
            return true
        }
        val start = min(startChapter, endChapter)
        val endExclusive = max(startChapter, endChapter) + 1
        val contentCount = contentCountPrefix[endExclusive] - contentCountPrefix[start]
        val exactCount = exactVisiblePrefix[endExclusive] - exactVisiblePrefix[start]
        return exactCount != contentCount
    }

    fun remainingContentUnits(position: ReadingTimePosition): Double {
        if (contentChapterCount == 0) return 0.0
        val index = position.chapterIndex
        if (index !in estimatedVisibleLengths.indices) return 0.0
        val currentRemaining = estimatedVisibleLengths[index] * (1.0 - position.normalizedProgress)
        return max(
            0.0,
            currentRemaining + estimatedVisiblePrefix[chapterCount] - estimatedVisiblePrefix[index + 1],
        )
    }

    @Deprecated("Use remainingContentUnits")
    fun remainingContentBytes(position: ReadingTimePosition): Double? {
        return remainingContentUnits(position).takeIf { it > 0.0 }
    }

    companion object {
        const val UNKNOWN_LENGTH = -1
        const val VOLUME_LENGTH = 0
        private const val DISPLAYABLE_REMAINING_CONFIDENCE = 0.55

        fun empty(chapterCount: Int = 0): ReadingTimeIndexSnapshot {
            val size = chapterCount.coerceAtLeast(0)
            return create(
                rawLengths = IntArray(size) { UNKNOWN_LENGTH },
                visibleLengths = IntArray(size) { UNKNOWN_LENGTH },
            )
        }

        fun create(
            rawLengths: IntArray,
            visibleLengths: IntArray = IntArray(rawLengths.size) { UNKNOWN_LENGTH },
            bookIdentityHash: Long = 0L,
            tocPrefixHash: Long = 0L,
        ): ReadingTimeIndexSnapshot {
            require(rawLengths.size == visibleLengths.size)
            val raw = rawLengths.copyOf()
            val visible = visibleLengths.copyOf()
            val contentIndices = ArrayList<Int>()
            val knownRaw = ArrayList<Int>()
            val knownVisible = ArrayList<Int>()
            val logRatios = ArrayList<Double>()

            for (index in raw.indices) {
                raw[index] = normalizeLength(raw[index])
                visible[index] = normalizeLength(visible[index])
                if (raw[index] == VOLUME_LENGTH) visible[index] = VOLUME_LENGTH
                if (raw[index] != VOLUME_LENGTH || visible[index] != VOLUME_LENGTH) {
                    contentIndices.add(index)
                }
                if (raw[index] > 0) knownRaw.add(raw[index])
                if (visible[index] > 0) knownVisible.add(visible[index])
                if (raw[index] > 0 && visible[index] > 0) {
                    logRatios.add(ln(visible[index].toDouble() / raw[index]))
                }
            }

            knownRaw.sort()
            knownVisible.sort()
            logRatios.sort()
            val medianRaw = medianInt(knownRaw)
            val medianVisible = medianInt(knownVisible).toDouble()
            val ratioCenter = medianDouble(logRatios)
            val ratioMad = if (logRatios.isEmpty()) 0.0 else {
                val deviations = logRatios.mapTo(ArrayList(logRatios.size)) { abs(it - ratioCenter) }
                deviations.sort()
                medianDouble(deviations)
            }
            val ratio = if (logRatios.isEmpty()) 0.0 else exp(ratioCenter)
            val estimated = DoubleArray(raw.size)
            for (index in estimated.indices) {
                estimated[index] = when {
                    visible[index] > 0 -> visible[index].toDouble()
                    visible[index] == VOLUME_LENGTH || raw[index] == VOLUME_LENGTH -> 0.0
                    raw[index] > 0 && ratio > 0.0 -> raw[index] * ratio
                    medianVisible > 0.0 -> medianVisible
                    raw[index] > 0 -> raw[index].toDouble()
                    medianRaw > 0 -> medianRaw.toDouble()
                    else -> 0.0
                }
            }

            val estimatedPrefix = DoubleArray(raw.size + 1)
            val exactPrefix = IntArray(raw.size + 1)
            val contentPrefix = IntArray(raw.size + 1)
            for (index in raw.indices) {
                estimatedPrefix[index + 1] = estimatedPrefix[index] + estimated[index]
                exactPrefix[index + 1] = exactPrefix[index]
                contentPrefix[index + 1] = contentPrefix[index]
                if (index in contentIndices) contentPrefix[index + 1]++
                if (visible[index] > 0) exactPrefix[index + 1]++
            }
            val contentCount = contentIndices.size
            val exactCount = knownVisible.size
            val exactCoverage = if (contentCount == 0) 0.0 else exactCount.toDouble() / contentCount
            val proxyConfidence = when {
                logRatios.isNotEmpty() -> {
                    (1.0 - exp(-logRatios.size / 2.0)) * exp(-ratioMad / 0.5)
                }

                knownVisible.isNotEmpty() -> {
                    0.75 * (1.0 - exp(-knownVisible.size / 2.0))
                }

                else -> 0.0
            }
            val confidence = when {
                contentCount == 0 -> 0.0
                exactCount == contentCount -> 1.0
                else -> (exactCoverage + (1.0 - exactCoverage) * proxyConfidence).coerceIn(0.0, 1.0)
            }
            val mode = when {
                contentCount > 0 && exactCount == contentCount -> ReadingTimeEstimateMode.FULL_CONTENT
                confidence >= DISPLAYABLE_REMAINING_CONFIDENCE -> ReadingTimeEstimateMode.HYBRID_CONTENT
                else -> ReadingTimeEstimateMode.CHAPTER
            }
            return ReadingTimeIndexSnapshot(
                rawLengths = raw,
                visibleLengths = visible,
                estimatedVisibleLengths = estimated,
                estimatedVisiblePrefix = estimatedPrefix,
                exactVisiblePrefix = exactPrefix,
                contentCountPrefix = contentPrefix,
                medianRawLength = medianRaw,
                knownContentCount = knownRaw.size,
                knownVisibleCount = exactCount,
                contentChapterCount = contentCount,
                remainingConfidence = confidence,
                mode = mode,
                bookIdentityHash = bookIdentityHash,
                tocPrefixHash = tocPrefixHash,
            )
        }

        private fun normalizeLength(value: Int): Int {
            return if (value < UNKNOWN_LENGTH) UNKNOWN_LENGTH else value
        }

        private fun medianInt(values: List<Int>): Int {
            if (values.isEmpty()) return 0
            val middle = values.size / 2
            return if (values.size % 2 == 1) {
                values[middle]
            } else {
                ((values[middle - 1].toLong() + values[middle]) / 2L).toInt()
            }
        }

        private fun medianDouble(values: List<Double>): Double {
            if (values.isEmpty()) return 0.0
            val middle = values.size / 2
            return if (values.size % 2 == 1) {
                values[middle]
            } else {
                (values[middle - 1] + values[middle]) / 2.0
            }
        }
    }
}

class ReadingTimeEstimator(
    initialState: ReadingTimeState? = null,
    private val elapsedRealtime: () -> Long = { System.nanoTime() / 1_000_000L },
) {

    private var state = migratedState(initialState)
    private var indexSnapshot = ReadingTimeIndexSnapshot.empty()
    private var anchor: ReadingTimeAnchor? = null
    private var anchorElapsedMillis: Long = 0L
    private var positionDeltaAnchor = false
    private var isActive = false
    private var lastSampleWeight = 0.0
    private val recentWindow = RobustReadingWindow(RECENT_CAPACITY)
    private var restoredRecentCenter = state.recentLogSecondsPerUnit
    private var restoredRecentMad = state.recentLogMad
    private var restoredRecentEvidence = state.recentEvidenceMillis.toDouble()
    private val windowSummary = RobustSummary()
    private val combinedSummary = RobustSummary()

    fun stateSnapshot(): ReadingTimeState = state.copy()

    fun diagnostics(): ReadingTimeDiagnostics {
        val center = fusedLogRate()
        val seconds = if (center.isFinite()) exp(center) else 0.0
        return ReadingTimeDiagnostics(
            secondsPerVisibleUnit = seconds,
            speedConfidence = speedConfidence(),
            remainingConfidence = indexSnapshot.remainingConfidence,
            effectiveEvidenceMillis = state.effectiveEvidenceMillis(),
            recentEvidenceMillis = state.recentEvidenceMillis,
            longTermEvidenceMillis = state.longTermEvidenceMillis,
            recentLogMad = state.recentLogMad,
            lastSampleWeight = lastSampleWeight,
            speedReason = speedConfidenceReason(),
            remainingReason = remainingConfidenceReason(),
        )
    }

    fun updateIdentity(
        bookIdentityHash: Long,
        tocChapterCount: Int,
        tocPrefixHash: Long,
        sourceLastModified: Long = state.sourceLastModified,
    ) {
        state.bookIdentityHash = bookIdentityHash
        state.tocChapterCount = tocChapterCount
        state.tocPrefixHash = tocPrefixHash
        state.sourceLastModified = sourceLastModified
    }

    fun updateIndex(snapshot: ReadingTimeIndexSnapshot) {
        indexSnapshot = snapshot
    }

    fun resume(readingAnchor: ReadingTimeAnchor, nowMillis: Long = elapsedRealtime()) {
        isActive = true
        anchor = readingAnchor
        anchorElapsedMillis = nowMillis
        positionDeltaAnchor = false
    }

    fun resume(position: ReadingTimePosition, nowMillis: Long = elapsedRealtime()) {
        isActive = true
        anchor = ReadingTimeAnchor(position, 0)
        anchorElapsedMillis = nowMillis
        positionDeltaAnchor = true
    }

    fun pause() {
        isActive = false
        anchor = null
        positionDeltaAnchor = false
    }

    fun reanchor(readingAnchor: ReadingTimeAnchor, nowMillis: Long = elapsedRealtime()) {
        if (!isActive) return
        anchor = readingAnchor
        anchorElapsedMillis = nowMillis
        positionDeltaAnchor = false
    }

    fun reanchor(position: ReadingTimePosition, nowMillis: Long = elapsedRealtime()) {
        if (!isActive) return
        anchor = ReadingTimeAnchor(position, 0)
        anchorElapsedMillis = nowMillis
        positionDeltaAnchor = true
    }

    fun onForward(
        readingAnchor: ReadingTimeAnchor,
        allowTraining: Boolean = true,
        nowMillis: Long = elapsedRealtime(),
    ): ReadingTimeAdvanceResult {
        val sampleAccepted = advance(readingAnchor, allowTraining, nowMillis)
        return ReadingTimeAdvanceResult(
            estimate = estimate(readingAnchor.position),
            sampleAccepted = sampleAccepted,
            sampleWeight = lastSampleWeight,
        )
    }

    fun advance(
        readingAnchor: ReadingTimeAnchor,
        allowTraining: Boolean = true,
        nowMillis: Long = elapsedRealtime(),
    ): Boolean {
        val previous = anchor
        val elapsed = nowMillis - anchorElapsedMillis
        val accepted = isActive && allowTraining && previous != null &&
                isAdjacentForward(previous.position, readingAnchor.position) &&
                elapsed > 0L && previous.visibleTextUnits > 0 &&
                previous.textReliability > 0.0
        val sampleWeight = if (accepted) {
            updateSpeed(
                previous = checkNotNull(previous),
                current = readingAnchor,
                elapsedMillis = elapsed,
            )
        } else {
            0.0
        }
        if (isActive) {
            anchor = readingAnchor
            anchorElapsedMillis = nowMillis
            positionDeltaAnchor = false
        }
        lastSampleWeight = sampleWeight
        return accepted
    }

    fun onForward(
        position: ReadingTimePosition,
        allowTraining: Boolean = true,
        nowMillis: Long = elapsedRealtime(),
    ): ReadingTimeAdvanceResult {
        val previous = anchor
        if (previous == null || !positionDeltaAnchor) {
            return onForward(ReadingTimeAnchor(position, 0), allowTraining, nowMillis)
        }
        val units = approximateUnitsBetween(previous.position, position)
        val elapsed = nowMillis - anchorElapsedMillis
        val accepted = isActive && allowTraining &&
                isAdjacentForward(previous.position, position) && elapsed > 0L && units > 0.0
        val next = ReadingTimeAnchor(position, 0)
        val sampleWeight = if (accepted) {
            updateSpeed(
                previous = ReadingTimeAnchor(previous.position, max(1, units.roundToLong().toInt())),
                current = next,
                elapsedMillis = elapsed,
            )
        } else {
            0.0
        }
        if (isActive) {
            anchor = next
            anchorElapsedMillis = nowMillis
            positionDeltaAnchor = true
        }
        lastSampleWeight = sampleWeight
        return ReadingTimeAdvanceResult(
            estimate = estimate(position),
            sampleAccepted = accepted,
            sampleWeight = sampleWeight,
        )
    }

    fun estimate(position: ReadingTimePosition): ReadingTimeEstimate {
        if (indexSnapshot.chapterCount == 0) return ReadingTimeEstimate.Unavailable
        if (isFinished(position)) {
            return ReadingTimeEstimate.Ready(0.0, indexSnapshot.mode)
        }
        val center = fusedLogRate()
        if (!center.isFinite()) return ReadingTimeEstimate.Learning
        if (speedConfidence() < DISPLAYABLE_SPEED_CONFIDENCE ||
            indexSnapshot.remainingConfidence < DISPLAYABLE_REMAINING_CONFIDENCE
        ) {
            return ReadingTimeEstimate.Learning
        }
        val remainingUnits = indexSnapshot.remainingContentUnits(position)
        if (remainingUnits <= 0.0) return ReadingTimeEstimate.Learning
        return ReadingTimeEstimate.Ready(
            remainingSeconds = (exp(center) * remainingUnits).coerceAtLeast(0.0),
            mode = indexSnapshot.mode,
        )
    }

    fun reset(position: ReadingTimePosition? = null, nowMillis: Long = elapsedRealtime()) {
        val identity = state
        state = ReadingTimeState(
            bookIdentityHash = identity.bookIdentityHash,
            tocChapterCount = identity.tocChapterCount,
            tocPrefixHash = identity.tocPrefixHash,
            sourceLastModified = identity.sourceLastModified,
        )
        recentWindow.clear()
        restoredRecentCenter = 0.0
        restoredRecentMad = 0.0
        restoredRecentEvidence = 0.0
        lastSampleWeight = 0.0
        if (isActive && position != null) {
            anchor = ReadingTimeAnchor(position, 0)
            anchorElapsedMillis = nowMillis
            positionDeltaAnchor = true
        } else {
            anchor = null
            positionDeltaAnchor = false
        }
    }

    private fun updateSpeed(
        previous: ReadingTimeAnchor,
        current: ReadingTimeAnchor,
        elapsedMillis: Long,
    ): Double {
        val units = previous.visibleTextUnits.toDouble()
        val elapsedSeconds = elapsedMillis / 1_000.0
        val logRate = ln(elapsedSeconds / units)
        if (!logRate.isFinite()) return 0.0
        val currentCenter = fusedLogRate()
        val expectedMillis = if (!currentCenter.isFinite()) {
            120_000.0 * (1.0 - exp(-elapsedMillis / 120_000.0))
        } else {
            val expected = exp(currentCenter) * units * 1_000.0
            val safeExpected = expected.coerceAtLeast(1.0)
            safeExpected * (1.0 - exp(-elapsedMillis / safeExpected)) / NORMAL_SATURATION
        }
        val contextWeight = previous.textReliability.coerceIn(0.0, 1.0)
        val effectiveMillis = (expectedMillis * contextWeight).coerceAtLeast(1.0)
        val ageDelta = effectiveMillis.roundToLong()
        val age = state.totalEffectiveReadingMillis + ageDelta
        val oldCenter = fusedLogRate()
        val oldMad = fusedMad()
        val robustWeight = cauchyWeight(logRate, oldCenter, oldMad)
        val sampleWeight = contextWeight * robustWeight

        restoredRecentEvidence *= exp(-LN_2 * effectiveMillis / RECENT_HALF_LIFE_MILLIS)
        recentWindow.add(logRate, effectiveMillis * contextWeight, age.toDouble())
        val recent = recentWindow.summary(age.toDouble(), windowSummary)
        val combinedRecent = combineRecent(recent, combinedSummary)
        state.recentLogSecondsPerUnit = combinedRecent.center
        state.recentLogMad = combinedRecent.mad
        state.recentEvidenceMillis = combinedRecent.evidence.roundToLong()

        val rereadWeight = if (previous.position.chapterCoordinate + 1e-9 < state.highestChapterCoordinate) {
            REREAD_LONG_TERM_WEIGHT
        } else {
            1.0
        }
        updateLongTerm(logRate, effectiveMillis * sampleWeight * rereadWeight)
        state.acceptedSampleCount++
        state.totalEffectiveReadingMillis = age
        state.highestChapterCoordinate = max(
            state.highestChapterCoordinate,
            current.position.chapterCoordinate,
        )
        return sampleWeight
    }

    private fun updateLongTerm(sample: Double, evidence: Double) {
        if (evidence <= 0.0) return
        val oldEvidence = state.longTermEvidenceMillis.toDouble()
        val oldCenter = state.longTermLogSecondsPerUnit
        if (oldEvidence <= 0.0 || !oldCenter.isFinite()) {
            state.longTermLogSecondsPerUnit = sample
            state.longTermLogMad = MAD_FLOOR
            state.longTermEvidenceMillis = evidence.roundToLong()
            return
        }
        val decayed = oldEvidence * exp(-LN_2 * evidence / LONG_TERM_HALF_LIFE_MILLIS)
        val residualWeight = cauchyWeight(sample, oldCenter, state.longTermLogMad)
        val acceptedEvidence = evidence * residualWeight
        val total = decayed + acceptedEvidence
        if (total <= 0.0) return
        val center = (oldCenter * decayed + sample * acceptedEvidence) / total
        val deviation = abs(sample - center)
        state.longTermLogSecondsPerUnit = center
        state.longTermLogMad = max(
            MAD_FLOOR,
            (state.longTermLogMad * decayed + deviation * acceptedEvidence) / total,
        )
        state.longTermEvidenceMillis = total.roundToLong()
    }

    private fun combineRecent(window: RobustSummary, out: RobustSummary): RobustSummary {
        val restoredWeight = restoredRecentEvidence.coerceAtLeast(0.0)
        if (window.evidence <= 0.0) {
            return out.set(
                restoredRecentCenter,
                max(restoredRecentMad, MAD_FLOOR),
                restoredWeight,
            )
        }
        if (restoredWeight <= 0.0 || !restoredRecentCenter.isFinite()) {
            return out.set(window.center, window.mad, window.evidence)
        }
        val total = restoredWeight + window.evidence
        val center = (restoredRecentCenter * restoredWeight + window.center * window.evidence) / total
        val mad = (
                max(restoredRecentMad, MAD_FLOOR) * restoredWeight +
                        max(window.mad, MAD_FLOOR) * window.evidence
                ) / total
        return out.set(center, max(mad, MAD_FLOOR), total)
    }

    private fun fusedLogRate(): Double {
        val recentCenter = state.recentLogSecondsPerUnit
        val longCenter = state.longTermLogSecondsPerUnit
        val recentValid = state.recentEvidenceMillis > 0L && recentCenter.isFinite()
        val longValid = state.longTermEvidenceMillis > 0L && longCenter.isFinite()
        if (!recentValid) return if (longValid) longCenter else Double.NaN
        if (!longValid) return recentCenter
        val recentWeight = state.recentEvidenceMillis.toDouble().coerceAtLeast(1.0) /
                max(state.recentLogMad, MAD_FLOOR)
        val longEvidence = LONG_FUSION_CAP_MILLIS *
                (1.0 - exp(-state.longTermEvidenceMillis / LONG_FUSION_CAP_MILLIS))
        val longWeight = longEvidence.coerceAtLeast(1.0) / max(state.longTermLogMad, MAD_FLOOR)
        return (recentCenter * recentWeight + longCenter * longWeight) / (recentWeight + longWeight)
    }

    private fun fusedMad(): Double {
        val recentValue = state.recentLogMad
        val recent = if (recentValue > 0.0) recentValue else MAD_FLOOR
        val longValue = state.longTermLogMad
        val long = if (longValue > 0.0) longValue else MAD_FLOOR
        return max(MAD_FLOOR, min(recent, long))
    }

    private fun speedConfidence(): Double {
        val center = fusedLogRate()
        if (!center.isFinite()) return 0.0
        val evidence = state.effectiveEvidenceMillis().toDouble()
        val evidenceConfidence = 1.0 - exp(-evidence / SPEED_CONFIDENCE_SCALE_MILLIS)
        val dispersionConfidence = exp(-fusedMad() / SPEED_DISPERSION_SCALE)
        return (evidenceConfidence * dispersionConfidence).coerceIn(0.0, 1.0)
    }

    private fun speedConfidenceReason(): ReadingTimeConfidenceReason {
        val center = fusedLogRate()
        if (!center.isFinite()) return ReadingTimeConfidenceReason.NO_EVIDENCE
        val evidenceConfidence = 1.0 - exp(
            -state.effectiveEvidenceMillis().toDouble() / SPEED_CONFIDENCE_SCALE_MILLIS
        )
        if (evidenceConfidence < DISPLAYABLE_SPEED_CONFIDENCE) {
            return ReadingTimeConfidenceReason.INSUFFICIENT_EVIDENCE
        }
        if (speedConfidence() < DISPLAYABLE_SPEED_CONFIDENCE) {
            return ReadingTimeConfidenceReason.HIGH_DISPERSION
        }
        return ReadingTimeConfidenceReason.NONE
    }

    private fun remainingConfidenceReason(): ReadingTimeConfidenceReason {
        if (indexSnapshot.chapterCount == 0 || indexSnapshot.knownVisibleCount == 0) {
            return ReadingTimeConfidenceReason.NO_EVIDENCE
        }
        if (indexSnapshot.remainingConfidence < DISPLAYABLE_REMAINING_CONFIDENCE) {
            return ReadingTimeConfidenceReason.UNCERTAIN_REMAINING_AMOUNT
        }
        return ReadingTimeConfidenceReason.NONE
    }

    private fun approximateUnitsBetween(
        previous: ReadingTimePosition,
        current: ReadingTimePosition,
    ): Double {
        val previousContent = indexSnapshot.contentCoordinate(previous)
        val currentContent = indexSnapshot.contentCoordinate(current)
        if (previousContent != null && currentContent != null && currentContent > previousContent) {
            return currentContent - previousContent
        }
        return max(0.0, current.chapterCoordinate - previous.chapterCoordinate) * LEGACY_CHAPTER_UNITS
    }

    private fun isFinished(position: ReadingTimePosition): Boolean {
        val chapterCount = indexSnapshot.chapterCount
        return position.chapterIndex >= chapterCount - 1 && position.normalizedProgress >= 1.0
    }

    private fun isAdjacentForward(
        previous: ReadingTimePosition,
        current: ReadingTimePosition,
    ): Boolean {
        val chapterDelta = current.chapterIndex - previous.chapterIndex
        if (chapterDelta !in 0..1) return false
        return current.chapterCoordinate > previous.chapterCoordinate
    }

    private fun cauchyWeight(sample: Double, center: Double, mad: Double): Double {
        if (!center.isFinite()) return 1.0
        val scale = max(mad, MAD_FLOOR) * CAUCHY_SCALE
        val z = (sample - center) / scale
        return 1.0 / (1.0 + z * z)
    }

    private class RobustSummary(
        var center: Double = 0.0,
        var mad: Double = 0.0,
        var evidence: Double = 0.0,
    ) {
        fun set(center: Double, mad: Double, evidence: Double): RobustSummary {
            this.center = center
            this.mad = mad
            this.evidence = evidence
            return this
        }
    }

    private class RobustReadingWindow(private val capacity: Int) {
        private val values = DoubleArray(capacity)
        private val evidence = DoubleArray(capacity)
        private val ages = DoubleArray(capacity)
        private val scratchValues = DoubleArray(capacity)
        private val scratchWeights = DoubleArray(capacity)
        private var size = 0
        private var next = 0

        fun clear() {
            size = 0
            next = 0
        }

        fun add(value: Double, sampleEvidence: Double, age: Double) {
            values[next] = value
            evidence[next] = sampleEvidence
            ages[next] = age
            next = (next + 1) % capacity
            if (size < capacity) size++
        }

        fun summary(currentAge: Double, out: RobustSummary): RobustSummary {
            if (size == 0) return out.set(0.0, MAD_FLOOR, 0.0)
            var total = 0.0
            for (index in 0 until size) {
                val weight = evidence[index] * exp(
                    -LN_2 * max(0.0, currentAge - ages[index]) / RECENT_HALF_LIFE_MILLIS
                )
                scratchValues[index] = values[index]
                scratchWeights[index] = weight
                total += weight
            }
            val median = weightedMedian(size)
            for (index in 0 until size) {
                scratchValues[index] = abs(values[index] - median)
                scratchWeights[index] = evidence[index] * exp(
                    -LN_2 * max(0.0, currentAge - ages[index]) / RECENT_HALF_LIFE_MILLIS
                )
            }
            val mad = max(MAD_FLOOR, weightedMedian(size) * MAD_NORMALIZATION)
            var weightedCenter = 0.0
            var robustTotal = 0.0
            val scale = mad * CAUCHY_SCALE
            for (index in 0 until size) {
                val base = evidence[index] * exp(
                    -LN_2 * max(0.0, currentAge - ages[index]) / RECENT_HALF_LIFE_MILLIS
                )
                val z = (values[index] - median) / scale
                val robust = 1.0 / (1.0 + z * z)
                val weight = base * robust
                weightedCenter += values[index] * weight
                robustTotal += weight
            }
            val center = if (robustTotal > 0.0) weightedCenter / robustTotal else median
            return out.set(center, mad, total)
        }

        private fun weightedMedian(count: Int): Double {
            sortPairs(0, count - 1)
            var total = 0.0
            for (index in 0 until count) total += scratchWeights[index]
            if (total <= 0.0) return scratchValues[count / 2]
            val target = total * 0.5
            var cumulative = 0.0
            for (index in 0 until count) {
                cumulative += scratchWeights[index]
                if (cumulative >= target) return scratchValues[index]
            }
            return scratchValues[count - 1]
        }

        private fun sortPairs(low: Int, high: Int) {
            var left = low
            var right = high
            val pivot = scratchValues[(low + high) ushr 1]
            while (left <= right) {
                while (scratchValues[left] < pivot) left++
                while (scratchValues[right] > pivot) right--
                if (left <= right) {
                    val value = scratchValues[left]
                    scratchValues[left] = scratchValues[right]
                    scratchValues[right] = value
                    val weight = scratchWeights[left]
                    scratchWeights[left] = scratchWeights[right]
                    scratchWeights[right] = weight
                    left++
                    right--
                }
            }
            if (low < right) sortPairs(low, right)
            if (left < high) sortPairs(left, high)
        }
    }

    companion object {
        private const val RECENT_CAPACITY = 512
        private const val RECENT_HALF_LIFE_MILLIS = 15.0 * 60_000.0
        private const val LONG_TERM_HALF_LIFE_MILLIS = 8.0 * 60.0 * 60_000.0
        private const val LONG_FUSION_CAP_MILLIS = 180_000.0
        private const val SPEED_CONFIDENCE_SCALE_MILLIS = 60_000.0
        private const val SPEED_DISPERSION_SCALE = 0.5
        private const val DISPLAYABLE_SPEED_CONFIDENCE = 0.55
        private const val DISPLAYABLE_REMAINING_CONFIDENCE = 0.55
        private const val LEGACY_CHAPTER_UNITS = 1_000.0
        private const val REREAD_LONG_TERM_WEIGHT = 0.15
        private const val MAD_FLOOR = 0.12
        private const val MAD_NORMALIZATION = 1.4826
        private const val CAUCHY_SCALE = 2.5
        private const val LN_2 = 0.6931471805599453
        private val NORMAL_SATURATION = 1.0 - exp(-1.0)

        private fun migratedState(initialState: ReadingTimeState?): ReadingTimeState {
            if (initialState == null) return ReadingTimeState()
            if (initialState.version == ReadingTimeState.CURRENT_VERSION) return initialState.copy()
            return ReadingTimeState(
                bookIdentityHash = initialState.bookIdentityHash,
                tocChapterCount = initialState.tocChapterCount,
                tocPrefixHash = initialState.tocPrefixHash,
                sourceLastModified = initialState.sourceLastModified,
            )
        }
    }
}

object ReadingTimeDuration {

    data class HoursMinutes(
        val hours: Long,
        val minutes: Int,
    )

    fun accumulatedMinutes(readMillis: Long): Long {
        return readMillis.coerceAtLeast(0L) / 60_000L
    }

    fun remainingMinutes(remainingSeconds: Double): Long {
        if (!remainingSeconds.isFinite() || remainingSeconds <= 0.0) return 0L
        return ceil(remainingSeconds / 60.0).toLong().coerceAtLeast(1L)
    }

    fun splitMinutes(totalMinutes: Long): HoursMinutes {
        val safeMinutes = totalMinutes.coerceAtLeast(0L)
        return HoursMinutes(
            hours = safeMinutes / 60L,
            minutes = (safeMinutes % 60L).toInt(),
        )
    }
}
