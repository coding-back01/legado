package io.legado.app.model.read

import android.content.Context
import io.legado.app.R

data class ReadingTimeDisplaySnapshot(
    val accumulated: String,
    val remaining: String,
    val combined: String,
)

object ReadingTimeDisplayFormatter {

    fun format(
        context: Context,
        readRecordEnabled: Boolean,
        accumulatedReadMillis: Long,
        estimate: ReadingTimeEstimate,
    ): ReadingTimeDisplaySnapshot {
        val unavailable = context.getString(R.string.reading_time_unavailable)
        if (!readRecordEnabled) {
            return unavailableSnapshot(unavailable)
        }
        val accumulated = formatAccumulated(context, accumulatedReadMillis)
        val remaining = when (estimate) {
            ReadingTimeEstimate.Unavailable -> unavailable
            ReadingTimeEstimate.Learning -> context.getString(R.string.reading_time_learning)
            is ReadingTimeEstimate.Ready -> formatRemaining(context, estimate.remainingSeconds)
        }
        return ReadingTimeDisplaySnapshot(
            accumulated = accumulated,
            remaining = remaining,
            combined = context.getString(R.string.reading_time_combined, accumulated, remaining),
        )
    }

    internal fun unavailableSnapshot(unavailable: String): ReadingTimeDisplaySnapshot {
        return ReadingTimeDisplaySnapshot(unavailable, unavailable, unavailable)
    }

    private fun formatAccumulated(context: Context, readMillis: Long): String {
        val parts = ReadingTimeDuration.splitMinutes(
            ReadingTimeDuration.accumulatedMinutes(readMillis)
        )
        return if (parts.hours > 0L) {
            context.getString(
                R.string.reading_time_read_hours_minutes,
                parts.hours,
                parts.minutes,
            )
        } else {
            context.getString(R.string.reading_time_read_minutes, parts.minutes)
        }
    }

    private fun formatRemaining(context: Context, remainingSeconds: Double): String {
        val parts = ReadingTimeDuration.splitMinutes(
            ReadingTimeDuration.remainingMinutes(remainingSeconds)
        )
        return if (parts.hours > 0L) {
            context.getString(
                R.string.reading_time_remaining_hours_minutes,
                parts.hours,
                parts.minutes,
            )
        } else {
            context.getString(R.string.reading_time_remaining_minutes, parts.minutes)
        }
    }
}
