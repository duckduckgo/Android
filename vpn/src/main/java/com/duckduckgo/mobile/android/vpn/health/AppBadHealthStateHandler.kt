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

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.core.content.edit
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.utils.ConflatedJob
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor
import com.duckduckgo.appbuildconfig.api.BuildFlavor.INTERNAL
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.model.AppHealthState
import com.duckduckgo.mobile.android.vpn.model.HealthEventType.BAD_HEALTH
import com.duckduckgo.mobile.android.vpn.model.HealthEventType.GOOD_HEALTH
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.duckduckgo.mobile.android.vpn.store.AppHealthDatabase
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.Moshi
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.threeten.bp.LocalDateTime
import org.threeten.bp.format.DateTimeFormatter
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes

@ContributesMultibinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class AppBadHealthStateHandler @Inject constructor(
    private val context: Context,
    private val appBuildConfig: AppBuildConfig,
    private val appHealthDatabase: AppHealthDatabase,
    private val deviceShieldPixels: DeviceShieldPixels,
    private val dispatcherProvider: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : AppHealthCallback {

    private var debounceJob = ConflatedJob()

    private val preferences: SharedPreferences
        get() = context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE)

    private var backoffIncrement: Long
        get() = preferences.getLong("backoff", INITIAL_BACKOFF_INCREMENT)
        set(value) {
            preferences.edit { putLong("backoff", value) }
        }

    private var restartBoundary: String?
        get() = preferences.getString("restartBoundary", null)
        set(value) {
            preferences.edit { putString("restartBoundary", value) }
        }

    override suspend fun onAppHealthUpdate(appHealthData: AppHealthData): Boolean {
        return withContext(dispatcherProvider.io()) {
            if (appHealthData.alerts.isNotEmpty()) {
                // send first-in-day pixels for alerts so what we can gather how many users see a particular alert every day
                sendFirstInDayAlertPixels(appHealthData.alerts)

                // we don't include raw metrics marked as "redacted" as they can contain information that could
                // be used to fingerprint.
                val (badHealthMetrics, _) = appHealthData.systemHealth.rawMetrics.partition { it.isInBadHealth() && !it.redacted }
                val badHealthData = appHealthData.copy(systemHealth = appHealthData.systemHealth.copy(rawMetrics = badHealthMetrics))
                val jsonAdapter = Moshi.Builder().build().run {
                    adapter(AppHealthData::class.java)
                }

                val json = jsonAdapter.toJson(badHealthData)
                appHealthDatabase.appHealthDao().insert(
                    AppHealthState(type = BAD_HEALTH, alerts = appHealthData.alerts, healthDataJsonString = json)
                )

                Timber.v("Storing app health alerts in local store: $badHealthData")

                return@withContext pixelAndMaybeRestartVpn(json)
            } else {
                appHealthDatabase.appHealthDao().insert(
                    AppHealthState(type = GOOD_HEALTH, alerts = listOf(), healthDataJsonString = "")
                )
                resetBackoff()
                Timber.d("No alerts")
                return@withContext false
            }
        }
    }

    private fun resetBackoff() {
        backoffIncrement = INITIAL_BACKOFF_INCREMENT
        restartBoundary = null
    }

    private fun sendFirstInDayAlertPixels(alerts: List<String>) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            alerts.forEach { alert ->
                deviceShieldPixels.sendHealthMonitorAlert(alert)
            }
        }
    }

    private suspend fun pixelAndMaybeRestartVpn(json: String): Boolean {
        val boundary = restartBoundary
        return if (shouldRestartVpn(boundary)) {
            Timber.v("Internal flavor detected, restarting the VPN...")

            // update the restart boundary
            DATE_FORMATTER.format(LocalDateTime.now().plusSeconds(backoffIncrement)).run {
                backoffIncrement *= 2
                restartBoundary = this
            }

            Timber.v("backoff = $backoffIncrement, boundary = $restartBoundary")

            debouncedPixelBadHealth(json, restarted = true)

            // place this in a different job to ensure the restart completes successfully and nobody can cancel it by mistake
            appCoroutineScope.launch {
                TrackerBlockingVpnService.restartVpnService(context, forceGc = true)
            }.join()
            true
        } else {
            Timber.v("Cancelled VPN restart, backoff boundary ($boundary)...")
            debouncedPixelBadHealth(json)
            false
        }
    }

    private fun shouldRestartVpn(boundary: String?): Boolean {
        // only restart in internal builds for now
        if (appBuildConfig.flavor != BuildFlavor.INTERNAL) return false

        val now = DATE_FORMATTER.format(LocalDateTime.now())
        return (boundary == null || now >= boundary)
    }

    private fun debouncedPixelBadHealth(
        badHealthJsonString: String,
        restarted: Boolean = false
    ) {
        if (debounceJob.isActive) {
            Timber.v("debouncing bad health pixel firing")
            return
        }
        // Place this in a different job (form the app scope) to make sure it is sent
        // Debounced it to deduplicate pixels as if the VPN is restarted, we'll get immediately a call to onAppHealthUpdate() with same bad-health
        debounceJob += appCoroutineScope.launch(dispatcherProvider.io()) {
            val encodedData = Base64.encodeToString(
                badHealthJsonString.toByteArray(), Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE,
            )
            deviceShieldPixels.sendHealthMonitorReport(
                mapOf(
                    "manufacturer" to appBuildConfig.manufacturer,
                    // model only in internal builds to avoid privacy issues in production for now
                    "model" to if (appBuildConfig.flavor.isInternal()) appBuildConfig.flavor.toString() else "redacted",
                    "restarted" to restarted.toString(),
                    "badHealthData" to encodedData,
                )
            )
            delay(1000)
        }
    }

    private fun BuildFlavor.isInternal(): Boolean {
        return this == INTERNAL
    }

    companion object {
        private const val FILENAME = "com.duckduckgo.mobile.android.vpn.app.health.state"
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
        private val INITIAL_BACKOFF_INCREMENT: Long = 1.minutes.inWholeSeconds
    }
}
