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

package com.duckduckgo.app.browser.sponsorblock

import android.util.LruCache
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import logcat.LogPriority.WARN
import logcat.logcat
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Named

const val SPONSOR_BLOCK_LOG_TAG = "SponsorBlock"

data class SponsorSegment(
    val startMs: Long,
    val endMs: Long,
)

interface SponsorBlockRepository {
    /** Sponsor segments for [videoId], or an empty list when there are none or the lookup fails. */
    suspend fun segmentsFor(videoId: String): List<SponsorSegment>
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealSponsorBlockRepository @Inject constructor(
    @Named("api") apiOkHttpClient: OkHttpClient,
    private val dispatchers: DispatcherProvider,
) : SponsorBlockRepository {

    // The DI clients add DDG headers and interceptors meant for DDG hosts; none of that should reach a third party.
    private val okHttpClient = apiOkHttpClient.newBuilder()
        .apply {
            interceptors().clear()
            networkInterceptors().clear()
        }
        .cache(null)
        .build()

    private val cache = LruCache<String, List<SponsorSegment>>(CACHE_SIZE)

    override suspend fun segmentsFor(videoId: String): List<SponsorSegment> {
        cache[videoId]?.let {
            logcat(tag = SPONSOR_BLOCK_LOG_TAG) { "[api] cache hit for $videoId: ${it.size} segment(s) $it" }
            return it
        }
        return withContext(dispatchers.io()) {
            runCatching { fetch(videoId) }
                .onSuccess { logcat(tag = SPONSOR_BLOCK_LOG_TAG) { "[api] $videoId: ${it.size} segment(s) $it" } }
                .onFailure { logcat(WARN, tag = SPONSOR_BLOCK_LOG_TAG) { "[api] lookup failed for $videoId: ${it.message}" } }
                .getOrNull()
                ?.also { cache.put(videoId, it) }
                .orEmpty()
        }
    }

    private fun fetch(videoId: String): List<SponsorSegment> {
        // Only a short prefix of the ID's hash leaves the device, so the server can't tell which video is being watched.
        val request = Request.Builder().url("$BASE_URL/${hashPrefix(videoId)}").build()
        logcat(tag = SPONSOR_BLOCK_LOG_TAG) { "[api] GET ${request.url} for $videoId" }
        okHttpClient.newCall(request).execute().use { response ->
            logcat(tag = SPONSOR_BLOCK_LOG_TAG) { "[api] HTTP ${response.code} for $videoId" }
            if (response.code == HTTP_NOT_FOUND) return emptyList()
            check(response.isSuccessful) { "HTTP ${response.code}" }
            return parseSegments(response.body?.string().orEmpty(), videoId)
        }
    }

    companion object {
        private const val BASE_URL = "https://sponsor.ajay.app/api/skipSegments"
        private const val HASH_PREFIX_LENGTH = 4
        private const val HTTP_NOT_FOUND = 404
        private const val CACHE_SIZE = 50

        internal fun hashPrefix(videoId: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(videoId.toByteArray())
                .joinToString("") { "%02x".format(it) }
                .take(HASH_PREFIX_LENGTH)

        internal fun parseSegments(
            json: String,
            videoId: String,
        ): List<SponsorSegment> {
            val videos = JSONArray(json)
            for (i in 0 until videos.length()) {
                val video = videos.getJSONObject(i)
                if (video.optString("videoID") != videoId) continue
                val segments = video.optJSONArray("segments") ?: return emptyList()
                return (0 until segments.length())
                    .map { segments.getJSONObject(it) }
                    .filter { it.optString("actionType", "skip") == "skip" }
                    .mapNotNull { segment ->
                        val range = segment.optJSONArray("segment") ?: return@mapNotNull null
                        SponsorSegment(
                            startMs = (range.getDouble(0) * 1000).toLong(),
                            endMs = (range.getDouble(1) * 1000).toLong(),
                        )
                    }
                    .filter { it.endMs > it.startMs }
                    .sortedBy { it.startMs }
            }
            return emptyList()
        }
    }
}

object YouTubeVideoId {
    private val ID_PATTERN = Regex("^[A-Za-z0-9_-]{11}$")

    fun isValid(videoId: String): Boolean = ID_PATTERN.matches(videoId)
}
