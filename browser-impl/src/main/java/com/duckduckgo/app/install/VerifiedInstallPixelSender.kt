/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.install

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.statistics.api.AtbLifecyclePlugin
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Count
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.verifiedinstallation.IsVerifiedPlayStoreInstall
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority
import logcat.asLog
import logcat.logcat
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class VerifiedInstallPixelSender @Inject constructor(
    private val isVerifiedPlayStoreInstall: IsVerifiedPlayStoreInstall,
    private val appBuildConfig: AppBuildConfig,
    private val pixel: Pixel,
    private val verifiedInstallDataStore: VerifiedInstallDataStore,
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
) : AtbLifecyclePlugin {

    // cache this in memory since it can't change at runtime
    private var cachedIsVerifiedInstall: Boolean? = null

    override fun onAppAtbInitialized() {
        appCoroutineScope.launch(dispatchers.io()) {
            if (!isFeatureEnabled()) {
                logcat(LogPriority.VERBOSE) { "Verified-install: feature is disabled so nothing to do in onAppAtbInitialized" }
                return@launch
            }
            if (!isVerifiedInstall()) {
                logcat(LogPriority.VERBOSE) { "Verified-install: not a verified install so nothing to do in onAppAtbInitialized" }
                return@launch
            }

            fireInstallPixelIfNewInstall()
        }
    }

    override fun onAppRetentionAtbRefreshed(oldAtb: String, newAtb: String) {
        appCoroutineScope.launch(dispatchers.io()) {
            if (!isFeatureEnabled()) {
                logcat(LogPriority.VERBOSE) { "Verified-install: feature is disabled so nothing to do in onAppRetentionAtbRefreshed" }
                return@launch
            }
            if (!isVerifiedInstall()) {
                logcat(LogPriority.VERBOSE) { "Verified-install: not a verified install so nothing to do in onAppRetentionAtbRefreshed" }
                return@launch
            }

            fireUpdatePixelIfNewVersion()
        }
    }

    private fun isFeatureEnabled(): Boolean = androidBrowserConfigFeature.sendVerifiedInstallPixels().isEnabled()

    private fun isVerifiedInstall(): Boolean {
        cachedIsVerifiedInstall?.let { return it }

        return runCatching { isVerifiedPlayStoreInstall() }
            .getOrElse { e ->
                logcat(LogPriority.WARN) { "Verified-install: exception checking verified install status: ${e.asLog()}" }
                false
            }
            .also { cachedIsVerifiedInstall = it }
    }

    private suspend fun fireInstallPixelIfNewInstall() {
        val lastVersion = verifiedInstallDataStore.getLastInstalledVersion()
        if (lastVersion != null) {
            logcat(LogPriority.VERBOSE) { "Verified-install: not a new install, skipping install pixel" }
            return
        }

        val currentVersion = appBuildConfig.versionCode
        val returningUser = appBuildConfig.isAppReinstall()
        val params = mapOf(RETURNING_USER_KEY to returningUser.toString())

        logcat { "Verified-install: new install on version $currentVersion, returning user: $returningUser" }
        pixel.fire(AppPixelName.APP_INSTALL_VERIFIED_INSTALL, params, type = Pixel.PixelType.Unique())
        verifiedInstallDataStore.setLastInstalledVersion(currentVersion)
    }

    private suspend fun fireUpdatePixelIfNewVersion() {
        val currentVersion = appBuildConfig.versionCode
        val lastVersion = verifiedInstallDataStore.getLastInstalledVersion()

        if (lastVersion == null) {
            // Existing user before this feature was added - store the version for future updates
            logcat(LogPriority.VERBOSE) { "Verified-install: no stored version, storing version $currentVersion for future updates" }
            verifiedInstallDataStore.setLastInstalledVersion(currentVersion)
            return
        }

        if (lastVersion == currentVersion) {
            logcat(LogPriority.VERBOSE) { "Verified-install: no version change, skipping update pixel" }
            return
        }

        val returningUser = appBuildConfig.isAppReinstall()
        val params = mapOf(RETURNING_USER_KEY to returningUser.toString())

        logcat { "Verified-install: app update. now=$currentVersion, previous=$lastVersion, returning user: $returningUser" }
        pixel.fire(AppPixelName.APP_UPDATE_VERIFIED_INSTALL, params, type = Count)
        verifiedInstallDataStore.setLastInstalledVersion(currentVersion)
    }

    private companion object {
        private const val RETURNING_USER_KEY = "returning_user"
    }
}
