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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.adblocking.impl.duckplayer.DuckPlayerFeatureRepository
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessaging
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class SegmentSkipContentScopeJsMessageHandlerTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val duckPlayerFeatureRepository: DuckPlayerFeatureRepository = mock()
    private val jsMessaging: JsMessaging = mock()
    private val segmentSkip = FakeSegmentSkip()

    private val handler = SegmentSkipContentScopeJsMessageHandler(
        segmentSkip = segmentSkip,
        duckPlayerFeatureRepository = duckPlayerFeatureRepository,
        dispatcherProvider = coroutineRule.testDispatcherProvider,
        appScope = coroutineRule.testScope,
    ).getJsMessageHandler()

    @Before
    fun setup() {
        whenever(duckPlayerFeatureRepository.getYouTubeEmbedUrl()).thenReturn("youtube-nocookie.com")
    }

    @Test
    fun allowedDomainsAreYouTubeAndTheDuckPlayerEmbedHost() {
        assertEquals(listOf("youtube.com", "m.youtube.com", "youtube-nocookie.com"), handler.allowedDomains)
    }

    @Test
    fun whenGetConfigThenReturnsConfigAndVersion() {
        val response = send("getConfig")

        assertTrue(response.getBoolean("enabled"))
        assertEquals("manual", response.getString("skipMode"))
        assertEquals(1, response.getInt("version"))
    }

    @Test
    fun whenNotEnabledThenGetConfigReturnsNotEnabled() {
        segmentSkip.config = segmentSkip.config.copy(enabled = false)

        assertFalse(send("getConfig").getBoolean("enabled"))
    }

    @Test
    fun whenGetSegmentsThenPassesVideoIdAndSerializesSegments() {
        segmentSkip.segments = listOf(
            SegmentSkipSegment(start = 5.0, end = 15.0, category = "sponsor", actionType = "skip", uuid = "uuid-1"),
            SegmentSkipSegment(start = 60.0, end = 70.0, category = "sponsor", actionType = "skip", videoDuration = 300.5, uuid = "uuid-2"),
        )

        val segments = send("getSegments", JSONObject().put("videoID", VIDEO_ID)).getJSONArray("segments")

        assertEquals(VIDEO_ID, segmentSkip.requestedIds.single())
        assertEquals(2, segments.length())
        with(segments.getJSONObject(0)) {
            assertEquals(5.0, getDouble("start"), 0.0)
            assertEquals(15.0, getDouble("end"), 0.0)
            assertEquals("sponsor", getString("category"))
            assertEquals("skip", getString("actionType"))
            assertEquals("uuid-1", getString("uuid"))
            assertFalse(has("videoDuration"))
        }
        assertEquals(300.5, segments.getJSONObject(1).getDouble("videoDuration"), 0.0)
    }

    @Test
    fun whenGetSegmentsWithoutVideoIdThenPassesEmptyId() {
        assertEquals(0, send("getSegments").getJSONArray("segments").length())
        assertEquals("", segmentSkip.requestedIds.single())
    }

    @Test
    fun whenMessageHasNoIdThenNoResponseIsSent() {
        handler.process(JsMessage("contentScopeScripts", "segmentSkip", "getConfig", JSONObject(), null), jsMessaging, null)

        verify(jsMessaging, never()).onResponse(any())
    }

    private fun send(method: String, params: JSONObject = JSONObject()): JSONObject {
        handler.process(JsMessage("contentScopeScripts", "segmentSkip", method, params, "id"), jsMessaging, null)
        val captor = argumentCaptor<JsCallbackData>()
        verify(jsMessaging, atLeastOnce()).onResponse(captor.capture())
        return captor.lastValue.also {
            assertEquals("segmentSkip", it.featureName)
            assertEquals(method, it.method)
            assertEquals("id", it.id)
        }.params
    }

    private class FakeSegmentSkip : SegmentSkip {
        var config = SegmentSkipConfig(enabled = true, skipMode = SegmentSkipSkipMode.MANUAL)
        var segments: List<SegmentSkipSegment> = emptyList()
        val requestedIds = mutableListOf<String>()

        override fun config(): SegmentSkipConfig = config

        override suspend fun getSegments(videoId: String): List<SegmentSkipSegment> {
            requestedIds += videoId
            return segments
        }

        override fun modeFlow(): Flow<SegmentSkipMode> = throw UnsupportedOperationException()

        override suspend fun setMode(mode: SegmentSkipMode) = throw UnsupportedOperationException()
    }

    private companion object {
        const val VIDEO_ID = "dQw4w9WgXcQ"
    }
}
