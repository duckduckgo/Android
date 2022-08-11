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

package com.duckduckgo.mobile.android.vpn.bugreport

import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.formatters.time.model.dateOfLastDay
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.state.VpnStateCollectorPlugin
import com.duckduckgo.app.global.formatters.time.DatabaseDateFormatter
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.threeten.bp.LocalDateTime
import javax.inject.Inject

@ContributesMultibinding(VpnScope::class)
class VpnLastTrackersBlockedCollector @Inject constructor(
    private val vpnDatabase: VpnDatabase,
    private val dispatcherProvider: DispatcherProvider,
    private val moshi: Moshi,
) : VpnStateCollectorPlugin {

    override val collectorName: String
        get() = "trackersBlockedLastDay"

    override suspend fun collectVpnRelatedState(appPackageId: String?): JSONObject {
        return withContext(dispatcherProvider.io()) {
            val result = mutableMapOf<String, List<String>>()
            vpnDatabase.vpnTrackerDao().getTrackersBetweenSync(dateOfLastDay(), noEndDate())
                .filter { tracker -> appPackageId?.let { tracker.trackingApp.packageId == it } ?: true }
                .groupBy { it.trackingApp.packageId }
                .mapValues { entry -> entry.value.map { it.domain } }
                .onEach {
                    result[it.key] = it.value.toSet().toList()
                }

            val adapter = moshi.adapter<Map<String, List<String>>>(
                Types.newParameterizedType(
                    Map::class.java,
                    String::class.java,
                    List::class.java,
                    String::class.java
                )
            )
            return@withContext JSONObject(adapter.toJson(result))
        }
    }

    private fun noEndDate(): String {
        return DatabaseDateFormatter.timestamp(LocalDateTime.of(9999, 1, 1, 0, 0))
    }
}
