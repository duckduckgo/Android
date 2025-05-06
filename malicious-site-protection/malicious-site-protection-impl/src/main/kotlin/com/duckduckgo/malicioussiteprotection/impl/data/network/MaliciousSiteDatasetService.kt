/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.malicioussiteprotection.impl.data.network

import com.duckduckgo.common.utils.AppUrl.Url.API
import retrofit2.http.GET
import retrofit2.http.Query

private const val BASE_URL = "$API/api/protection/v2/android"
private const val HASH_PREFIX_PATH = "/hashPrefix"
private const val FILTER_SET_PATH = "/filterSet"
private const val CATEGORY = "category"
private const val PHISHING = "phishing"
private const val MALWARE = "malware"
private const val SCAM = "scam"

interface MaliciousSiteDatasetService {
    @AuthRequired
    @GET("$BASE_URL$HASH_PREFIX_PATH?$CATEGORY=$PHISHING")
    suspend fun getPhishingHashPrefixes(@Query("revision") revision: Int): HashPrefixResponse

    @AuthRequired
    @GET("$BASE_URL$HASH_PREFIX_PATH?$CATEGORY=$MALWARE")
    suspend fun getMalwareHashPrefixes(@Query("revision") revision: Int): HashPrefixResponse

    @AuthRequired
    @GET("$BASE_URL$HASH_PREFIX_PATH?$CATEGORY=$SCAM")
    suspend fun getScamHashPrefixes(@Query("revision") revision: Int): HashPrefixResponse

    @AuthRequired
    @GET("$BASE_URL$FILTER_SET_PATH?$CATEGORY=$PHISHING")
    suspend fun getPhishingFilterSet(@Query("revision") revision: Int): FilterSetResponse

    @AuthRequired
    @GET("$BASE_URL$FILTER_SET_PATH?$CATEGORY=$MALWARE")
    suspend fun getMalwareFilterSet(@Query("revision") revision: Int): FilterSetResponse

    @AuthRequired
    @GET("$BASE_URL$FILTER_SET_PATH?$CATEGORY=$SCAM")
    suspend fun getScamFilterSet(@Query("revision") revision: Int): FilterSetResponse
}

data class HashPrefixResponse(
    val insert: Set<String>,
    val delete: Set<String>,
    val revision: Int,
    val replace: Boolean,
)

data class FilterSetResponse(
    val insert: Set<FilterResponse>,
    val delete: Set<FilterResponse>,
    val revision: Int,
    val replace: Boolean,
)

data class FilterResponse(
    val hash: String,
    val regex: String,
)
