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

package com.duckduckgo.vpn.internal.feature.health

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.health.AppHealthCallback
import com.duckduckgo.mobile.android.vpn.health.AppHealthData
import com.squareup.anvil.annotations.ContributesMultibinding
import timber.log.Timber
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class AppHealthCallbackNotification @Inject constructor(
    private val healthNotificationManager: HealthNotificationManager
) : AppHealthCallback {
    override suspend fun onAppHealthUpdate(appHealthData: AppHealthData): Boolean {
        if (appHealthData.alerts.isNotEmpty()) {
            Timber.i("App health check caught some problem(s).\n%s", appHealthData.alerts.joinToString(separator = "\n"))
            healthNotificationManager.showBadHealthNotification(appHealthData.alerts, appHealthData.systemHealth)
        } else {
            Timber.i("App health check is good")
            healthNotificationManager.hideBadHealthNotification()
        }

        return false
    }
}
