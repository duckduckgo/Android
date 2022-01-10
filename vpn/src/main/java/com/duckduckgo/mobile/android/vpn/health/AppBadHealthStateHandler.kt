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

package com.duckduckgo.mobile.android.vpn.health

import android.util.Base64
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.model.AppHealthState
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.store.AppHealthDatabase
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.Moshi
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class AppBadHealthStateHandler @Inject constructor(
    private val appHealthDatabase: AppHealthDatabase,
    private val deviceShieldPixels: DeviceShieldPixels,
    private val dispatcherProvider: DispatcherProvider
) : AppHealthCallback {
    override suspend fun onAppHealthUpdate(appHealthData: AppHealthData): Boolean {
        withContext(dispatcherProvider.io()) {
            if (appHealthData.alerts.isNotEmpty()) {
                // we don't include raw metrics marked as "redacted" as they can contain information that could
                // be used to fingerprint.
                val (badHealthMetrics, _) = appHealthData.systemHealth.rawMetrics.partition { it.isInBadHealth() && !it.redacted }
                val badHealthData = appHealthData.copy(systemHealth = appHealthData.systemHealth.copy(rawMetrics = badHealthMetrics))
                val jsonAdapter = Moshi.Builder().build().run {
                    adapter(AppHealthData::class.java)
                }

                val json = jsonAdapter.toJson(badHealthData)
                appHealthDatabase.appHealthDao().insert(
                    AppHealthState(alerts = appHealthData.alerts, healthDataJsonString = json)
                )

                // send pixel with only bad health
                pixelBadHealth(json)

                Timber.v("Storing app health alerts in local store: $badHealthData")
            } else {
                appHealthDatabase.appHealthDao().clear()
                Timber.d("No alerts, clearing storage")
            }
        }

        return false
    }

    private fun pixelBadHealth(badHealthJsonString: String) {
        val encodedData = Base64.encodeToString(
            badHealthJsonString.toByteArray(), Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE,
        )

        deviceShieldPixels.sendHealthMonitorReport(mapOf("badHealthData" to encodedData))
    }
}
