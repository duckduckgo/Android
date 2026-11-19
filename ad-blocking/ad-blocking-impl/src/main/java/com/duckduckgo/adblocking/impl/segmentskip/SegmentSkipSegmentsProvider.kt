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

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.sha256
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logcat.LogPriority.WARN
import logcat.logcat
import javax.inject.Inject

data class SegmentSkipSegment(
    val start: Double,
    val end: Double,
    val category: String,
    val actionType: String,
    val videoDuration: Double? = null,
    val uuid: String,
)

interface SegmentSkipSegmentsProvider {
    /** Segments for [videoId], or an empty list when there are none or the lookup fails. */
    suspend fun getSegments(videoId: String): List<SegmentSkipSegment>
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealSegmentSkipSegmentsProvider @Inject constructor(
    private val service: SegmentSkipService,
    private val hashPrefixGate: SegmentSkipHashPrefixGate,
    private val settingsProvider: SegmentSkipSettingsProvider,
    private val dispatchers: DispatcherProvider,
    @AppCoroutineScope private val appScope: CoroutineScope,
) : SegmentSkipSegmentsProvider {

    private val mutex = Mutex()

    private val lookups = object : LinkedHashMap<String, Deferred<List<SegmentSkipSegment>?>>(CACHE_SIZE, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Deferred<List<SegmentSkipSegment>?>>): Boolean =
            size > CACHE_SIZE
    }

    override suspend fun getSegments(videoId: String): List<SegmentSkipSegment> {
        val segments = lookup(videoId).await() ?: return emptyList()
        val settings = settingsProvider.settings()
        return segments.filter { it.category in settings.categories && it.actionType in settings.actionTypes }
    }

    private suspend fun lookup(videoId: String): Deferred<List<SegmentSkipSegment>?> = mutex.withLock {
        lookups.getOrPut(videoId) {
            appScope.async(dispatchers.io()) {
                fetch(videoId).also { segments ->
                    if (segments == null) mutex.withLock { lookups.remove(videoId) }
                }
            }
        }
    }

    /** Null when the lookup failed, so the failure isn't cached. */
    private suspend fun fetch(videoId: String): List<SegmentSkipSegment>? {
        val hashPrefix = videoId.sha256.take(HASH_PREFIX_LENGTH)
        return try {
            if (!hashPrefixGate.hasSegments(hashPrefix)) return emptyList()
            val response = service.getSegments(hashPrefix)
            when {
                response.code() == HTTP_NOT_FOUND -> emptyList()
                response.isSuccessful -> response.body().orEmpty()
                    .firstOrNull { it.videoID == videoId }
                    ?.segments.orEmpty()
                    .mapNotNull { it.toSegmentOrNull() }
                else -> {
                    logcat(WARN) { "SegmentSkip: segments lookup failed: HTTP ${response.code()}" }
                    null
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(WARN) { "SegmentSkip: segments lookup failed: ${e.javaClass.simpleName}" }
            null
        }
    }

    private fun SegmentResponse.toSegmentOrNull(): SegmentSkipSegment? {
        val range = segment?.takeIf { it.size == 2 } ?: return null
        val start = range[0] ?: return null
        val end = range[1] ?: return null
        if (!start.isFinite() || !end.isFinite() || start < 0 || end <= start) return null
        if (category.isNullOrBlank() || actionType.isNullOrBlank() || uuid.isNullOrBlank()) return null
        return SegmentSkipSegment(
            start = start,
            end = end,
            category = category,
            actionType = actionType,
            videoDuration = videoDuration?.takeIf { it.isFinite() && it > 0 },
            uuid = uuid,
        )
    }

    private companion object {
        const val HASH_PREFIX_LENGTH = 4
        const val HTTP_NOT_FOUND = 404
        const val CACHE_SIZE = 50
        const val LOAD_FACTOR = 0.75f
    }
}
