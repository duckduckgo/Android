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

package com.duckduckgo.app.feedback.api

import io.reactivex.Observable
import okhttp3.ResponseBody
import retrofit2.http.Field
import retrofit2.http.POST

interface FeedbackService {

    object Platform {
        const val ANDROID = "Android"
    }

    object Reason {
        const val BROKEN_SITE = "broken_site"
        const val GENERAL = "general"
    }

    @POST("/feedback.js?type=app-feedback")
    fun feedback(
        @Field("reason") reason: String,
        @Field("url") url: String,
        @Field("comment") comment: String,
        @Field("platform") platform: String,
        @Field("os") api: Int,
        @Field("manufacturer") manufacturer: String,
        @Field("model") model: String,
        @Field("v") appVersion: String,
        @Field("atb") atb: String
    ): Observable<ResponseBody>
}