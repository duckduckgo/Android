/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.httpsupgrade.api

import com.duckduckgo.app.httpsupgrade.model.HttpsBloomFilterSpec
import com.duckduckgo.app.httpsupgrade.model.HttpsWhitelistedDomain
import io.reactivex.Observable
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface HttpsUpgradeService {

    @GET("https://staticcdn.duckduckgo.com/https/https-mobile-whitelist.json?cache_version=1")
    fun whitelist(): Call<List<HttpsWhitelistedDomain>>

    @GET("https://staticcdn.duckduckgo.com/https/https-mobile-bloom-spec.json?cache_version=1")
    fun httpsBloomFilterSpec(): Observable<HttpsBloomFilterSpec>

    @GET("https://staticcdn.duckduckgo.com/https/https-mobile-bloom.bin?cache_version=1")
    fun httpsBloomFilter(): Call<ResponseBody>

    @GET("https://duckduckgo.com/smarter_encryption.js")
    fun upgradeListForPartialHost(@Query("pv1") partialSha1: String): Call<List<String>>
}
