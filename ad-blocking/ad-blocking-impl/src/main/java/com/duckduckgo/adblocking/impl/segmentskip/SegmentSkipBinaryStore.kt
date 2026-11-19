/*
 * Copyright (c) 2026 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.adblocking.impl.segmentskip

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.security.MessageDigest
import javax.inject.Inject

class UnsupportedSegmentSkipFormat : Exception()
class InvalidSegmentSkipFile : Exception()

@SingleIn(AppScope::class)
class SegmentSkipBinaryStore @Inject constructor(
    context: Context,
    private val dispatchers: DispatcherProvider,
) {
    private val file = File(context.filesDir, "sponsor-prefixes.bin")
    private val updateMutex = Mutex()

    @VisibleForTesting internal var beforePublish: (() -> Unit)? = null

    @VisibleForTesting internal var onUpdateCheckpoint: (() -> Unit)? = null

    @VisibleForTesting internal var onLoadCheckpoint: (() -> Unit)? = null

    @Volatile private var loaded: Loaded? = null

    suspend fun revision(): Long = currentSnapshot()?.revision ?: 0L

    suspend fun contains(prefix: String): Boolean {
        val data = currentSnapshot()?.data ?: return false
        val value = prefix.toLong(16)
        val bucket = (value ushr 16).toInt()
        val suffix = (value and 0xffff).toInt()
        var low = data.getInt(HEADER + bucket * 4)
        var high = data.getInt(HEADER + (bucket + 1) * 4) - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val candidate = data.getShort(REMAINDERS + mid * 2).toInt() and 0xffff
            if (candidate == suffix) return true
            if (candidate < suffix) low = mid + 1 else high = mid - 1
        }
        return false
    }

    suspend fun apply(
        expected: Long,
        body: InputStream,
        requireFull: Boolean = false,
        onPublished: () -> Unit = {},
    ) = updateMutex.withLock {
        val current = loadLocked()?.revision ?: 0L
        if (current > expected) return@withLock
        check(current == expected)
        val coroutineContext = currentCoroutineContext()
        val checkCancellation = {
            onUpdateCheckpoint?.invoke()
            coroutineContext.ensureActive()
        }
        val download = File(file.parentFile, "sponsor-download.tmp")
        val result = File(file.parentFile, "sponsor-next.tmp")
        val suffixes = File(file.parentFile, "sponsor-suffix.tmp")
        try {
            FileOutputStream(download).use { output ->
                val buffer = ByteArray(8192)
                while (true) {
                    checkCancellation()
                    val count = body.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
            }
            checkCancellation()
            val data = map(download)
            val header = readHeader(data)
            valid(header.revision > expected)
            valid(!requireFull || header.type == 0)
            if (header.type == 0) {
                val replacement = readFull(download, checkCancellation)
                FileOutputStream(download, true).use { it.fd.sync() }
                checkCancellation()
                publish(download, replacement, checkCancellation, onPublished)
            } else {
                val snapshot = requireNotNull(loaded).snapshot
                valid(expected != 0L && snapshot != null)
                merge(data, header, requireNotNull(snapshot), result, suffixes, checkCancellation)
                checkCancellation()
                publish(result, Snapshot(header.revision, header.count, map(result)), checkCancellation, onPublished)
            }
        } finally {
            download.delete()
            result.delete()
            suffixes.delete()
        }
    }

    private suspend fun currentSnapshot(): Snapshot? {
        loaded?.let { return it.snapshot }
        return updateMutex.withLock { loadLocked() }
    }

    private suspend fun loadLocked(): Snapshot? {
        loaded?.let { return it.snapshot }
        val snapshot = withContext(dispatchers.io()) {
            val coroutineContext = currentCoroutineContext()
            val checkCancellation = {
                onLoadCheckpoint?.invoke()
                coroutineContext.ensureActive()
            }
            try {
                checkCancellation()
                if (file.exists()) readFull(file, checkCancellation) else null
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
        loaded = Loaded(snapshot)
        return snapshot
    }

    private fun publish(next: File, replacement: Snapshot, checkCancellation: () -> Unit, onPublished: () -> Unit) {
        beforePublish?.invoke()
        checkCancellation()
        check(next.renameTo(file))
        loaded = Loaded(replacement)
        onPublished()
    }

    private fun merge(diff: ByteBuffer, header: Header, old: Snapshot, result: File, suffixes: File, checkCancellation: () -> Unit) {
        valid(diff.limit().toLong() == HEADER.toLong() + 4L * (header.inserts.toLong() + header.deletes))
        val inserts = PrefixList(diff, HEADER, header.inserts)
        val deletes = PrefixList(diff, HEADER + header.inserts * 4, header.deletes)
        val offsets = IntArray(BUCKETS + 1)
        var bucket = 0
        var count = 0
        var index = 0
        var oldBucket = 0
        var added = inserts.next()
        var removed = deletes.next()
        fun oldNext(): Long? {
            val data = old.data
            if (index >= old.count) return null
            while (index >= data.getInt(HEADER + (oldBucket + 1) * 4)) oldBucket++
            return (oldBucket.toLong() shl 16) or (data.getShort(REMAINDERS + index++ * 2).toLong() and 0xffff)
        }
        var existing = oldNext()
        BufferedOutputStream(FileOutputStream(suffixes)).use { output ->
            var processed = 0
            while (existing != null || added != null || removed != null) {
                if (processed++ % 4096 == 0) checkCancellation()
                val value = minOf(existing ?: Long.MAX_VALUE, added ?: Long.MAX_VALUE, removed ?: Long.MAX_VALUE)
                valid(added != value || removed != value)
                val wasAdded = added == value
                val wasRemoved = removed == value
                if (existing == value) existing = oldNext()
                if (wasAdded) added = inserts.next()
                if (wasRemoved) removed = deletes.next()
                if (!wasRemoved) {
                    val target = (value ushr 16).toInt()
                    while (bucket < target) offsets[++bucket] = count
                    output.write((value and 0xff).toInt())
                    output.write(((value ushr 8) and 0xff).toInt())
                    count++
                }
            }
        }
        while (bucket < BUCKETS) offsets[++bucket] = count
        valid(count == header.count)
        val digest = MessageDigest.getInstance("SHA-256")
        FileOutputStream(result).use { stream ->
            val output = BufferedOutputStream(stream)
            val bytes = ByteBuffer.allocate(HEADER).order(ByteOrder.LITTLE_ENDIAN)
                .put("SBPL".toByteArray(Charsets.US_ASCII)).put(1).put(0).putShort(0)
                .putInt(header.revision.toInt()).put(header.checksum).putInt(count).putInt(0).putInt(0).array()
            output.write(bytes)
            val table = ByteBuffer.allocate((BUCKETS + 1) * 4).order(ByteOrder.LITTLE_ENDIAN)
            offsets.forEach { table.putInt(it) }
            output.write(table.array())
            digest.update(table.array())
            suffixes.inputStream().buffered().use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    checkCancellation()
                    val n = input.read(buffer)
                    if (n < 0) break
                    output.write(buffer, 0, n)
                    digest.update(buffer, 0, n)
                }
            }
            valid(digest.digest().copyOf(4).contentEquals(header.checksum))
            output.flush()
            stream.fd.sync()
        }
    }

    private fun readFull(file: File, checkCancellation: () -> Unit = {}): Snapshot {
        val data = map(file)
        val header = readHeader(data)
        valid(header.type == 0 && header.inserts == 0 && header.deletes == 0)
        valid(data.limit().toLong() == REMAINDERS.toLong() + 2L * header.count)
        valid(data.getInt(HEADER) == 0 && data.getInt(HEADER + BUCKETS * 4) == header.count)
        var previous = 0
        for (bucket in 0 until BUCKETS) {
            if (bucket % 256 == 0) checkCancellation()
            val end = data.getInt(HEADER + (bucket + 1) * 4)
            valid(end >= previous && end <= header.count)
            var last = -1
            for (index in previous until end) {
                val value = data.getShort(REMAINDERS + index * 2).toInt() and 0xffff
                valid(value > last)
                last = value
            }
            previous = end
        }
        val digest = MessageDigest.getInstance("SHA-256")
        val content = data.asReadOnlyBuffer().apply { position(HEADER) }
        while (content.hasRemaining()) {
            checkCancellation()
            val end = content.position() + minOf(content.remaining(), 65536)
            val chunk = content.slice().apply { limit(end - content.position()) }
            digest.update(chunk)
            content.position(end)
        }
        valid(digest.digest().copyOf(4).contentEquals(header.checksum))
        return Snapshot(header.revision, header.count, data)
    }

    private fun readHeader(data: ByteBuffer): Header {
        valid(data.limit() >= HEADER)
        valid(
            data.get(0) == 'S'.code.toByte() && data.get(1) == 'B'.code.toByte() &&
                data.get(2) == 'P'.code.toByte() && data.get(3) == 'L'.code.toByte(),
        )
        if (data.get(4).toInt() != 1) throw UnsupportedSegmentSkipFormat()
        val type = data.get(5).toInt()
        valid(type in 0..1 && data.getShort(6).toInt() == 0)
        val revision = data.getInt(8).toLong() and 0xffffffffL
        val count = data.getInt(16).toLong() and 0xffffffffL
        val inserts = data.getInt(20).toLong() and 0xffffffffL
        val deletes = data.getInt(24).toLong() and 0xffffffffL
        valid(revision > 0 && count <= (Int.MAX_VALUE - REMAINDERS) / 2 && inserts + deletes <= (Int.MAX_VALUE - HEADER) / 4)
        return Header(type, revision, ByteArray(4) { data.get(12 + it) }, count.toInt(), inserts.toInt(), deletes.toInt())
    }

    private fun map(file: File): ByteBuffer {
        valid(file.length() in 1..Int.MAX_VALUE.toLong())
        return FileInputStream(file).use { it.channel.map(FileChannel.MapMode.READ_ONLY, 0, it.channel.size()).order(ByteOrder.LITTLE_ENDIAN) }
    }

    private fun valid(value: Boolean) { if (!value) throw InvalidSegmentSkipFile() }

    private class PrefixList(private val data: ByteBuffer, private val start: Int, private val count: Int) {
        private var index = 0
        private var previous = -1L
        fun next(): Long? {
            if (index == count) return null
            val value = data.getInt(start + index++ * 4).toLong() and 0xffffffffL
            if (value <= previous) throw InvalidSegmentSkipFile()
            previous = value
            return value
        }
    }

    private data class Header(val type: Int, val revision: Long, val checksum: ByteArray, val count: Int, val inserts: Int, val deletes: Int)
    private data class Loaded(val snapshot: Snapshot?)
    private data class Snapshot(val revision: Long, val count: Int, val data: ByteBuffer)
    private companion object {
        const val HEADER = 28
        const val BUCKETS = 65536
        const val REMAINDERS = HEADER + (BUCKETS + 1) * 4
    }
}
