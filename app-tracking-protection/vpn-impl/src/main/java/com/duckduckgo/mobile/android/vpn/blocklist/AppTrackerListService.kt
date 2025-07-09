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
    @GET("$APPTP_TDS_BASE_URL$APPTP_TDS_PATH")
    @AppTPTdsRequired
    fun appTrackerBlocklist(): Call<JsonAppBlockingList>
}

/**
 * This annotation is used in interceptors to be able to intercept the annotated service calls
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class AppTPTdsRequired

const val APPTP_TDS_BASE_URL = "https://staticcdn.duckduckgo.com/trackerblocking/appTP/"
const val APPTP_TDS_PATH = "2.1/android-tds.json"
