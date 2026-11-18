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

import com.duckduckgo.adblocking.impl.BuildConfig
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Streaming
import retrofit2.http.Query as HttpQuery

interface SponsorBlockDatasetService {
    @GET(BuildConfig.SPONSOR_BLOCK_BASE_URL + "/revision")
    suspend fun revision(): SponsorBlockRevisionResponse

    @GET(BuildConfig.SPONSOR_BLOCK_BASE_URL + "/hashPrefixBinary?format=binary&version=1")
    @Streaming
    suspend fun binaryHashPrefixes(@HttpQuery("revision") revision: Long): ResponseBody
}

data class SponsorBlockRevisionResponse(val revision: Long?)

@BindingContainer
@ContributesTo(AppScope::class)
object SponsorBlockDatasetModule {
    @Provides
    @SingleIn(AppScope::class)
    @android.annotation.SuppressLint("NoRetrofitCreateMethodCallDetector")
    fun service(@javax.inject.Named("api") retrofit: retrofit2.Retrofit): SponsorBlockDatasetService =
        retrofit.create(SponsorBlockDatasetService::class.java)
}
