/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.privacy

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.global.install.daysInstalled
import com.duckduckgo.app.privacy.store.PrivacySettingsStore
import com.duckduckgo.app.statistics.pixels.Pixel

class HistoricTrackerBlockingObserver(
    private val appInstallStore: AppInstallStore,
    private val privacySettingsStore: PrivacySettingsStore,
    private val pixel: Pixel
) : LifecycleObserver {

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun onApplicationCreated() {

        if (isHistoricUser() && !privacySettingsStore.historicTrackerOptionRecorded) {
            when (privacySettingsStore.privacyOn) {
                true -> pixel.fire(Pixel.PixelName.TRACKER_BLOCKER_HISTORICAL_ON)
                false -> pixel.fire(Pixel.PixelName.TRACKER_BLOCKER_HISTORICAL_OFF)
            }
        }

        privacySettingsStore.historicTrackerOptionRecorded = true
    }

    private fun isHistoricUser() : Boolean {
        return appInstallStore.hasInstallTimestampRecorded() && appInstallStore.daysInstalled() > 0
    }
}