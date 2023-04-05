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

import com.duckduckgo.anvil.annotations.ContributesServiceApi
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.trackers.JsonAppBlockingList
import retrofit2.Call
import retrofit2.http.GET

@ContributesServiceApi(AppScope::class)
interface AppTrackerListService {
    @GET("https://staticcdn.duckduckgo.com/trackerblocking/appTP/2.0/blocklist.json")
    fun appTrackerBlocklist(): Call<JsonAppBlockingList>
}
