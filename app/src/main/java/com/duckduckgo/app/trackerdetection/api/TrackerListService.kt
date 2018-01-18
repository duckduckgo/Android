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

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface TrackerListService {

    @GET("/contentblocking.js")
    fun list(@Query("l") list: String): Call<ResponseBody>

    @GET("/contentblocking/trackers-whitelist.txt")
    fun trackersWhitelist(): Call<ResponseBody>

    @GET("/contentblocking.js?l=disconnect")
    fun disconnect(): Call<DisconnectListJson>

}
