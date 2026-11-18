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
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

@RunWith(AndroidJUnit4::class)
class SegmentSkipBinaryStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val file = File(context.filesDir, "sponsor-prefixes.bin")
    private val store = SegmentSkipBinaryStore(context)

    @After fun clean() {
        file.delete()
        File(context.filesDir, "sponsor-next.tmp").delete()
    }

    @Test fun fullDiffAndInvalidUpdates() {
        clean()
        store.apply(0, full(1, listOf(0x0a130329, 0x0a13c480)).inputStream())
        assertTrue(store.contains("0a130329"))
        store.apply(1, diff(2, listOf(0xcb1ad2a7), listOf(0x0a13c480), listOf(0x0a130329, 0xcb1ad2a7)).inputStream())
        assertEquals(2L, store.revision())
        assertFalse(store.contains("0a13c480"))
        assertTrue(store.contains("cb1ad2a7"))
        val invalid = full(3, listOf(1)).apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        try {
            store.apply(2, invalid.inputStream())
            fail("Expected invalid checksum")
        } catch (_: InvalidSegmentSkipFile) { }
        assertEquals(2L, store.revision())
        assertTrue(store.contains("cb1ad2a7"))
        val badDiff = diff(3, listOf(5), listOf(5), listOf(0x0a130329, 0xcb1ad2a7))
        try {
            store.apply(2, badDiff.inputStream())
            fail("Expected overlapping lists to fail")
        } catch (_: InvalidSegmentSkipFile) { }
        assertEquals(2L, store.revision())
    }

    @Test fun acceptsUnsignedRevisionsAcrossSignedIntBoundary() {
        clean()
        store.apply(0, full(0x80000000L, listOf(1)).inputStream())
        assertEquals(0x80000000L, store.revision())
        store.apply(0x80000000L, diff(0xffffffffL, listOf(2), emptyList(), listOf(1, 2)).inputStream())
        assertEquals(0xffffffffL, SegmentSkipBinaryStore(context).revision())
        assertTrue(store.contains("00000002"))
    }

    @Test fun rejectsInvalidHeadersOffsetsAndVersions() {
        clean()
        val valid = full(1, listOf(1, 2))
        val malformed = listOf(
            valid.copyOf(20),
            valid.copyOf().apply { this[0] = 0 },
            valid.copyOf().apply { this[6] = 1 },
            valid.copyOf().apply { this[28] = 1 },
            valid.copyOf().apply {
                this[lastIndex] = this[lastIndex - 2]
                this[lastIndex - 1] = this[lastIndex - 3]
            },
        )
        for (response in malformed) {
            try {
                store.apply(0, response.inputStream())
                fail("Expected invalid file")
            } catch (_: InvalidSegmentSkipFile) { }
            assertEquals(0L, store.revision())
        }
        try {
            store.apply(0, valid.copyOf().apply { this[4] = 2 }.inputStream())
            fail("Expected unsupported version")
        } catch (_: UnsupportedSegmentSkipFormat) { }
        assertEquals(0L, store.revision())
    }

    @Test fun interruptedDownloadKeepsSavedFileAcrossRestart() {
        clean()
        store.apply(0, full(1, listOf(1)).inputStream())
        val interrupted = object : InputStream() {
            private var reads = 0
            override fun read(): Int = if (reads++ < 10) 0 else throw IOException("interrupted")
        }
        try {
            store.apply(1, interrupted)
            fail("Expected interrupted download")
        } catch (_: IOException) { }
        assertEquals(1L, store.revision())
        File(context.filesDir, "sponsor-next.tmp").writeBytes(full(2, listOf(2)))
        val restarted = SegmentSkipBinaryStore(context)
        assertEquals(1L, restarted.revision())
        assertTrue(restarted.contains("00000001"))
        assertFalse(File(context.filesDir, "sponsor-download.tmp").exists())
    }

    @Test fun lookupsUseOldFileUntilPublication() {
        clean()
        store.apply(0, full(1, listOf(1)).inputStream())
        val atPublish = CountDownLatch(1)
        val resume = CountDownLatch(1)
        store.beforePublish = {
            atPublish.countDown()
            check(resume.await(5, TimeUnit.SECONDS))
        }
        var failure: Throwable? = null
        val writer = thread {
            try {
                store.apply(1, diff(2, listOf(2), listOf(1), listOf(2)).inputStream())
            } catch (e: Throwable) {
                failure = e
            }
        }
        try {
            assertTrue(atPublish.await(5, TimeUnit.SECONDS))
            assertEquals(1L, store.revision())
            assertTrue(store.contains("00000001"))
            assertFalse(store.contains("00000002"))
            assertTrue(SegmentSkipBinaryStore(context).contains("00000001"))
        } finally {
            resume.countDown()
            writer.join(5000)
        }
        assertFalse(writer.isAlive)
        assertNull(failure)
        assertEquals(2L, store.revision())
        assertFalse(store.contains("00000001"))
        assertTrue(store.contains("00000002"))
        assertTrue(SegmentSkipBinaryStore(context).contains("00000002"))
    }

    @Test fun lookupsContinueWhileUpdateIsBeingDownloaded() {
        clean()
        store.apply(0, full(1, listOf(1)).inputStream())
        val started = CountDownLatch(1)
        val resume = CountDownLatch(1)
        val input = object : ByteArrayInputStream(full(2, listOf(2))) {
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                started.countDown()
                check(resume.await(5, TimeUnit.SECONDS))
                return super.read(b, off, len)
            }
        }
        val writer = thread { store.apply(1, input) }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        try {
            assertTrue(store.contains("00000001"))
            assertEquals(1L, store.revision())
        } finally {
            resume.countDown()
            writer.join(5000)
        }
        assertEquals(2L, store.revision())
        assertTrue(store.contains("00000002"))
    }
}

internal fun full(revision: Long, values: List<Long>): ByteArray {
    val sorted = values.sorted()
    val body = ByteBuffer.allocate(65537 * 4 + sorted.size * 2).order(ByteOrder.LITTLE_ENDIAN)
    for (bucket in 0..65536) body.putInt(sorted.count { (it ushr 16) < bucket })
    sorted.forEach { body.putShort(it.toShort()) }
    val checksum = MessageDigest.getInstance("SHA-256").digest(body.array()).copyOf(4)
    return header(0, revision, checksum, sorted.size, 0, 0) + body.array()
}

internal fun diff(revision: Long, inserts: List<Long>, deletes: List<Long>, result: List<Long>): ByteArray {
    val checksum = full(revision, result).copyOfRange(12, 16)
    val body = ByteBuffer.allocate(4 * (inserts.size + deletes.size)).order(ByteOrder.LITTLE_ENDIAN)
    (inserts + deletes).forEach { body.putInt(it.toInt()) }
    return header(1, revision, checksum, result.size, inserts.size, deletes.size) + body.array()
}

private fun header(type: Byte, revision: Long, checksum: ByteArray, count: Int, inserts: Int, deletes: Int): ByteArray =
    ByteBuffer.allocate(28).order(ByteOrder.LITTLE_ENDIAN).put("SBPL".toByteArray()).put(1).put(type).putShort(0)
        .putInt(revision.toInt()).put(checksum).putInt(count).putInt(inserts).putInt(deletes).array()
