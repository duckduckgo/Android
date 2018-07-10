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
import java.util.concurrent.TimeUnit
import javax.inject.Inject


interface DefaultBrowserNotification {
    fun shouldShowBannerNotification(
        browserShowing: Boolean,
        timeNow: Long = System.currentTimeMillis()
    ): Boolean

    fun shouldShowHomeScreenCallToActionNotification(): Boolean
}

class DefaultBrowserTimeBasedNotification @Inject constructor(
    private val defaultBrowserDetector: DefaultBrowserDetector,
    private val appInstallStore: AppInstallStore,
    private val variantManager: VariantManager
) : DefaultBrowserNotification {

    override fun shouldShowBannerNotification(browserShowing: Boolean, timeNow: Long): Boolean {

        if (!browserShowing) {
            return false
        }

        if (!isDeviceCapable()) {
            return false
        }

        if (!isFeatureEnabled(DefaultBrowserFeature.ShowBanner)) {
            return false
        }

        if (isAlreadyDefaultBrowser()) {
            return false
        }

        if (appInstallStore.hasUserDeclinedDefaultBrowserBannerPreviously()) {
            return false
        }

        return hasEnoughTimeElapsed(timeNow)
    }

    override fun shouldShowHomeScreenCallToActionNotification(): Boolean {

        if (!isDeviceCapable()) {
            return false
        }

        if (!isFeatureEnabled(DefaultBrowserFeature.ShowHomeScreenCallToAction)) {
            return false
        }

        if (isAlreadyDefaultBrowser()) {
            return false
        }

        if (appInstallStore.hasUserDeclinedDefaultBrowserHomeScreenCallToActionPreviously()) {
            return false
        }

        return true
    }

    private fun isDeviceCapable(): Boolean {
        return defaultBrowserDetector.deviceSupportsDefaultBrowserConfiguration() && appInstallStore.hasInstallTimestampRecorded()
    }

    private fun isFeatureEnabled(feature: DefaultBrowserFeature): Boolean {
        return variantManager.getVariant().hasFeature(feature)
    }

    private fun isAlreadyDefaultBrowser(): Boolean {
        return defaultBrowserDetector.isCurrentlyConfiguredAsDefaultBrowser()
    }

    private fun hasEnoughTimeElapsed(now: Long): Boolean {
        val elapsed = calculateElapsedTime(now)

        return elapsed >= ELAPSED_TIME_THRESHOLD_MS
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