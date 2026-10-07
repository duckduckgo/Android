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
package com.duckduckgo.app.anr.ndk

import android.content.Context
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.data.store.api.FakeSharedPreferencesProvider
import com.duckduckgo.feature.toggles.api.Toggle
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

class MinidumpUploaderTest {

    @get:Rule
    val filesDir = TemporaryFolder()

    private val server = MockWebServer()
    private val toggle: Toggle = mock()
    private val feature: NativeCrashFeature = mock<NativeCrashFeature>().also {
        whenever(it.uploadMinidumps()).thenReturn(toggle)
    }
    private val context: Context = mock()
    private val timeProvider: CurrentTimeProvider = mock()
    private val prefsProvider = FakeSharedPreferencesProvider()
    private lateinit var pendingDir: File
    private lateinit var uploader: MinidumpUploader

    @Before
    fun setup() {
        server.start()
        pendingDir = filesDir.root.resolve("crashpad/pending").apply { mkdirs() }
        whenever(context.filesDir).thenReturn(filesDir.root)
        whenever(toggle.isEnabled()).thenReturn(true)
        whenever(toggle.getSettings()).thenReturn("""{"uploadUrl":"${server.url("/crash.js")}"}""")
        whenever(timeProvider.currentTimeMillis()).thenReturn(NOW)
        uploader = MinidumpUploader(context, feature, OkHttpClient(), timeProvider, prefsProvider)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `upload url empty when toggle disabled even if settings has url`() {
        whenever(toggle.isEnabled()).thenReturn(false)
        assertEquals("", uploader.uploadUrl())
    }

    @Test
    fun `upload url empty when enabled but settings missing or malformed`() {
        whenever(toggle.getSettings()).thenReturn(null)
        assertEquals("", uploader.uploadUrl())
        whenever(toggle.getSettings()).thenReturn("not json")
        assertEquals("", uploader.uploadUrl())
    }

    @Test
    fun `pending minidump posted as multipart with metadata and deleted`() {
        val dump = writeDump("abc")
        val meta = pendingDir.resolve("abc.meta").apply { writeText("meta") }
        server.enqueue(MockResponse().setResponseCode(200))

        uploader.uploadPending("""{"ExceptionType":"AndroidNativeCrash"}""")

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/crash.js", request.path)
        assertTrue(request.getHeader("Content-Type")!!.startsWith("multipart/form-data"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("name=\"upload_file_minidump\"; filename=\"abc.dmp\""))
        assertTrue(body.contains("name=\"crash_metadata\""))
        assertTrue(body.contains("AndroidNativeCrash"))
        assertFalse(dump.exists())
        assertFalse(meta.exists())
    }

    @Test
    fun `minidump deleted even when server rejects upload`() {
        val dump = writeDump("abc")
        server.enqueue(MockResponse().setResponseCode(500))

        uploader.uploadPending("{}")

        assertFalse(dump.exists())
    }

    @Test
    fun `nothing uploaded when toggle disabled`() {
        whenever(toggle.isEnabled()).thenReturn(false)
        val dump = writeDump("abc")

        uploader.uploadPending("{}")

        assertEquals(0, server.requestCount)
        assertTrue(dump.exists())
    }

    @Test
    fun `only oldest minidump uploaded per pass and the rest discarded`() {
        val dumps = (1..5).map { writeDump("d$it").apply { setLastModified(1_000_000L * it) } }
        server.enqueue(MockResponse().setResponseCode(200))

        uploader.uploadPending("{}")

        assertEquals(1, server.requestCount)
        assertTrue(server.takeRequest().body.readUtf8().contains("filename=\"d1.dmp\""))
        assertTrue(dumps.none { it.exists() })
    }

    @Test
    fun `nothing uploaded within an hour of the last attempt and pending minidumps discarded`() {
        server.enqueue(MockResponse().setResponseCode(500))
        writeDump("first")
        uploader.uploadPending("{}")
        assertEquals(1, server.requestCount)

        whenever(timeProvider.currentTimeMillis()).thenReturn(NOW + ONE_HOUR - 1)
        val second = writeDump("second")
        uploader.uploadPending("{}")

        assertEquals(1, server.requestCount)
        assertFalse(second.exists())
    }

    @Test
    fun `upload resumes once an hour has passed since the last attempt`() {
        server.enqueue(MockResponse().setResponseCode(200))
        writeDump("first")
        uploader.uploadPending("{}")

        whenever(timeProvider.currentTimeMillis()).thenReturn(NOW + ONE_HOUR)
        server.enqueue(MockResponse().setResponseCode(200))
        writeDump("second")
        uploader.uploadPending("{}")

        assertEquals(2, server.requestCount)
    }

    @Test
    fun `last attempt time in the future does not throttle`() {
        prefsProvider.getSharedPreferences("com.duckduckgo.app.anr.minidump.upload").edit().putLong("last_upload_attempt", NOW + ONE_HOUR).apply()
        server.enqueue(MockResponse().setResponseCode(200))
        writeDump("abc")

        uploader.uploadPending("{}")

        assertEquals(1, server.requestCount)
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
        const val ONE_HOUR = 60 * 60 * 1000L
    }

    private fun writeDump(uuid: String): File = pendingDir.resolve("$uuid.dmp").apply { writeBytes(byteArrayOf(1, 2, 3)) }
}
