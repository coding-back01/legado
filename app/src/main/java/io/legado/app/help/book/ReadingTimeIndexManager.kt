package io.legado.app.help.book

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.model.localBook.LocalBook
import io.legado.app.model.read.ReadingTimeIdentity
import io.legado.app.model.read.ReadingTimeIndexCodec
import io.legado.app.model.read.ReadingTimeIndexData
import io.legado.app.model.read.ReadingTimeIndexReconciler
import io.legado.app.model.read.ReadingTimeIndexSnapshot
import io.legado.app.model.read.ReadingTimeState
import io.legado.app.model.read.ReadingTimeTocEntry
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.min

data class ReadingTimeIndexUpdate(
    val snapshot: ReadingTimeIndexSnapshot,
    val resetSpeedModel: Boolean,
    val bookIdentityHash: Long,
    val tocChapterCount: Int,
    val tocPrefixHash: Long,
    val sourceLastModified: Long,
)

object ReadingTimeIndexManager {

    const val INDEX_FILE_NAME = "reading_time_index.bin"
    private const val SCAN_BATCH_SIZE = 64
    private const val SCAN_SLICE_NANOS = 8_000_000L
    private const val SCAN_YIELD_MILLIS = 50L
    private const val SNAPSHOT_DEBOUNCE_MILLIS = 500L
    private const val WRITE_DEBOUNCE_MILLIS = 30_000L

