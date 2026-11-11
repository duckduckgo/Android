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
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import javax.inject.Named

interface SponsorBlockService {
    @GET("$SEGMENTS_ENDPOINT/{hashPrefix}")
    suspend fun getSegments(@Path("hashPrefix") hashPrefix: String): Response<List<VideoSegmentsResponse>>

    companion object {
        private const val SEGMENTS_ENDPOINT = "https://duckduckgo.com/sponsorblock/v1/segments"
    }
}

// Every field is nullable because the response is untrusted and Moshi's reflective adapter doesn't enforce Kotlin nullability.
data class VideoSegmentsResponse(
    val videoID: String?,
    val segments: List<SegmentResponse>?,
)

data class SegmentResponse(
    val segment: List<Double?>?,
    val category: String?,
    val actionType: String?,
    val videoDuration: Double?,
)

@BindingContainer
@ContributesTo(AppScope::class)
class SponsorBlockModule {
    @Provides
    @SingleIn(AppScope::class)
    @SuppressLint("NoRetrofitCreateMethodCallDetector")
    fun provideSponsorBlockService(@Named("api") retrofit: Retrofit): SponsorBlockService {
        return retrofit.create(SponsorBlockService::class.java)
    }
}
