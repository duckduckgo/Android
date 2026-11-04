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

package com.duckduckgo.adblocking.impl.sponsorblock

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.adblocking.impl.duckplayer.DuckPlayerFeatureRepository
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessaging
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

@SuppressLint("DenyListedApi") // setRawStoredState
@RunWith(AndroidJUnit4::class)
class SponsorBlockContentScopeJsMessageHandlerTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val feature = FakeFeatureToggleFactory.create(SponsorBlockFeature::class.java)
    private val duckPlayerFeatureRepository: DuckPlayerFeatureRepository = mock()
    private val jsMessaging: JsMessaging = mock()
    private val segmentsProvider = FakeSegmentsProvider()

    private val handler = SponsorBlockContentScopeJsMessageHandler(
        sponsorBlockFeature = feature,
        segmentsProvider = segmentsProvider,
        duckPlayerFeatureRepository = duckPlayerFeatureRepository,
        dispatcherProvider = coroutineRule.testDispatcherProvider,
        appScope = coroutineRule.testScope,
    ).getJsMessageHandler()

    @Before
    fun setup() {
        whenever(duckPlayerFeatureRepository.getYouTubeEmbedUrl()).thenReturn("youtube-nocookie.com")
        setEnabled(true)
    }

    @Test
    fun allowedDomainsAreYouTubeAndTheDuckPlayerEmbedHost() {
        assertEquals(listOf("youtube.com", "m.youtube.com", "youtube-nocookie.com"), handler.allowedDomains)
    }

    @Test
    fun whenGetConfigThenReturnsEnabledManualModeAndVersion() {
        val response = send("getConfig")

        assertTrue(response.getBoolean("enabled"))
        assertEquals("manual", response.getString("skipMode"))
        assertEquals(1, response.getInt("version"))
    }

    @Test
    fun whenFeatureDisabledThenGetConfigReturnsNotEnabled() {
        setEnabled(false)

        assertFalse(send("getConfig").getBoolean("enabled"))
    }

    @Test
    fun whenGetSegmentsWithValidIdThenReturnsSegmentsSortedByStart() {
        segmentsProvider.segments = listOf(
            SponsorBlockSegment(start = 60.0, end = 70.0, category = "sponsor", actionType = "skip", videoDuration = 300.5),
            SponsorBlockSegment(start = 5.0, end = 15.0, category = "sponsor", actionType = "skip"),
        )

        val segments = send("getSegments", JSONObject().put("videoID", VALID_ID)).getJSONArray("segments")

        assertEquals(VALID_ID, segmentsProvider.requestedIds.single())
        assertEquals(2, segments.length())
        with(segments.getJSONObject(0)) {
            assertEquals(5.0, getDouble("start"), 0.0)
            assertEquals(15.0, getDouble("end"), 0.0)
            assertEquals("sponsor", getString("category"))
            assertEquals("skip", getString("actionType"))
            assertFalse(has("videoDuration"))
        }
        assertEquals(300.5, segments.getJSONObject(1).getDouble("videoDuration"), 0.0)
    }

    @Test
    fun whenGetSegmentsWithMalformedIdThenReturnsEmptyWithoutLookup() {
        listOf("", "short", "dQw4w9WgXcQX", "dQw4w9WgXc!", "../../etc/pa").forEach { id ->
            val segments = send("getSegments", JSONObject().put("videoID", id)).getJSONArray("segments")
            assertEquals(0, segments.length())
        }
        assertTrue(segmentsProvider.requestedIds.isEmpty())
    }

    @Test
    fun whenGetSegmentsWithoutVideoIdThenReturnsEmptyWithoutLookup() {
        assertEquals(0, send("getSegments").getJSONArray("segments").length())
        assertTrue(segmentsProvider.requestedIds.isEmpty())
    }

    @Test
    fun whenFeatureDisabledThenGetSegmentsReturnsEmptyWithoutLookup() {
        setEnabled(false)

        assertEquals(0, send("getSegments", JSONObject().put("videoID", VALID_ID)).getJSONArray("segments").length())
        assertTrue(segmentsProvider.requestedIds.isEmpty())
    }

    @Test
    fun whenProviderThrowsThenGetSegmentsReturnsEmpty() {
        segmentsProvider.error = RuntimeException("boom")

        assertEquals(0, send("getSegments", JSONObject().put("videoID", VALID_ID)).getJSONArray("segments").length())
    }

    @Test
    fun whenMessageHasNoIdThenNoResponseIsSent() {
        handler.process(JsMessage("contentScopeScripts", "sponsorBlock", "getConfig", JSONObject(), null), jsMessaging, null)

        verify(jsMessaging, never()).onResponse(any())
    }

    private fun send(method: String, params: JSONObject = JSONObject()): JSONObject {
        handler.process(JsMessage("contentScopeScripts", "sponsorBlock", method, params, "id"), jsMessaging, null)
        val captor = argumentCaptor<JsCallbackData>()
        verify(jsMessaging, atLeastOnce()).onResponse(captor.capture())
        return captor.lastValue.also {
            assertEquals("sponsorBlock", it.featureName)
            assertEquals(method, it.method)
            assertEquals("id", it.id)
        }.params
    }

    private fun setEnabled(enabled: Boolean) {
        feature.self().setRawStoredState(State(enable = true))
        feature.mvpEnabled().setRawStoredState(State(enable = enabled))
    }

    private class FakeSegmentsProvider : SponsorBlockSegmentsProvider {
        var segments: List<SponsorBlockSegment> = emptyList()
        var error: Exception? = null
        val requestedIds = mutableListOf<String>()

        override suspend fun getSegments(videoId: String): List<SponsorBlockSegment> {
            requestedIds += videoId
            error?.let { throw it }
            return segments
        }
    }

    private companion object {
        const val VALID_ID = "dQw4w9WgXcQ"
    }
}
