/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.installation.impl.installer

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.plugins.pixel.PixelParamRemovalPlugin
import com.duckduckgo.common.utils.plugins.pixel.PixelParamRemovalPlugin.PixelParameter
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.installation.impl.installer.InstallationPixelName.APP_INSTALLER_FULL_PACKAGE_NAME
import com.duckduckgo.installation.impl.installer.InstallationPixelName.APP_INSTALLER_PACKAGE_NAME
import com.duckduckgo.installation.impl.installer.fullpackage.InstallSourceFullPackageStore
import com.duckduckgo.installation.impl.installer.fullpackage.feature.InstallSourceFullPackageFeature
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority.INFO
import logcat.LogPriority.VERBOSE
import logcat.logcat

@SuppressLint("DenyListedApi")
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = PrivacyConfigCallbackPlugin::class,
)
@SingleInstanceIn(AppScope::class)
class InstallSourcePrivacyConfigObserver @Inject constructor(
    private val installSourceExtractor: InstallSourceExtractor,
    private val context: Context,
    private val pixel: Pixel,
    private val dispatchers: DispatcherProvider,
    private val installSourceFullPackageFeature: InstallSourceFullPackageFeature,
    private val store: InstallSourceFullPackageStore,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : PrivacyConfigCallbackPlugin {

    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences(SHARED_PREFERENCES_FILENAME, Context.MODE_PRIVATE)
    }

    override fun onPrivacyConfigDownloaded() {
        appCoroutineScope.launch(dispatchers.io()) {
            if (!hasAlreadyProcessed()) {
                val installationSource = installSourceExtractor.extract()
                logcat(INFO) { "Installation source extracted: $installationSource" }

                sendPixelIndicatingIfPlayStoreInstall(installationSource)
                conditionallySendFullInstallerPackage(installationSource)

                recordInstallSourceProcessed()
            } else {
                logcat(VERBOSE) { "Already processed" }
            }
        }
    }

    private fun sendPixelIndicatingIfPlayStoreInstall(installationSource: String?) {
        val isFromPlayStoreParam = if (installationSource == PLAY_STORE_PACKAGE_NAME) "1" else "0"
        val params = mapOf(PIXEL_PARAMETER_INSTALLED_THROUGH_PLAY_STORE to isFromPlayStoreParam)
        pixel.fire(APP_INSTALLER_PACKAGE_NAME, params)
    }

    private suspend fun conditionallySendFullInstallerPackage(installationSource: String?) {
        if (installationSource.shouldSendFullInstallerPackage()) {
            val params = mapOf(PIXEL_PARAMETER_FULL_INSTALLER_SOURCE to installationSource.toString())
            pixel.fire(APP_INSTALLER_FULL_PACKAGE_NAME, params)
        }
    }

    private suspend fun String?.shouldSendFullInstallerPackage(): Boolean {
        if (!installSourceFullPackageFeature.self().isEnabled()) {
            return false
        }

        val packages = store.getInstallSourceFullPackages()
        return packages.hasWildcard() || packages.list.contains(this)
    }

    @VisibleForTesting
    fun recordInstallSourceProcessed() {
        sharedPreferences.edit {
            putBoolean(SHARED_PREFERENCES_PROCESSED_KEY, true)
        }
    }

    private fun hasAlreadyProcessed(): Boolean {
        return sharedPreferences.getBoolean(SHARED_PREFERENCES_PROCESSED_KEY, false)
    }

    companion object {
        private const val PLAY_STORE_PACKAGE_NAME = "com.android.vending"
        private const val SHARED_PREFERENCES_FILENAME = "com.duckduckgo.app.installer.InstallSource"
        private const val SHARED_PREFERENCES_PROCESSED_KEY = "processed"
        private const val PIXEL_PARAMETER_INSTALLED_THROUGH_PLAY_STORE = "installedThroughPlayStore"
        private const val PIXEL_PARAMETER_FULL_INSTALLER_SOURCE = "package"
    }
}

enum class InstallationPixelName(override val pixelName: String) : Pixel.PixelName {
    APP_INSTALLER_PACKAGE_NAME("m_installation_source"),
    APP_INSTALLER_FULL_PACKAGE_NAME("m_installation_installer"),
}

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = PixelParamRemovalPlugin::class,
)
object InstallerPixelsRequiringDataCleaning : PixelParamRemovalPlugin {
    override fun names(): List<Pair<String, Set<PixelParameter>>> {
        return listOf(
            APP_INSTALLER_FULL_PACKAGE_NAME.pixelName to PixelParameter.removeAtb(),
        )
    }
}
