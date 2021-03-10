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

package com.duckduckgo.mobile.android.vpn.exclusions

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo.FLAG_SYSTEM
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.core.content.edit
import com.duckduckgo.di.scopes.AppObjectGraph
import com.duckduckgo.mobile.android.vpn.BuildConfig
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import timber.log.Timber
import javax.inject.Singleton

@Module
@ContributesTo(AppObjectGraph::class)
class DeviceShieldExcludedAppsModule {
    @Provides
    @Singleton
    fun provideDeviceShieldExcludedApps(
        context: Context,
        packageManager: PackageManager
    ): DeviceShieldExcludedApps = DeviceShieldExcludedAppsImpl(context, packageManager)
}

data class DeviceShieldApp(
    val name: String,
    val packageName: String,
    val type: String?,
    val icon: Drawable? = null,
    var isExcluded: Boolean = true
)
interface DeviceShieldExcludedApps {
    /** @return the list of installed apps currently excluded */
    fun getExcludedApps(): List<DeviceShieldApp>

    /** @return the complete exclusion list (app package names) */
    fun getExclusionList(): List<String>

    fun removeFromExclusionList(packageName: String)

    fun addToExclusionList(packageName: String)
}

private class DeviceShieldExcludedAppsImpl(private val context: Context, private val packageManager: PackageManager) : DeviceShieldExcludedApps {

    private val preferences: SharedPreferences
        get() = context.getSharedPreferences("com.duckduckgo.mobile.android.vpn.exclusions", Context.MODE_MULTI_PROCESS)

    override fun getExcludedApps(): List<DeviceShieldApp> {
        return EXCLUDED_APPS
            .filter { isInstalled(it) }
            .filter { isNotDdgApp(it) }
            .map {
                DeviceShieldApp(
                    name = getAppName(it),
                    packageName = it,
                    icon = getAppIcon(it),
                    type = getAppType(it),
                    isExcluded = !isRemovedFromExclusionList(it)
                )
            }
            .sortedBy { it.name }
    }

    override fun getExclusionList(): List<String> {
        return EXCLUDED_APPS
            .filter { !isRemovedFromExclusionList(it) }
    }

    override fun removeFromExclusionList(packageName: String) {
        preferences.edit { putBoolean(packageName, true) }
    }

    override fun addToExclusionList(packageName: String) {
        preferences.edit { putBoolean(packageName, false) }
    }

    private fun isRemovedFromExclusionList(packageName: String): Boolean {
        return preferences.getBoolean(packageName, false)
    }

    private fun getAppIcon(packageName: String): Drawable? {
        return try {
            return packageManager.getApplicationIcon(packageName)
        } catch (t: Throwable) {
            Timber.d("Error getting icon for $packageName")
            null
        }
    }

    private fun getAppName(packageName: String): String {
        return try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (t: Throwable) {
            "(Unknown)"
        }
    }

    private fun getAppType(packageName: String): String? {
        return try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            if ((info.flags and FLAG_SYSTEM) != 0) {
                "System"
            } else {
                null
            }
        } catch (t: Throwable) {
            null
        }
    }

    private fun isInstalled(packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (t: Throwable) {
            false
        }
    }

    private fun isNotDdgApp(packageName: String): Boolean {
        return !packageName.startsWith(DDG_PACKAGE_PREFIX)
    }

    private companion object {
        private const val DDG_PACKAGE_PREFIX = "com.duckduckgo.mobile"

        private val EXCLUDED_SYSTEM_APPS = listOf(
            "com.android.vending",
            "com.google.android.gsf.login",
            "com.google.android.googlequicksearchbox",
            "com.google.android.gms",
            "com.google.android.backuptransport"
        )

        private val EXCLUDED_PROBLEMATIC_APPS = listOf(
            "com.facebook.talk",
            "com.facebook.bishop",
            "com.facebook.games",
            "com.whatsapp",
            "com.facebook.katana",
            "com.facebook.lite",
            "com.facebook.orca",
            "com.facebook.mlite",
            "com.instagram.android",
            "com.facebook.pages.app",
            "com.facebook.work",
            "com.facebook.workchat",
            "com.facebook.creatorstudio",
            "com.facebook.Socal",
            "com.facebook.arstudio.player",
            "com.facebook.adsmanager",
            "com.facebook.analytics",
            "com.facebook.Origami",
            // deferred breakages
            "com.shopify.arrive",
            "com.discord",
            "com.lasoo.android.target",
            "com.myklarnamobile",
            "com.reddit.frontpage",
            "com.mbam.poshtips",
            "com.nasscript.fetch",
            "com.gotv.nflgamecenter.us.lite"
        )

        private val FIRST_PARTY_TRACKERS_ONLY_APPS = listOf(
            "com.google.android.youtube"
        )

        private val MAJOR_BROWSERS = listOf(
            "com.duckduckgo.mobile.android",
            "com.duckduckgo.mobile.android.debug",
            "com.duckduckgo.mobile.android.vpn",
            "com.duckduckgo.mobile.android.vpn.debug",
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.opera.browser",
            "com.microsoft.emmx",
            "com.brave.browser",
            "com.UCMobile.intl",
            "com.android.browser",
            "com.sec.android.app.sbrowser",
            "info.guardianproject.orfo",
            "org.torproject.torbrowser_alpha",
            "mobi.mgeek.TunnyBrowser",
            "com.linkbubble.playstore",
            "org.adblockplus.browser",
            "arun.com.chromer",
            "com.flynx",
            "com.ghostery.android.ghostery",
            "com.cliqz.browser",
            "com.opera.mini.native",
            "com.uc.browser.en",
            "com.chrome.beta",
            "org.mozilla.firefox_beta",
            "com.opera.browser.beta",
            "com.opera.mini.native.beta",
            "com.sec.android.app.sbrowser.beta",
            "org.mozilla.fennec_fdroid",
            "org.mozilla.rocket",
            "com.chrome.dev",
            "com.chrome.canary",
            "org.mozilla.fennec_aurora",
            "org.mozilla.fennec",
            "com.google.android.apps.chrome",
            "org.chromium.chrome"
        )

        private val EXCLUDED_APPS =
            setOf(BuildConfig.LIBRARY_PACKAGE_NAME)
                .plus(EXCLUDED_SYSTEM_APPS)
                .plus(EXCLUDED_PROBLEMATIC_APPS)
                .plus(FIRST_PARTY_TRACKERS_ONLY_APPS)
                .plus(MAJOR_BROWSERS)
    }
}
