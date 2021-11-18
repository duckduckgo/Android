/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.blocklist

import com.duckduckgo.mobile.android.vpn.trackers.JsonAppBlockingList
import com.duckduckgo.mobile.android.vpn.trackers.JsonAppTrackerExclusionList
import com.duckduckgo.mobile.android.vpn.trackers.JsonAppTrackerExceptionRules
import retrofit2.Call
import retrofit2.http.GET

interface AppTrackerListService {
    @GET("https://staticcdn.duckduckgo.com/trackerblocking/appTB/1.0/blocklist.json")
    fun appTrackerBlocklist(): Call<JsonAppBlockingList>

    @GET("https://staticcdn.duckduckgo.com/trackerblocking/appTB/1.0/apps-unprotected-temporary.json")
    fun appTrackerExclusionList(): Call<JsonAppTrackerExclusionList>

    @GET("https://staticcdn.duckduckgo.com/trackerblocking/appTB/1.0/unbreak.json")
    fun appTrackerExceptionRules(): Call<JsonAppTrackerExceptionRules>
}