    private val executor by lazy {
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "reading-time-index").apply {
                priority = Thread.MIN_PRIORITY
                isDaemon = true
            }
        }
    }
    private var generation = 0L
    private var session: Session? = null

    fun start(
        book: Book,
        chapters: List<BookChapter>,
        speedState: ReadingTimeState?,
        onUpdate: (ReadingTimeIndexUpdate) -> Unit,
    ) {
        val request = StartRequest.create(book, chapters, speedState, onUpdate)
        val requestGeneration: Long
        val oldSession: Session?
        synchronized(this) {
            generation++
            requestGeneration = generation
            oldSession = session
            session = null
        }
        flush(oldSession)
        executor.execute { initialize(requestGeneration, request) }
    }

    fun stop() {
        val oldSession: Session?
        synchronized(this) {
            generation++
            oldSession = session
            session = null
        }
        flush(oldSession)
    }

    fun onContentSaved(book: Book, chapter: BookChapter, rawBytes: Long) {
        updateChapter(book, chapter, rawBytes.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt())
    }

    fun onContentDeleted(book: Book, chapter: BookChapter) {
        updateChapter(book, chapter, ReadingTimeIndexSnapshot.UNKNOWN_LENGTH)
    }

    fun onBookCacheCleared(book: Book) {
        synchronized(this) {
            val current = session ?: return
            if (!current.matches(book)) return
            current.chapterMetadata.forEachIndexed { index, metadata ->
                current.rawLengths[index] = metadata?.directRawLength
                    ?.takeIf { it >= 0 } ?: ReadingTimeIndexSnapshot.UNKNOWN_LENGTH
            }
            current.dirty = false
            current.writeFuture?.cancel(false)
            scheduleSnapshotLocked(current)
        }
    }

    fun onAllCachesCleared() {
        synchronized(this) {
            val current = session ?: return
            current.chapterMetadata.forEachIndexed { index, metadata ->
                current.rawLengths[index] = metadata?.directRawLength
                    ?.takeIf { it >= 0 } ?: ReadingTimeIndexSnapshot.UNKNOWN_LENGTH
            }
            current.dirty = false
            current.writeFuture?.cancel(false)
            scheduleSnapshotLocked(current)
        }
    }

    fun flushActive() {
        val data = synchronized(this) { session?.toWriteRequest() }
        if (data != null) {
            executor.execute { ReadingTimeIndexCodec.write(data.file, data.data) }
        }
    }

    private fun initialize(requestGeneration: Long, request: StartRequest) {
        if (!isCurrentGeneration(requestGeneration)) return
        val storedFile = File(request.cacheDirectory, INDEX_FILE_NAME)
        val stored = ReadingTimeIndexCodec.read(storedFile)
        if (stored == null && storedFile.exists()) storedFile.delete()
        val reconcile = ReadingTimeIndexReconciler.reconcile(
            stored = stored,
            bookIdentityHash = request.bookIdentityHash,
            sourceLastModified = request.sourceLastModified,
            entries = request.tocEntries,
        )
        val resetStoredSpeed = ReadingTimeIndexReconciler.shouldResetSpeedState(
            state = request.speedState,
            bookIdentityHash = request.bookIdentityHash,
            sourceLastModified = request.sourceLastModified,
            entries = request.tocEntries,
        )
        val newSession = Session(
            generation = requestGeneration,
            bookUrl = request.bookUrl,
            cacheDirectory = request.cacheDirectory,
            bookIdentityHash = request.bookIdentityHash,
            sourceLastModified = request.sourceLastModified,
            tocPrefixHash = reconcile.tocPrefixHash,
            chapterMetadata = request.chapterMetadata,
            rawLengths = reconcile.rawLengths,
            onUpdate = request.onUpdate,
        )
        synchronized(this) {
            if (generation != requestGeneration) return
            session = newSession
        }
        publish(newSession, reconcile.resetSpeedModel || resetStoredSpeed)
        scanBatch(requestGeneration, 0, false)
    }

    private fun scanBatch(requestGeneration: Long, startIndex: Int, previouslyChanged: Boolean) {
        val current = synchronized(this) {
            session?.takeIf { it.generation == requestGeneration }
        } ?: return
        val startedAt = System.nanoTime()
        var index = startIndex
        var changed = previouslyChanged
        while (index < current.rawLengths.size && index - startIndex < SCAN_BATCH_SIZE) {
            if (!isCurrentGeneration(requestGeneration)) return
            val metadata = current.chapterMetadata[index]
            if (metadata != null && current.rawLengths[index] < 0) {
                val file = File(current.cacheDirectory, metadata.fileName)
                if (file.isFile && file.length() > 0L) {
                    synchronized(this) {
                        val active = session
                        if (active?.generation != requestGeneration) return
                        active.rawLengths[index] = file.length()
                            .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                        active.dirty = true
                    }
                    changed = true
                }
            }
            index++
            if (System.nanoTime() - startedAt >= SCAN_SLICE_NANOS) break
        }
        if (index < current.rawLengths.size) {
            executor.schedule(
                { scanBatch(requestGeneration, index, changed) },
                SCAN_YIELD_MILLIS,
                TimeUnit.MILLISECONDS,
            )
        } else if (changed) {
            publishCurrent(requestGeneration, false)
            synchronized(this) {
                session?.takeIf { it.generation == requestGeneration }?.let(::scheduleWriteLocked)
            }
        }
    }

    private fun updateChapter(book: Book, chapter: BookChapter, rawLength: Int) {
        synchronized(this) {
            val current = session ?: return
            if (!current.matches(book) || chapter.index !in current.rawLengths.indices) {
                return
            }
            val normalizedLength = if (chapter.isVolume) {
                ReadingTimeIndexSnapshot.VOLUME_LENGTH
            } else {
                rawLength
            }
            if (current.rawLengths[chapter.index] == normalizedLength) return
            current.rawLengths[chapter.index] = normalizedLength
            current.dirty = true
            scheduleSnapshotLocked(current)
            scheduleWriteLocked(current)
        }
    }

    private fun scheduleSnapshotLocked(current: Session) {
        current.snapshotFuture?.cancel(false)
        current.snapshotFuture = executor.schedule(
            { publishCurrent(current.generation, false) },
            SNAPSHOT_DEBOUNCE_MILLIS,
            TimeUnit.MILLISECONDS,
        )
    }

    private fun scheduleWriteLocked(current: Session) {
        current.writeFuture?.cancel(false)
        current.writeFuture = executor.schedule(
            { writeCurrent(current.generation) },
            WRITE_DEBOUNCE_MILLIS,
            TimeUnit.MILLISECONDS,
        )
    }

    private fun publishCurrent(requestGeneration: Long, resetSpeedModel: Boolean) {
        val current = synchronized(this) {
            session?.takeIf { it.generation == requestGeneration }
        } ?: return
        publish(current, resetSpeedModel)
    }

    private fun publish(current: Session, resetSpeedModel: Boolean) {
        val lengths = synchronized(this) {
            if (session?.generation != current.generation) return
            current.rawLengths.copyOf()
        }
        current.onUpdate(
            ReadingTimeIndexUpdate(
                snapshot = ReadingTimeIndexSnapshot.create(
                    rawLengths = lengths,
                    bookIdentityHash = current.bookIdentityHash,
                    tocPrefixHash = current.tocPrefixHash,
                ),
                resetSpeedModel = resetSpeedModel,
                bookIdentityHash = current.bookIdentityHash,
                tocChapterCount = lengths.size,
                tocPrefixHash = current.tocPrefixHash,
                sourceLastModified = current.sourceLastModified,
            )
        )
    }

    private fun writeCurrent(requestGeneration: Long) {
        val request = synchronized(this) {
            session?.takeIf { it.generation == requestGeneration }?.toWriteRequest()
        } ?: return
        if (ReadingTimeIndexCodec.write(request.file, request.data)) {
            synchronized(this) {
                session?.takeIf { it.generation == requestGeneration }?.dirty = false
            }
        }
    }

    private fun flush(oldSession: Session?) {
        if (oldSession == null || !oldSession.dirty) return
        oldSession.snapshotFuture?.cancel(false)
        oldSession.writeFuture?.cancel(false)
        val request = oldSession.toWriteRequest()
        executor.execute { ReadingTimeIndexCodec.write(request.file, request.data) }
    }

    private fun isCurrentGeneration(requestGeneration: Long): Boolean {
        return synchronized(this) { generation == requestGeneration }
    }

    private data class ChapterMetadata(
        val fileName: String,
        val directRawLength: Int,
    )

    private data class StartRequest(
        val bookUrl: String,
        val cacheDirectory: File,
        val bookIdentityHash: Long,
        val sourceLastModified: Long,
        val tocEntries: List<ReadingTimeTocEntry>,
        val chapterMetadata: Array<ChapterMetadata?>,
        val speedState: ReadingTimeState?,
        val onUpdate: (ReadingTimeIndexUpdate) -> Unit,
    ) {
        companion object {
            fun create(
                book: Book,
                chapters: List<BookChapter>,
                speedState: ReadingTimeState?,
                onUpdate: (ReadingTimeIndexUpdate) -> Unit,
            ): StartRequest {
                val chapterCount = maxOf(
                    chapters.size,
                    chapters.maxOfOrNull { it.index + 1 } ?: 0,
                )
                val metadata = arrayOfNulls<ChapterMetadata>(chapterCount)
                val entries = MutableList(chapterCount) { index ->
                    ReadingTimeTocEntry("$index|missing")
                }
                chapters.forEach { chapter ->
                    if (chapter.index !in 0 until chapterCount) return@forEach
                    val directLength = when {
                        chapter.isVolume -> ReadingTimeIndexSnapshot.VOLUME_LENGTH
                        book.isLocalTxt -> {
                            val start = chapter.start
                            val end = chapter.end
                            if (start != null && end != null && end > start) {
                                (end - start).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                            } else {
                                ReadingTimeIndexSnapshot.UNKNOWN_LENGTH
                            }
                        }

                        else -> ReadingTimeIndexSnapshot.UNKNOWN_LENGTH
                    }
                    val fileName = chapter.getFileName()
                    metadata[chapter.index] = ChapterMetadata(fileName, directLength)
                    entries[chapter.index] = ReadingTimeTocEntry(
                        identity = "${chapter.index}|${chapter.url}|${chapter.title}|${chapter.isVolume}",
                        directRawLength = directLength,
                    )
                }
                val sourceLastModified = if (book.isLocal) {
                    LocalBook.getLastModified(book).getOrDefault(0L)
                } else {
                    0L
                }
                return StartRequest(
                    bookUrl = book.bookUrl,
                    cacheDirectory = BookHelp.getBookCacheDirectory(book),
                    bookIdentityHash = ReadingTimeIdentity.hash(listOf(book.bookUrl, book.origin)),
                    sourceLastModified = sourceLastModified,
                    tocEntries = entries,
                    chapterMetadata = metadata,
                    speedState = speedState?.copy(),
                    onUpdate = onUpdate,
                )
            }
        }
    }

    private data class Session(
        val generation: Long,
        val bookUrl: String,
        val cacheDirectory: File,
        val bookIdentityHash: Long,
        val sourceLastModified: Long,
        val tocPrefixHash: Long,
        val chapterMetadata: Array<ChapterMetadata?>,
        val rawLengths: IntArray,
        val onUpdate: (ReadingTimeIndexUpdate) -> Unit,
        var dirty: Boolean = false,
        var snapshotFuture: ScheduledFuture<*>? = null,
        var writeFuture: ScheduledFuture<*>? = null,
    ) {
        fun matches(book: Book): Boolean {
            return bookUrl == book.bookUrl &&
                    bookIdentityHash == ReadingTimeIdentity.hash(listOf(book.bookUrl, book.origin))
        }

        fun toWriteRequest(): WriteRequest {
            return WriteRequest(
                file = File(cacheDirectory, INDEX_FILE_NAME),
                data = ReadingTimeIndexData(
                    bookIdentityHash = bookIdentityHash,
                    tocPrefixHash = tocPrefixHash,
                    sourceLastModified = sourceLastModified,
                    rawLengths = rawLengths.copyOf(),
                ),
            )
        }
    }

    private data class WriteRequest(
        val file: File,
        val data: ReadingTimeIndexData,
    )
}
