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
import com.duckduckgo.mobile.android.vpn.model.AppHealthState
import com.duckduckgo.mobile.android.vpn.model.HealthEventType.BAD_HEALTH
import com.duckduckgo.mobile.android.vpn.model.HealthEventType.GOOD_HEALTH
import com.duckduckgo.mobile.android.vpn.state.VpnStateCollectorPlugin
import com.duckduckgo.mobile.android.vpn.store.AppHealthDatabase
import com.duckduckgo.mobile.android.vpn.store.DatabaseDateFormatter
import com.squareup.anvil.annotations.ContributesMultibinding
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject

@ContributesMultibinding(VpnScope::class)
class AppHealthStateCollector @Inject constructor(
    private val appHealthDatabase: AppHealthDatabase
) : VpnStateCollectorPlugin {
    override val collectorName: String = "latestAppBadHealth"

    override suspend fun collectVpnRelatedState(appPackageId: String?): JSONObject {
        val latestBadHealth = appHealthDatabase.appHealthDao().remove(BAD_HEALTH)
        val latestGoodHealth = appHealthDatabase.appHealthDao().remove(GOOD_HEALTH)

        val isGoodHealthNow = latestGoodHealth?.let {
            return@let latestBadHealth == null || it.localtime > latestBadHealth.localtime
        } ?: (latestBadHealth == null)

        Timber.v("Latest app BAD health state: $latestBadHealth")
        Timber.v("Is currently in GOOD health: $isGoodHealthNow")

        // ensure DB table is cleared
        appHealthDatabase.appHealthDao().clearAll()

        return JSONObject().apply {
            latestBadHealth?.let { state ->
                put(IS_CURRENTLY_IN_GOOD_HEALTH, isGoodHealthNow.toString())
                put(
                    BAD_HEALTH_DATA,
                    JSONObject().apply {
                        put(
                            SUSTAINED_BAD_HEALTH_SEC,
                            calculatedSustainedBadHealthInSeconds(badHealth = latestBadHealth, goodHealth = latestGoodHealth)
                        )
                        put(SECONDS_AGO, DatabaseDateFormatter.duration(state.localtime).seconds)
                        put(BAD_HEALTH_DATA, JSONObject(state.healthDataJsonString))
                    }
                )
            }
        }
    }

    private fun calculatedSustainedBadHealthInSeconds(
        badHealth: AppHealthState?,
        goodHealth: AppHealthState?
    ): Long? {
        if (badHealth == null || goodHealth == null) {
            return null
        }

        val secondsSustained = DatabaseDateFormatter.duration(goodHealth.localtime, badHealth.localtime).seconds

        return if (secondsSustained > 0) {
            secondsSustained
        } else {
            null
        }
    }

    companion object {
        private const val SECONDS_AGO = "secondsAgo"
        private const val BAD_HEALTH_DATA = "badHealthData"
        private const val SUSTAINED_BAD_HEALTH_SEC = "secondsSustained"
        private const val IS_CURRENTLY_IN_GOOD_HEALTH = "isCurrentlyGooHealth"
    }
}
