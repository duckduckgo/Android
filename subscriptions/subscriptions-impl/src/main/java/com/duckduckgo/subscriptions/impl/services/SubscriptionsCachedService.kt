/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.subscriptions.impl.services

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface SubscriptionsCachedService {
    @Deprecated("Use featuresV2 instead")
    @GET("https://subscriptions.duckduckgo.com/api/products/{sku}/features")
    suspend fun features(@Path("sku") sku: String): FeaturesResponse

    @GET("https://subscriptions.duckduckgo.com/api/v2/features")
    suspend fun featuresV2(@Query("sku") sku: String): FeaturesV2Response
}

data class FeaturesV2Response(
    val features: Map<String, List<TierFeatureResponse>>,
)

data class TierFeatureResponse(
    val product: String,
    val name: String, // e.g. "Plus", "Pro" (Tier)
)
