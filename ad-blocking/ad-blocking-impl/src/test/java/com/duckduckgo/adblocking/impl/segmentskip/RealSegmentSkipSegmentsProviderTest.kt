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

import com.duckduckgo.common.test.CoroutineTestRule
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import retrofit2.Response
import retrofit2.http.GET
import java.io.IOException

class RealSegmentSkipSegmentsProviderTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val service = FakeSegmentSkipService()
    private val settingsProvider = FakeSettingsProvider()

    private val provider = RealSegmentSkipSegmentsProvider(
        service = service,
        settingsProvider = settingsProvider,
        dispatchers = coroutineRule.testDispatcherProvider,
        appScope = coroutineRule.testScope,
    )

    @Test
    fun whenUsingMockDatasetThenSegmentsStillUseProductionEndpoint() {
        val method = SegmentSkipService::class.java.declaredMethods.single { it.name == "getSegments" }
        assertEquals("https://duckduckgo.com/sponsorblock/v1/segments/{hashPrefix}", method.getAnnotation(GET::class.java)?.value)
    }

    @Test
    fun whenLookingUpThenOnlyTheFourCharacterHashPrefixIsSent() = runTest {
        provider.getSegments(VIDEO_ID, VIDEO_ID_HASH_PREFIX)

        assertEquals(listOf(VIDEO_ID_HASH_PREFIX), service.requestedPrefixes)
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
            listOf(
                SegmentSkipSegment(start = 10.0, end = 20.0, category = "sponsor", actionType = "skip", videoDuration = 300.0, uuid = "uuid-10.0"),
            ),
            provider.getSegments(VIDEO_ID, VIDEO_ID_HASH_PREFIX),
        )
    }

    @Test
    fun whenResponseIsParsedThenSegmentUuidIsRead() = runTest {
        val type = Types.newParameterizedType(List::class.java, VideoSegmentsResponse::class.java)
        val json = """[{"videoID":"$VIDEO_ID","segments":[{"segment":[10.0,20.0],"category":"sponsor","actionType":"skip","UUID":"abc"}]}]"""
        service.response = Response.success(Moshi.Builder().build().adapter<List<VideoSegmentsResponse>>(type).fromJson(json))

        assertEquals(listOf("abc"), provider.getSegments(VIDEO_ID, VIDEO_ID_HASH_PREFIX).map { it.uuid })
    }

    @Test
    fun whenSegmentsHaveDisallowedCategoryOrActionTypeThenTheyAreDropped() = runTest {
        settingsProvider.settings = SegmentSkipSettings(categories = listOf("sponsor", "intro"), actionTypes = listOf("skip"))
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

        assertEquals(listOf(1.0, 3.0), provider.getSegments(VIDEO_ID, VIDEO_ID_HASH_PREFIX).map { it.start })
    }

    @Test
    fun whenSegmentsAreInvalidThenTheyAreDropped() = runTest {
        service.response = Response.success(
            listOf(
                video(
                    VIDEO_ID,
                    SegmentResponse(segment = null, category = "sponsor", actionType = "skip", videoDuration = null, uuid = "uuid"),
                    SegmentResponse(segment = listOf(1.0), category = "sponsor", actionType = "skip", videoDuration = null, uuid = "uuid"),
                    SegmentResponse(segment = listOf(1.0, 2.0, 3.0), category = "sponsor", actionType = "skip", videoDuration = null, uuid = "uuid"),
                    SegmentResponse(segment = listOf(1.0, null), category = "sponsor", actionType = "skip", videoDuration = null, uuid = "uuid"),
                    segment(-1.0, 2.0),
                    segment(5.0, 5.0),
                    segment(6.0, 4.0),
                    segment(1.0, Double.POSITIVE_INFINITY),
                    segment(Double.NaN, 2.0),
                    SegmentResponse(segment = listOf(1.0, 2.0), category = null, actionType = "skip", videoDuration = null, uuid = "uuid"),
                    SegmentResponse(segment = listOf(1.0, 2.0), category = "sponsor", actionType = null, videoDuration = null, uuid = "uuid"),
                    segment(7.0, 8.0, uuid = null),
                    segment(9.0, 10.0, uuid = ""),
                    segment(30.0, 40.0, videoDuration = 0.0),
                ),
            ),
        )

        assertEquals(
            listOf(
                SegmentSkipSegment(start = 30.0, end = 40.0, category = "sponsor", actionType = "skip", videoDuration = null, uuid = "uuid-30.0"),
            ),
            provider.getSegments(VIDEO_ID, VIDEO_ID_HASH_PREFIX),
        )
    }

    @Test
    fun whenServiceReturnsNotFoundThenEmptyListIsReturnedAndCached() = runTest {
        service.response = Response.error(404, "".toResponseBody())

        assertTrue(provider.getSegments(VIDEO_ID, VIDEO_ID_HASH_PREFIX).isEmpty())
        provider.getSegments(VIDEO_ID, VIDEO_ID_HASH_PREFIX)

        assertEquals(1, service.requestedPrefixes.size)
    }

    @Test
    fun whenServiceFailsThenEmptyListIsReturnedAndNotCached() = runTest {
        service.response = Response.error(410, "".toResponseBody())
        assertTrue(provider.getSegments(VIDEO_ID, VIDEO_ID_HASH_PREFIX).isEmpty())

        service.error = IOException()
        assertTrue(provider.getSegments(VIDEO_ID, VIDEO_ID_HASH_PREFIX).isEmpty())

        service.error = null
        service.response = Response.success(listOf(video(VIDEO_ID, segment(1.0, 2.0))))
        assertEquals(1, provider.getSegments(VIDEO_ID, VIDEO_ID_HASH_PREFIX).size)
        assertEquals(3, service.requestedPrefixes.size)
    }

    @Test
    fun whenVideoWasLookedUpThenCacheIsUsedAndCurrentSettingsApply() = runTest {
        service.response = Response.success(
            listOf(video(VIDEO_ID, segment(1.0, 2.0, category = "sponsor"), segment(3.0, 4.0, category = "intro"))),
        )
        assertEquals(1, provider.getSegments(VIDEO_ID, VIDEO_ID_HASH_PREFIX).size)

        settingsProvider.settings = SegmentSkipSettings(categories = listOf("sponsor", "intro"))

        assertEquals(2, provider.getSegments(VIDEO_ID, VIDEO_ID_HASH_PREFIX).size)
        assertEquals(1, service.requestedPrefixes.size)
    }

    @Test
    fun whenCacheIsClearedThenNextLookupFetchesAgain() = runTest {
        service.response = Response.success(listOf(video(VIDEO_ID, segment(1.0, 2.0))))
        provider.getSegments(VIDEO_ID, VIDEO_ID_HASH_PREFIX)
        provider.getSegments(VIDEO_ID, VIDEO_ID_HASH_PREFIX)
        assertEquals(1, service.requestedPrefixes.size)
        provider.clearCache()
        provider.getSegments(VIDEO_ID, VIDEO_ID_HASH_PREFIX)
        assertEquals(2, service.requestedPrefixes.size)
    }

    @Test
    fun whenOldLookupFailsAfterClearThenItDoesNotEvictNewEntry() = runTest {
        val release = CompletableDeferred<Unit>()
        service.gate = release
        val old = async { provider.getSegments(VIDEO_ID, VIDEO_ID_HASH_PREFIX) }
        coroutineRule.testScope.testScheduler.runCurrent()
        provider.clearCache()
        service.gate = null
        service.response = Response.success(listOf(video(VIDEO_ID, segment(1.0, 2.0))))
        assertEquals(1, provider.getSegments(VIDEO_ID, VIDEO_ID_HASH_PREFIX).size)
        service.error = IOException()
        release.complete(Unit)
        assertTrue(old.await().isEmpty())
        service.error = null
        assertEquals(1, provider.getSegments(VIDEO_ID, VIDEO_ID_HASH_PREFIX).size)
        assertEquals(2, service.requestedPrefixes.size)
    }

    @Test
    fun whenCacheIsFullThenLeastRecentlyUsedVideoIsEvicted() = runTest {
        val videoIds = (0..50).map { "video${it.toString().padStart(6, '0')}" }
        videoIds.forEach { provider.getSegments(it, VIDEO_ID_HASH_PREFIX) }
        assertEquals(51, service.requestedPrefixes.size)

        provider.getSegments(videoIds.last(), VIDEO_ID_HASH_PREFIX)
        assertEquals(51, service.requestedPrefixes.size)

        provider.getSegments(videoIds.first(), VIDEO_ID_HASH_PREFIX)
        assertEquals(52, service.requestedPrefixes.size)
    }

    @Test
    fun whenSameVideoIsRequestedConcurrentlyThenOneRequestIsMade() = runTest {
        val release = CompletableDeferred<Unit>()
        service.gate = release
        service.response = Response.success(listOf(video(VIDEO_ID, segment(1.0, 2.0))))

        val first = async { provider.getSegments(VIDEO_ID, VIDEO_ID_HASH_PREFIX) }
        val second = async { provider.getSegments(VIDEO_ID, VIDEO_ID_HASH_PREFIX) }
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
        uuid: String? = "uuid-$start",
    ) = SegmentResponse(segment = listOf(start, end), category = category, actionType = actionType, videoDuration = videoDuration, uuid = uuid)

    private class FakeSegmentSkipService : SegmentSkipService {
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

    private class FakeSettingsProvider : SegmentSkipSettingsProvider {
        var settings = SegmentSkipSettings()

        override suspend fun settings(): SegmentSkipSettings = settings
    }

    private companion object {
        const val VIDEO_ID = "dQw4w9WgXcQ"
        const val VIDEO_ID_HASH_PREFIX = "5f6b"
        const val OTHER_VIDEO_ID = "aQp-iM0bCs4"
    }
}
