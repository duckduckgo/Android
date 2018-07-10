/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.browser.defaultBrowsing

import com.duckduckgo.app.browser.BuildConfig
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.VariantManager.VariantFeature.DefaultBrowserFeature
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject


interface DefaultBrowserNotification {
    fun shouldShowBanner(
        browserShowing: Boolean,
        timeNow: Long = System.currentTimeMillis()
    ): Boolean

    fun shouldShowCallToActionButton(
        browserShowing: Boolean,
        timeNow: Long = System.currentTimeMillis()
    ): Boolean
}

class DefaultBrowserNotificationFeatureAnalyzer @Inject constructor(
    private val defaultBrowserDetector: DefaultBrowserDetector,
    private val appInstallStore: AppInstallStore,
    private val variantManager: VariantManager
) : DefaultBrowserNotification {

    override fun shouldShowBanner(browserShowing: Boolean, timeNow: Long): Boolean {
        if (!browserShowing) {
            return false
        }

        return conditionsAllowShowingNotification(timeNow)
    }

    override fun shouldShowCallToActionButton(browserShowing: Boolean, timeNow: Long): Boolean {
        if (browserShowing) {
            return false
        }

        return conditionsAllowShowingNotification(timeNow)
    }

    private fun conditionsAllowShowingNotification(timeNow: Long): Boolean {
        if (!isDeviceCapable()) {
            return false
        }

        if (!isFeatureEnabled()) {
            return false
        }

        if (isAlreadyDefaultBrowser()) {
            return false
        }

        if (hasUserDeclinedPreviously()) {
            return false
        }

        return hasEnoughTimeElapsed(timeNow)
    }

    private fun isDeviceCapable(): Boolean {
        return defaultBrowserDetector.deviceSupportsDefaultBrowserConfiguration() && appInstallStore.hasInstallTimestampRecorded()
    }

    private fun isFeatureEnabled(): Boolean {
        return variantManager.getVariant().hasFeature(DefaultBrowserFeature.ShowTimedReminder)
    }

    private fun isAlreadyDefaultBrowser(): Boolean {
        return defaultBrowserDetector.isCurrentlyConfiguredAsDefaultBrowser()
    }

    private fun hasUserDeclinedPreviously(): Boolean {
        return appInstallStore.hasUserDeclinedDefaultBrowserPreviously()
    }

    private fun hasEnoughTimeElapsed(now: Long): Boolean {
        val elapsed = calculateElapsedTime(now)

        return if (elapsed >= ELAPSED_TIME_THRESHOLD_MS) {
            Timber.v("Enough time has elapsed to show banner")
            true
        } else {
            Timber.v("Not enough time has elapsed to show banner")
            false
        }
    }

    private fun calculateElapsedTime(now: Long): Long {
        return now - appInstallStore.installTimestamp
    }

    companion object {

        // time period to wait after first launch before showing notification banner - set much shorter for DEBUG builds so it can be tested
        private val ELAPSED_TIME_THRESHOLD_PRODUCTION = TimeUnit.DAYS.toMillis(3)
        private val ELAPSED_TIME_THRESHOLD_DEBUG = TimeUnit.SECONDS.toMillis(20)

        private val ELAPSED_TIME_THRESHOLD_MS = if (BuildConfig.DEBUG) ELAPSED_TIME_THRESHOLD_DEBUG else ELAPSED_TIME_THRESHOLD_PRODUCTION
    }

}