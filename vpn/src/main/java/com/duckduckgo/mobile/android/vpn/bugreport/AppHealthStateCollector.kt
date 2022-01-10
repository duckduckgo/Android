/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.bugreport

import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.state.VpnStateCollectorPlugin
import com.duckduckgo.mobile.android.vpn.store.AppHealthDatabase
import com.duckduckgo.mobile.android.vpn.store.DatabaseDateFormatter
import com.squareup.anvil.annotations.ContributesMultibinding
import org.json.JSONObject
import org.threeten.bp.Duration
import org.threeten.bp.LocalDateTime
import org.threeten.bp.format.DateTimeFormatter
import timber.log.Timber
import javax.inject.Inject

@ContributesMultibinding(VpnScope::class)
class AppHealthStateCollector @Inject constructor(
    private val appHealthDatabase: AppHealthDatabase
) : VpnStateCollectorPlugin {
    override val collectorName: String = "latestAppBadHealth"

    override suspend fun collectVpnRelatedState(appPackageId: String?): JSONObject {
        val healthState = appHealthDatabase.appHealthDao().latestHealthState()
        Timber.v("Stored app health state: $healthState")

        return JSONObject().apply {
            healthState?.let {
                put(SECONDS_AGO, DatabaseDateFormatter.duration(it.localtime).seconds)
                put(HEALTH_STATE, JSONObject(it.healthDataJsonString))
            }
        }
    }

    companion object {
        private const val SECONDS_AGO = "secondsAgo"
        private const val HEALTH_STATE = "healthState"
    }
}
