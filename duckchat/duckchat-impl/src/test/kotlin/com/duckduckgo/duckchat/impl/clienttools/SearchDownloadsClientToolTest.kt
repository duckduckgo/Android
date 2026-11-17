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

package com.duckduckgo.duckchat.impl.clienttools

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.downloads.api.DownloadsRepository
import com.duckduckgo.downloads.api.model.DownloadItem
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.TimeZone

@RunWith(AndroidJUnit4::class)
class SearchDownloadsClientToolTest {

    private val feature = FakeFeatureToggleFactory.create(AiChatBrowserToolsFeature::class.java)
    private val downloads: DownloadsRepository = mock()
    private val testee = SearchDownloadsClientTool(feature, downloads)
    private val context = ClientToolCallContext(chatTabId = "chat", browserMode = BrowserMode.REGULAR, chatId = "chat-1")
    private val originalZone = TimeZone.getDefault()

    @Before
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Madrid"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalZone)
    }

    @Test
    fun whenParentAndToolFlagsEnabledThenToolIsEnabled() {
        feature.self().setRawStoredState(Toggle.State(enable = true))
        assertFalse(testee.isEnabled())

        feature.searchDownloads().setRawStoredState(Toggle.State(enable = true))
        assertTrue(testee.isEnabled())
    }

    @Test
    fun whenInputMatchesSchemaThenValid() {
        listOf(
            """{"query":"paris"}""",
            """{"query":"paris","limit":50}""",
            JSONObject().put("query", "🦆".repeat(1000)).toString(),
        ).forEach { assertTrue(it, testee.isValidInput(JSONObject(it))) }
    }

    @Test
    fun whenInputBreaksSchemaThenInvalidArguments() = runTest {
        listOf(
            """{}""",
            """{"query":""}""",
            JSONObject().put("query", "a".repeat(1001)).toString(),
            """{"query":"paris","limit":51}""",
            """{"query":"paris","limit":0}""",
            """{"query":"paris","limit":1.5}""",
            """{"query":"paris","startDate":"2026-10-01"}""",
        ).forEach { input ->
            assertFalse(input, testee.isValidInput(JSONObject(input)))
            assertEquals(input, ClientToolResult.Failure(ClientToolErrorCode.INVALID_ARGUMENTS), testee.execute(JSONObject(input), context))
        }
    }

    @Test
    fun whenFileNamesMatchIgnoringCaseThenTheyAreReturnedMostRecentFirstWithUtcTimestamps() = runTest {
        givenDownloads(
            download("Paris-guide.pdf", "2026-10-27T09:30:00"),
            download("notes.txt", "2026-10-28T09:30:00"),
            download("paris-map.png", "2026-10-29T01:15:30"),
        )

        val value = success(testee.execute(JSONObject("""{"query":"PARIS"}"""), context))

        assertEquals(
            """{"results":[""" +
                """{"fileName":"paris-map.png","url":"","downloadedAt":"2026-10-29T00:15:30Z"},""" +
                """{"fileName":"Paris-guide.pdf","url":"","downloadedAt":"2026-10-27T08:30:00Z"}""" +
                """]}""",
            value.toString(),
        )
    }

    @Test
    fun whenTimestampCrossesDaylightSavingThenOffsetOfThatDateIsUsed() = runTest {
        givenDownloads(download("summer.pdf", "2026-07-01T12:00:00"))

        val result = success(testee.execute(JSONObject("""{"query":"summer"}"""), context)).getJSONArray("results").getJSONObject(0)

        assertEquals("2026-07-01T10:00:00Z", result.getString("downloadedAt"))
    }

    @Test
    fun whenTimestampIsUnreadableThenThatDownloadIsLeftOut() = runTest {
        givenDownloads(download("a.pdf", "yesterday"), download("b.pdf", "2026-10-27T09:30:00"))

        val results = success(testee.execute(JSONObject("""{"query":"pdf"}"""), context)).getJSONArray("results")

        assertEquals(1, results.length())
        assertEquals("b.pdf", results.getJSONObject(0).getString("fileName"))
    }

    @Test
    fun whenLimitOmittedThenTenAreReturnedAndLimitCapsTheRest() = runTest {
        givenDownloads(*Array(60) { download("file$it.pdf", "2026-10-27T09:%02d:00".format(it)) })

        assertEquals(10, success(testee.execute(JSONObject("""{"query":"file"}"""), context)).getJSONArray("results").length())
        val fifty = success(testee.execute(JSONObject("""{"query":"file","limit":50}"""), context)).getJSONArray("results")
        assertEquals(50, fifty.length())
        assertEquals("file59.pdf", fifty.getJSONObject(0).getString("fileName"))
    }

    @Test
    fun whenFileNameTooLongThenCutToContractLimit() = runTest {
        givenDownloads(download("f".repeat(300), "2026-10-27T09:30:00"))

        val result = success(testee.execute(JSONObject("""{"query":"f"}"""), context)).getJSONArray("results").getJSONObject(0)

        assertEquals(256, result.getString("fileName").length)
    }

    private fun success(result: ClientToolResult): JSONObject = (result as ClientToolResult.Success).value

    private suspend fun givenDownloads(vararg items: DownloadItem) {
        whenever(downloads.getDownloads()).thenReturn(items.toList())
    }

    private fun download(
        fileName: String,
        createdAt: String,
    ) = DownloadItem(
        downloadId = 1L,
        downloadStatus = 1,
        fileName = fileName,
        contentLength = 1L,
        createdAt = createdAt,
        filePath = "/sdcard/Download/$fileName",
    )
}
