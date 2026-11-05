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

import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class RealSponsorBlockSegmentsProviderTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val service = FakeSponsorBlockService()
    private val gate = FakeHashPrefixGate()
    private val settingsProvider = FakeSettingsProvider()

    private val provider = RealSponsorBlockSegmentsProvider(
        service = service,
        hashPrefixGate = gate,
        settingsProvider = settingsProvider,
        dispatchers = coroutineRule.testDispatcherProvider,
        appScope = coroutineRule.testScope,
    )

    @Test
    fun whenLookingUpThenOnlyTheFourCharacterHashPrefixIsSent() = runTest {
        provider.getSegments(VIDEO_ID)

        assertEquals(listOf(VIDEO_ID_HASH_PREFIX), service.requestedPrefixes)
        assertEquals(listOf(VIDEO_ID_HASH_PREFIX), gate.checkedPrefixes)
    }

    @Test
    fun whenResponseHasOtherVideosThenOnlyRequestedVideoSegmentsAreReturned() = runTest {
        service.response = Response.success(
            listOf(
                video(OTHER_VIDEO_ID, segment(1.0, 2.0)),
                video(VIDEO_ID, segment(10.0, 20.0, videoDuration = 300.0)),
            ),
        )

        assertEquals(
            listOf(SponsorBlockSegment(start = 10.0, end = 20.0, category = "sponsor", actionType = "skip", videoDuration = 300.0)),
            provider.getSegments(VIDEO_ID),
        )
    }

    @Test
    fun whenSegmentsHaveDisallowedCategoryOrActionTypeThenTheyAreDropped() = runTest {
        settingsProvider.settings = SponsorBlockSettings(categories = listOf("sponsor", "intro"), actionTypes = listOf("skip"))
        service.response = Response.success(
            listOf(
                video(
                    VIDEO_ID,
                    segment(1.0, 2.0, category = "sponsor"),
                    segment(3.0, 4.0, category = "intro"),
                    segment(5.0, 6.0, category = "selfpromo"),
                    segment(7.0, 8.0, actionType = "mute"),
                ),
            ),
        )

        assertEquals(listOf(1.0, 3.0), provider.getSegments(VIDEO_ID).map { it.start })
    }

    @Test
    fun whenSegmentsAreInvalidThenTheyAreDropped() = runTest {
        service.response = Response.success(
            listOf(
                video(
                    VIDEO_ID,
                    SegmentResponse(segment = null, category = "sponsor", actionType = "skip", videoDuration = null),
                    SegmentResponse(segment = listOf(1.0), category = "sponsor", actionType = "skip", videoDuration = null),
                    SegmentResponse(segment = listOf(1.0, 2.0, 3.0), category = "sponsor", actionType = "skip", videoDuration = null),
                    SegmentResponse(segment = listOf(1.0, null), category = "sponsor", actionType = "skip", videoDuration = null),
                    segment(-1.0, 2.0),
                    segment(5.0, 5.0),
                    segment(6.0, 4.0),
                    segment(1.0, Double.POSITIVE_INFINITY),
                    segment(Double.NaN, 2.0),
                    SegmentResponse(segment = listOf(1.0, 2.0), category = null, actionType = "skip", videoDuration = null),
                    SegmentResponse(segment = listOf(1.0, 2.0), category = "sponsor", actionType = null, videoDuration = null),
                    segment(30.0, 40.0, videoDuration = 0.0),
                ),
            ),
        )

        assertEquals(
            listOf(SponsorBlockSegment(start = 30.0, end = 40.0, category = "sponsor", actionType = "skip", videoDuration = null)),
            provider.getSegments(VIDEO_ID),
        )
    }

    @Test
    fun whenServiceReturnsNotFoundThenEmptyListIsReturnedAndCached() = runTest {
        service.response = Response.error(404, "".toResponseBody())

        assertTrue(provider.getSegments(VIDEO_ID).isEmpty())
        provider.getSegments(VIDEO_ID)

        assertEquals(1, service.requestedPrefixes.size)
    }

    @Test
    fun whenServiceFailsThenEmptyListIsReturnedAndNotCached() = runTest {
        service.response = Response.error(410, "".toResponseBody())
        assertTrue(provider.getSegments(VIDEO_ID).isEmpty())

        service.error = IOException()
        assertTrue(provider.getSegments(VIDEO_ID).isEmpty())

        service.error = null
        service.response = Response.success(listOf(video(VIDEO_ID, segment(1.0, 2.0))))
        assertEquals(1, provider.getSegments(VIDEO_ID).size)
        assertEquals(3, service.requestedPrefixes.size)
    }

    @Test
    fun whenGateMissesThenNetworkIsNotCalled() = runTest {
        gate.hit = false

        assertTrue(provider.getSegments(VIDEO_ID).isEmpty())
        assertTrue(service.requestedPrefixes.isEmpty())
    }

    @Test
    fun whenVideoWasLookedUpThenCacheIsUsedAndCurrentSettingsApply() = runTest {
        service.response = Response.success(
            listOf(video(VIDEO_ID, segment(1.0, 2.0, category = "sponsor"), segment(3.0, 4.0, category = "intro"))),
        )
        assertEquals(1, provider.getSegments(VIDEO_ID).size)

        settingsProvider.settings = SponsorBlockSettings(categories = listOf("sponsor", "intro"))

        assertEquals(2, provider.getSegments(VIDEO_ID).size)
        assertEquals(1, service.requestedPrefixes.size)
    }

    @Test
    fun whenCacheIsFullThenLeastRecentlyUsedVideoIsEvicted() = runTest {
        val videoIds = (0..50).map { "video${it.toString().padStart(6, '0')}" }
        videoIds.forEach { provider.getSegments(it) }
        assertEquals(51, service.requestedPrefixes.size)

        provider.getSegments(videoIds.last())
        assertEquals(51, service.requestedPrefixes.size)

        provider.getSegments(videoIds.first())
        assertEquals(52, service.requestedPrefixes.size)
    }

    @Test
    fun whenSameVideoIsRequestedConcurrentlyThenOneRequestIsMade() = runTest {
        val release = CompletableDeferred<Unit>()
        service.gate = release
        service.response = Response.success(listOf(video(VIDEO_ID, segment(1.0, 2.0))))

        val first = async { provider.getSegments(VIDEO_ID) }
        val second = async { provider.getSegments(VIDEO_ID) }
        coroutineRule.testScope.testScheduler.runCurrent()
        release.complete(Unit)

        assertEquals(first.await(), second.await())
        assertEquals(1, first.await().size)
        assertEquals(1, service.requestedPrefixes.size)
    }

    private fun video(videoId: String, vararg segments: SegmentResponse) = VideoSegmentsResponse(videoID = videoId, segments = segments.toList())

    private fun segment(
        start: Double,
        end: Double,
        category: String = "sponsor",
        actionType: String = "skip",
        videoDuration: Double? = null,
    ) = SegmentResponse(segment = listOf(start, end), category = category, actionType = actionType, videoDuration = videoDuration)

    private class FakeSponsorBlockService : SponsorBlockService {
        val requestedPrefixes = mutableListOf<String>()
        var response: Response<List<VideoSegmentsResponse>> = Response.success(emptyList())
        var error: Exception? = null
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun getSegments(hashPrefix: String): Response<List<VideoSegmentsResponse>> {
            requestedPrefixes += hashPrefix
            gate?.await()
            error?.let { throw it }
            return response
        }
    }

    private class FakeHashPrefixGate : SponsorBlockHashPrefixGate {
        val checkedPrefixes = mutableListOf<String>()
        var hit = true

        override suspend fun hasSegments(hashPrefix: String): Boolean {
            checkedPrefixes += hashPrefix
            return hit
        }
    }

    private class FakeSettingsProvider : SponsorBlockSettingsProvider {
        var settings = SponsorBlockSettings()

        override suspend fun settings(): SponsorBlockSettings = settings
    }

    private companion object {
        const val VIDEO_ID = "dQw4w9WgXcQ"
        const val VIDEO_ID_HASH_PREFIX = "5f6b"
        const val OTHER_VIDEO_ID = "aQp-iM0bCs4"
    }
}
