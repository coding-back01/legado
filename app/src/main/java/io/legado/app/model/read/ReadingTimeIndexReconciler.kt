package io.legado.app.model.read

data class ReadingTimeTocEntry(
    val identity: String,
    val directRawLength: Int = ReadingTimeIndexSnapshot.UNKNOWN_LENGTH,
)

data class ReadingTimeIndexReconcileResult(
    val rawLengths: IntArray,
    val visibleLengths: IntArray,
    val tocPrefixHash: Long,
    val resetSpeedModel: Boolean,
)

object ReadingTimeIndexReconciler {

    fun shouldResetSpeedState(
        state: ReadingTimeState?,
        bookIdentityHash: Long,
        sourceLastModified: Long,
        entries: List<ReadingTimeTocEntry>,
    ): Boolean {
        state ?: return false
        if (state.bookIdentityHash == 0L) return false
        return state.bookIdentityHash != bookIdentityHash ||
                state.sourceLastModified != sourceLastModified ||
                state.tocChapterCount > entries.size ||
                tocHash(entries, state.tocChapterCount) != state.tocPrefixHash
    }

    fun reconcile(
        stored: ReadingTimeIndexData?,
        bookIdentityHash: Long,
        sourceLastModified: Long,
        entries: List<ReadingTimeTocEntry>,
    ): ReadingTimeIndexReconcileResult {
        val fullHash = tocHash(entries)
        val directLengths = IntArray(entries.size) { entries[it].directRawLength }
        val unknownVisibleLengths = IntArray(entries.size) {
            if (entries[it].directRawLength == ReadingTimeIndexSnapshot.VOLUME_LENGTH) {
                ReadingTimeIndexSnapshot.VOLUME_LENGTH
            } else {
                ReadingTimeIndexSnapshot.UNKNOWN_LENGTH
            }
        }
        if (stored == null) {
            return ReadingTimeIndexReconcileResult(
                directLengths,
                unknownVisibleLengths,
                fullHash,
                false,
            )
        }
        if (stored.bookIdentityHash != bookIdentityHash ||
            stored.sourceLastModified != sourceLastModified ||
            stored.rawLengths.size > entries.size
        ) {
            return ReadingTimeIndexReconcileResult(
                directLengths,
                unknownVisibleLengths,
                fullHash,
                true,
            )
        }
        val storedPrefixHash = tocHash(entries, stored.rawLengths.size)
        if (storedPrefixHash != stored.tocPrefixHash) {
            return ReadingTimeIndexReconcileResult(
                directLengths,
                unknownVisibleLengths,
                fullHash,
                true,
            )
        }
        val merged = directLengths.copyOf()
        val mergedVisible = unknownVisibleLengths.copyOf()
        stored.rawLengths.copyInto(merged, endIndex = stored.rawLengths.size)
        stored.visibleLengths.copyInto(mergedVisible, endIndex = stored.visibleLengths.size)
        entries.forEachIndexed { index, entry ->
            if (entry.directRawLength >= 0) {
                merged[index] = entry.directRawLength
            }
        }
        return ReadingTimeIndexReconcileResult(merged, mergedVisible, fullHash, false)
    }

    fun tocHash(entries: List<ReadingTimeTocEntry>, count: Int = entries.size): Long {
        var result = ReadingTimeIdentity.initialHash()
        val safeCount = count.coerceIn(0, entries.size)
        for (index in 0 until safeCount) {
            result = ReadingTimeIdentity.extend(result, entries[index].identity)
        }
        return result
    }
}
