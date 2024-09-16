/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.trackerdetection.api

import com.duckduckgo.anvil.annotations.ContributesServiceApi
import com.duckduckgo.di.scopes.AppScope
import retrofit2.Call
import retrofit2.http.GET

@ContributesServiceApi(AppScope::class)
interface TrackerListService {
    @GET("${TDS_URL}current/android-tds.json")
    fun tds(): Call<TdsJson>

    @GET("/contentblocking/trackers-unprotected-temporary.txt")
    fun temporaryAllowList(): Call<String>
}

const val TDS_URL = "https://staticcdn.duckduckgo.com/trackerblocking/v5/"
