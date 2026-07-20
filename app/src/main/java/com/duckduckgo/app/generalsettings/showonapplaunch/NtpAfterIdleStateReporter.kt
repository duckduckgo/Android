/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.app.generalsettings.showonapplaunch

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.api.AtbLifecyclePlugin
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.newtabpage.api.NtpAfterIdleManager
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Emits a daily snapshot of the after-idle settings: one return-to-last-tab-enabled/disabled pixel and
 * one idle-timeout pixel per active user per day, including the defaults for users who never changed
 * a setting.
 */
@ContributesMultibinding(AppScope::class)
class NtpAfterIdleStateReporter @Inject constructor(
    private val pixel: Pixel,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val settingsDataStore: SettingsDataStore,
    private val ntpAfterIdleManager: NtpAfterIdleManager,
    private val idleThresholdResolver: IdleThresholdResolver,
) : AtbLifecyclePlugin {

    override fun onSearchRetentionAtbRefreshed(oldAtb: String, newAtb: String) {
        report()
    }

    override fun onAppRetentionAtbRefreshed(oldAtb: String, newAtb: String) {
        report()
    }

    private fun report() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            val returnToLastTabEnabled = ntpAfterIdleManager.returnToLastTabEnabled.firstOrNull() ?: true
            pixel.fire(
                if (returnToLastTabEnabled) {
                    NtpAfterIdleStatePixelName.RETURN_TO_LAST_TAB_ENABLED_DAILY
                } else {
                    NtpAfterIdleStatePixelName.RETURN_TO_LAST_TAB_DISABLED_DAILY
                },
                type = Daily(),
            )

            val idleTimeoutSeconds =
                idleThresholdResolver.effectiveThresholdSeconds(settingsDataStore.userSelectedIdleThresholdSeconds)
            idleTimeoutSeconds.toIdleTimeoutPixel()?.let { pixel.fire(it, type = Daily()) }
        }
    }

    private fun Long.toIdleTimeoutPixel(): NtpAfterIdleStatePixelName? = when (this) {
        0L -> NtpAfterIdleStatePixelName.IDLE_TIMEOUT_ALWAYS_DAILY
        60L -> NtpAfterIdleStatePixelName.IDLE_TIMEOUT_60_DAILY
        300L -> NtpAfterIdleStatePixelName.IDLE_TIMEOUT_300_DAILY
        600L -> NtpAfterIdleStatePixelName.IDLE_TIMEOUT_600_DAILY
        1800L -> NtpAfterIdleStatePixelName.IDLE_TIMEOUT_1800_DAILY
        3600L -> NtpAfterIdleStatePixelName.IDLE_TIMEOUT_3600_DAILY
        43200L -> NtpAfterIdleStatePixelName.IDLE_TIMEOUT_43200_DAILY
        86400L -> NtpAfterIdleStatePixelName.IDLE_TIMEOUT_86400_DAILY
        else -> null
    }
}
