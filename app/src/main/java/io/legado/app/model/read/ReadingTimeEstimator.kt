package io.legado.app.model.read

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

@Parcelize
data class ReadingTimeState(
    var version: Int = CURRENT_VERSION,
    var chapterSecondsPerUnit: Double = 0.0,
    var contentSecondsPerByte: Double = 0.0,
    var sampleCount: Int = 0,
    var contentSampleCount: Int = 0,
    var validReadingMillis: Long = 0L,
    var contentValidReadingMillis: Long = 0L,
    var bookIdentityHash: Long = 0L,
    var tocChapterCount: Int = 0,
    var tocPrefixHash: Long = 0L,
    var sourceLastModified: Long = 0L,
) : Parcelable {

    fun isChapterQualified(): Boolean {
        return version == CURRENT_VERSION &&
                sampleCount >= MIN_SAMPLE_COUNT &&
                validReadingMillis >= MIN_VALID_READING_MILLIS &&
                chapterSecondsPerUnit > 0.0
    }

    fun isContentQualified(): Boolean {
        return version == CURRENT_VERSION &&
                contentSampleCount >= MIN_SAMPLE_COUNT &&
                contentValidReadingMillis >= MIN_VALID_READING_MILLIS &&
                contentSecondsPerByte > 0.0
    }

    companion object {
        const val CURRENT_VERSION = 1
        const val MIN_SAMPLE_COUNT = 5
        const val MIN_VALID_READING_MILLIS = 60_000L
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
)

class ReadingTimeIndexSnapshot private constructor(
    val rawLengths: IntArray,
    private val knownBytePrefix: LongArray,
    private val knownCountPrefix: IntArray,
    private val contentCountPrefix: IntArray,
    val medianRawLength: Int,
    val knownContentCount: Int,
    val contentChapterCount: Int,
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

    fun contentCoordinate(position: ReadingTimePosition): Double? {
        val index = position.chapterIndex
        if (index !in rawLengths.indices) return null
        val chapterLength = rawLengths[index]
        if (chapterLength <= 0) return null
        return knownBytePrefix[index] + chapterLength * position.normalizedProgress
    }

    fun hasUnknownContentBetween(startChapter: Int, endChapter: Int): Boolean {
        if (startChapter !in rawLengths.indices || endChapter !in rawLengths.indices) {
            return true
        }
        val start = min(startChapter, endChapter)
        val endExclusive = max(startChapter, endChapter) + 1
        val contentCount = contentCountPrefix[endExclusive] - contentCountPrefix[start]
        val knownCount = knownCountPrefix[endExclusive] - knownCountPrefix[start]
        return knownCount != contentCount
    }

    fun remainingContentBytes(position: ReadingTimePosition): Double? {
        if (mode == ReadingTimeEstimateMode.CHAPTER || contentChapterCount == 0) return null
        val index = position.chapterIndex
        if (index !in rawLengths.indices) return null
        val currentLength = estimatedLength(index)
        val currentRemaining = currentLength * (1.0 - position.normalizedProgress)
        val afterIndex = index + 1
        val knownAfter = knownBytePrefix[chapterCount] - knownBytePrefix[afterIndex]
        val contentAfter = contentCountPrefix[chapterCount] - contentCountPrefix[afterIndex]
        val knownCountAfter = knownCountPrefix[chapterCount] - knownCountPrefix[afterIndex]
        val unknownAfter = contentAfter - knownCountAfter
        return max(0.0, currentRemaining + knownAfter + unknownAfter * medianRawLength.toDouble())
    }

    private fun estimatedLength(index: Int): Double {
        val length = rawLengths[index]
        return when {
            length > 0 -> length.toDouble()
            length == 0 -> 0.0
            else -> medianRawLength.toDouble()
        }
    }

    companion object {
        const val UNKNOWN_LENGTH = -1
        const val VOLUME_LENGTH = 0
        const val MIN_HYBRID_CHAPTERS = 20
        const val MIN_HYBRID_COVERAGE = 0.2

        fun empty(chapterCount: Int = 0): ReadingTimeIndexSnapshot {
            return create(IntArray(chapterCount.coerceAtLeast(0)) { UNKNOWN_LENGTH })
        }

        fun create(
            rawLengths: IntArray,
            bookIdentityHash: Long = 0L,
            tocPrefixHash: Long = 0L,
        ): ReadingTimeIndexSnapshot {
            val lengths = rawLengths.copyOf()
            val knownBytePrefix = LongArray(lengths.size + 1)
            val knownCountPrefix = IntArray(lengths.size + 1)
            val contentCountPrefix = IntArray(lengths.size + 1)
            val knownLengths = ArrayList<Int>()
            lengths.forEachIndexed { index, length ->
                val normalizedLength = if (length < UNKNOWN_LENGTH) UNKNOWN_LENGTH else length
                lengths[index] = normalizedLength
                knownBytePrefix[index + 1] = knownBytePrefix[index]
                knownCountPrefix[index + 1] = knownCountPrefix[index]
                contentCountPrefix[index + 1] = contentCountPrefix[index]
                if (normalizedLength != VOLUME_LENGTH) {
                    contentCountPrefix[index + 1]++
                }
                if (normalizedLength > 0) {
                    knownBytePrefix[index + 1] += normalizedLength.toLong()
                    knownCountPrefix[index + 1]++
                    knownLengths.add(normalizedLength)
                }
            }
            knownLengths.sort()
            val median = when {
                knownLengths.isEmpty() -> 0
                knownLengths.size % 2 == 1 -> knownLengths[knownLengths.size / 2]
                else -> {
                    val right = knownLengths.size / 2
                    ((knownLengths[right - 1].toLong() + knownLengths[right]) / 2L).toInt()
                }
            }
            val knownCount = knownCountPrefix.last()
            val contentCount = contentCountPrefix.last()
            val mode = when {
                contentCount > 0 && knownCount == contentCount -> ReadingTimeEstimateMode.FULL_CONTENT
                knownCount >= MIN_HYBRID_CHAPTERS &&
                        knownCount.toDouble() / contentCount.coerceAtLeast(1) >= MIN_HYBRID_COVERAGE -> {
                    ReadingTimeEstimateMode.HYBRID_CONTENT
                }

                else -> ReadingTimeEstimateMode.CHAPTER
            }
            return ReadingTimeIndexSnapshot(
                rawLengths = lengths,
                knownBytePrefix = knownBytePrefix,
                knownCountPrefix = knownCountPrefix,
                contentCountPrefix = contentCountPrefix,
                medianRawLength = median,
                knownContentCount = knownCount,
                contentChapterCount = contentCount,
                mode = mode,
                bookIdentityHash = bookIdentityHash,
                tocPrefixHash = tocPrefixHash,
            )
        }
    }
}

class ReadingTimeEstimator(
    initialState: ReadingTimeState? = null,
    private val elapsedRealtime: () -> Long = { System.nanoTime() / 1_000_000L },
) {

    private var state = initialState?.takeIf { it.version == ReadingTimeState.CURRENT_VERSION }
        ?.copy() ?: ReadingTimeState()
    private var indexSnapshot = ReadingTimeIndexSnapshot.empty()
    private var anchorPosition: ReadingTimePosition? = null
    private var anchorElapsedMillis: Long = 0L
    private var isActive = false
    private var lastEstimateSeconds: Double? = null
    private var lastEstimateMode = ReadingTimeEstimateMode.CHAPTER
    private var transitionFromSeconds: Double? = null
    private var transitionStep = 0

    fun stateSnapshot(): ReadingTimeState = state.copy()

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
        val oldEffectiveMode = effectiveMode(indexSnapshot)
        val newEffectiveMode = effectiveMode(snapshot)
        indexSnapshot = snapshot
        if (oldEffectiveMode != newEffectiveMode && lastEstimateSeconds != null) {
            transitionFromSeconds = lastEstimateSeconds
            transitionStep = 0
        }
    }

    fun resume(position: ReadingTimePosition, nowMillis: Long = elapsedRealtime()) {
        isActive = true
        anchorPosition = position
        anchorElapsedMillis = nowMillis
    }

    fun pause() {
        isActive = false
        anchorPosition = null
    }

    fun reanchor(position: ReadingTimePosition, nowMillis: Long = elapsedRealtime()) {
        if (!isActive) return
        anchorPosition = position
        anchorElapsedMillis = nowMillis
    }

    fun onForward(
        position: ReadingTimePosition,
        allowTraining: Boolean = true,
        nowMillis: Long = elapsedRealtime(),
    ): ReadingTimeAdvanceResult {
        val previous = anchorPosition
        val elapsed = nowMillis - anchorElapsedMillis
        val accepted = isActive && allowTraining && previous != null &&
                isAdjacentForward(previous, position) && elapsed in MIN_SAMPLE_MILLIS..MAX_SAMPLE_MILLIS
        if (accepted) {
            updateSpeed(checkNotNull(previous), position, elapsed)
        }
        if (isActive) {
            anchorPosition = position
            anchorElapsedMillis = nowMillis
        }
        return ReadingTimeAdvanceResult(
            estimate = estimate(position, accepted),
            sampleAccepted = accepted,
        )
    }

    fun estimate(position: ReadingTimePosition): ReadingTimeEstimate {
        return estimate(position, false)
    }

    fun reset(position: ReadingTimePosition? = null, nowMillis: Long = elapsedRealtime()) {
        state = ReadingTimeState()
        lastEstimateSeconds = null
        lastEstimateMode = ReadingTimeEstimateMode.CHAPTER
        transitionFromSeconds = null
        transitionStep = 0
        if (isActive && position != null) {
            anchorPosition = position
            anchorElapsedMillis = nowMillis
        } else {
            anchorPosition = null
        }
    }

    private fun updateSpeed(
        previous: ReadingTimePosition,
        current: ReadingTimePosition,
        elapsedMillis: Long,
    ) {
        val elapsedSeconds = elapsedMillis / 1_000.0
        val chapterDelta = current.chapterCoordinate - previous.chapterCoordinate
        if (chapterDelta > 0.0) {
            state.chapterSecondsPerUnit = updateEwma(
                state.chapterSecondsPerUnit,
                elapsedSeconds / chapterDelta,
            )
            state.sampleCount++
            state.validReadingMillis += elapsedMillis
        }

        val previousContent = indexSnapshot.contentCoordinate(previous)
        val currentContent = indexSnapshot.contentCoordinate(current)
        if (previousContent != null && currentContent != null &&
            !indexSnapshot.hasUnknownContentBetween(previous.chapterIndex, current.chapterIndex)
        ) {
            val contentDelta = currentContent - previousContent
            if (contentDelta > 0.0) {
                state.contentSecondsPerByte = updateEwma(
                    state.contentSecondsPerByte,
                    elapsedSeconds / contentDelta,
                )
                state.contentSampleCount++
                state.contentValidReadingMillis += elapsedMillis
            }
        }
    }

    private fun estimate(
        position: ReadingTimePosition,
        advanceTransition: Boolean,
    ): ReadingTimeEstimate {
        if (indexSnapshot.chapterCount == 0) {
            return ReadingTimeEstimate.Unavailable
        }
        if (isFinished(position)) {
            lastEstimateSeconds = 0.0
            val mode = effectiveMode(indexSnapshot)
            lastEstimateMode = mode
            return ReadingTimeEstimate.Ready(0.0, mode)
        }
        if (!state.isChapterQualified()) {
            return ReadingTimeEstimate.Learning
        }
        val mode = effectiveMode(indexSnapshot)
        if (mode != lastEstimateMode && lastEstimateSeconds != null && transitionFromSeconds == null) {
            transitionFromSeconds = lastEstimateSeconds
            transitionStep = 0
        }
        val targetSeconds = when (mode) {
            ReadingTimeEstimateMode.CHAPTER -> {
                state.chapterSecondsPerUnit * indexSnapshot.remainingChapterUnits(position)
            }

            ReadingTimeEstimateMode.HYBRID_CONTENT,
            ReadingTimeEstimateMode.FULL_CONTENT -> {
                val remainingBytes = indexSnapshot.remainingContentBytes(position)
                if (remainingBytes == null) {
                    state.chapterSecondsPerUnit * indexSnapshot.remainingChapterUnits(position)
                } else {
                    state.contentSecondsPerByte * remainingBytes
                }
            }
        }.coerceAtLeast(0.0)

        val from = transitionFromSeconds
        val displayedSeconds = if (from != null && advanceTransition) {
            transitionStep = min(TRANSITION_SAMPLES, transitionStep + 1)
            val weight = transitionStep.toDouble() / TRANSITION_SAMPLES
            (from * (1.0 - weight) + targetSeconds * weight).also {
                if (transitionStep >= TRANSITION_SAMPLES) {
                    transitionFromSeconds = null
                    transitionStep = 0
                    lastEstimateMode = mode
                }
            }
        } else if (from != null) {
            lastEstimateSeconds ?: from
        } else {
            targetSeconds
        }
        lastEstimateSeconds = displayedSeconds
        if (transitionFromSeconds == null) {
            lastEstimateMode = mode
        }
        return ReadingTimeEstimate.Ready(displayedSeconds, mode)
    }

    private fun effectiveMode(snapshot: ReadingTimeIndexSnapshot): ReadingTimeEstimateMode {
        return if (snapshot.mode != ReadingTimeEstimateMode.CHAPTER && state.isContentQualified()) {
            snapshot.mode
        } else {
            ReadingTimeEstimateMode.CHAPTER
        }
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

    private fun updateEwma(current: Double, sample: Double): Double {
        if (!sample.isFinite() || sample <= 0.0) return current
        if (current <= 0.0 || !current.isFinite()) return sample
        val clipped = sample.coerceIn(current * OUTLIER_MIN_FACTOR, current * OUTLIER_MAX_FACTOR)
        return current * (1.0 - EWMA_ALPHA) + clipped * EWMA_ALPHA
    }

    companion object {
        const val MIN_SAMPLE_MILLIS = 5_000L
        const val MAX_SAMPLE_MILLIS = 120_000L
        const val EWMA_ALPHA = 0.2
        const val OUTLIER_MIN_FACTOR = 0.25
        const val OUTLIER_MAX_FACTOR = 4.0
        const val TRANSITION_SAMPLES = 5
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
