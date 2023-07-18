/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.httpsupgrade.impl

import com.duckduckgo.anvil.annotations.ContributesServiceApi
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.httpsupgrade.store.HttpsBloomFilterSpec
import com.duckduckgo.httpsupgrade.store.HttpsFalsePositiveDomain
import io.reactivex.Observable
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.GET

@ContributesServiceApi(AppScope::class)
interface HttpsUpgradeService {

    @GET("https://staticcdn.duckduckgo.com/https/https-mobile-v2-bloom-spec.json")
    fun httpsBloomFilterSpec(): Observable<HttpsBloomFilterSpec>

    @GET("https://staticcdn.duckduckgo.com/https/https-mobile-v2-bloom.bin")
    fun httpsBloomFilter(): Call<ResponseBody>

    @GET("https://staticcdn.duckduckgo.com/https/https-mobile-v2-false-positives.json")
    fun falsePositives(): Call<List<HttpsFalsePositiveDomain>>
}
