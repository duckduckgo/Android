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

import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Streaming
import retrofit2.http.Query as HttpQuery

interface SegmentSkipDatasetService {
    @GET("https://protection.duckduckgo.com/api/protection/segments/v1/android" + "/revision")
    suspend fun revision(): SegmentSkipRevisionResponse

    @GET("https://protection.duckduckgo.com/api/protection/segments/v1/android" + "/hashPrefix")
    @Streaming
    fun binaryHashPrefixes(@HttpQuery("revision") revision: Long): Call<ResponseBody>
}

data class SegmentSkipRevisionResponse(val revision: Long?)

@BindingContainer
@ContributesTo(AppScope::class)
object SegmentSkipDatasetModule {
    @Provides
    @SingleIn(AppScope::class)
    @android.annotation.SuppressLint("NoRetrofitCreateMethodCallDetector")
    fun service(@javax.inject.Named("api") retrofit: retrofit2.Retrofit): SegmentSkipDatasetService =
        retrofit.create(SegmentSkipDatasetService::class.java)
}
