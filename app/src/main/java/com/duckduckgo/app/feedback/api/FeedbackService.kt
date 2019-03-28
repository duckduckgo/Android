/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.feedback.api

import retrofit2.Call
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST


interface FeedbackService {

    @FormUrlEncoded
    @POST("/feedback.js?type=app-feedback")
    fun submitFeedback(
        @Field("reason") reason: String = REASON_GENERAL,
        @Field("rating") rating: String,
        @Field("category") category: String?,
        @Field("subcategory") subcategory: String?,
        @Field("comment") comment: String,
        @Field("url") url: String? = null,
        @Field("platform") platform: String = PLATFORM,
        @Field("v") version: String,
        @Field("os") api: Int,
        @Field("manufacturer") manufacturer: String,
        @Field("model") model: String,
        @Field("atb") atb: String
    ): Call<Void>

    @FormUrlEncoded
    @POST("/feedback.js?type=app-feedback")
    fun submitBrokenSite(
        @Field("reason") reason: String = REASON_BROKEN_SITE,
        @Field("comment") comment: String,
        @Field("platform") platform: String = PLATFORM,
        @Field("url") url: String? = null,
        @Field("v") version: String,
        @Field("os") api: Int,
        @Field("manufacturer") manufacturer: String,
        @Field("model") model: String,
        @Field("atb") atb: String
    ): Call<Void>

    companion object {
        const val REASON_GENERAL = "general"
        const val REASON_BROKEN_SITE = "broken_site"
        private const val PLATFORM = "Android"
    }
}