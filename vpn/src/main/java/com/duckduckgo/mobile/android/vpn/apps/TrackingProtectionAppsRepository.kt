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

package com.duckduckgo.mobile.android.vpn.apps

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerExcludedPackage
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerManualExcludedApp
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerRepository
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.flowOn

interface TrackingProtectionAppsRepository {
    /** @return the list of installed apps and information about its excluded state */
    suspend fun getProtectedApps(): Flow<List<TrackingProtectionAppInfo>>

    /** @return the list of installed apps currently excluded */
    suspend fun getExclusionAppsList(): List<String>

    /** Remove the app to the exclusion list so that its traffic does not go through the VPN */
    suspend fun manuallyEnabledApp(packageName: String)

    /** Add the app to the exclusion list so that its traffic goes through the VPN */
    suspend fun manuallyExcludedApp(packageName: String)

    /** Restore protection to the default list */
    suspend fun restoreDefaultProtectedList()
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealTrackingProtectionAppsRepository @Inject constructor(
    private val packageManager: PackageManager,
    private val appTrackerRepository: AppTrackerRepository,
    private val dispatcherProvider: DispatcherProvider
) : TrackingProtectionAppsRepository {

    private var installedApps: Sequence<ApplicationInfo> = emptySequence()

    override suspend fun getProtectedApps(): Flow<List<TrackingProtectionAppInfo>> {
        return appTrackerRepository.getAppExclusionListFlow()
            .combine(appTrackerRepository.getManualAppExclusionListFlow()) { ddgExclusionList, manualList ->
                Timber.d("getProtectedApps flow")
                installedApps
                    .map {
                        val isExcluded = shouldBeExcluded(it, ddgExclusionList, manualList)
                        TrackingProtectionAppInfo(
                            packageName = it.packageName,
                            name = packageManager.getApplicationLabel(it).toString(),
                            type = it.getAppType(),
                            category = it.parseAppCategory(),
                            isExcluded = isExcluded,
                            knownProblem = hasKnownIssue(it, ddgExclusionList),
                            userModifed = isUserModified(it.packageName, manualList)
                        )
                    }
                    .sortedBy { it.name.lowercase() }
                    .toList()
            }.onStart {
                refreshInstalledApps()
            }.flowOn(dispatcherProvider.io())
    }

    private fun refreshInstalledApps() {
        Timber.d("Excluded Apps: refreshInstalledApps")
        installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .asSequence()
            .filterNot { shouldNotBeShown(it) }
    }

    override suspend fun getExclusionAppsList(): List<String> = withContext(dispatcherProvider.io()) {
        val exclusionList = appTrackerRepository.getAppExclusionList()
        val manualExclusionList = appTrackerRepository.getManualAppExclusionList()
        return@withContext packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .asSequence()
            .filter { shouldBeExcluded(it, exclusionList, manualExclusionList) }
            .sortedBy { it.name }
            .map { it.packageName }
            .toList()
    }

    private fun shouldNotBeShown(appInfo: ApplicationInfo): Boolean {
        return VpnExclusionList.isDdgApp(appInfo.packageName) || isSystemAppAndNotOverriden(appInfo)
    }

    private fun isSystemAppAndNotOverriden(appInfo: ApplicationInfo): Boolean {
        return if (appTrackerRepository.getSystemAppOverrideList().map { it.packageId }.contains(appInfo.packageName)) {
            false
        } else {
            appInfo.isSystemApp()
        }
    }

    private fun shouldBeExcluded(
        appInfo: ApplicationInfo,
        ddgExclusionList: List<AppTrackerExcludedPackage>,
        userExclusionList: List<AppTrackerManualExcludedApp>
    ): Boolean {
        return VpnExclusionList.isDdgApp(appInfo.packageName) ||
            isSystemAppAndNotOverriden(appInfo) ||
            isManuallyExcluded(appInfo, ddgExclusionList, userExclusionList)
    }

    private fun isManuallyExcluded(
        appInfo: ApplicationInfo,
        ddgExclusionList: List<AppTrackerExcludedPackage>,
        userExclusionList: List<AppTrackerManualExcludedApp>
    ): Boolean {
        val userExcludedApp = userExclusionList.find { it.packageId == appInfo.packageName }
        if (userExcludedApp != null) {
            return !userExcludedApp.isProtected
        }

        if (appInfo.isGame()) {
            return true
        }

        return ddgExclusionList.any { it.packageId == appInfo.packageName }
    }

    private fun hasKnownIssue(
        appInfo: ApplicationInfo,
        ddgExclusionList: List<AppTrackerExcludedPackage>
    ): Int {
        if (BROWSERS.contains(appInfo.packageName)) {
            return TrackingProtectionAppInfo.LOADS_WEBSITES_EXCLUSION_REASON
        }
        if (ddgExclusionList.any { it.packageId == appInfo.packageName }) {
            return TrackingProtectionAppInfo.KNOWN_ISSUES_EXCLUSION_REASON
        }
        if (appInfo.isGame()) {
            return TrackingProtectionAppInfo.KNOWN_ISSUES_EXCLUSION_REASON
        }
        return TrackingProtectionAppInfo.NO_ISSUES
    }

    private fun isUserModified(
        packageName: String,
        userExclusionList: List<AppTrackerManualExcludedApp>
    ): Boolean {
        val userExcludedApp = userExclusionList.find { it.packageId == packageName }
        return userExcludedApp != null
    }

    override suspend fun manuallyEnabledApp(packageName: String) {
        withContext(dispatcherProvider.io()) {
            appTrackerRepository.manuallyEnabledApp(packageName)
        }
    }

    override suspend fun manuallyExcludedApp(packageName: String) {
        withContext(dispatcherProvider.io()) {
            appTrackerRepository.manuallyExcludedApp(packageName)
        }
    }

    override suspend fun restoreDefaultProtectedList() {
        withContext(dispatcherProvider.io()) {
            appTrackerRepository.restoreDefaultProtectedList()
        }
    }

    companion object {
        private val BROWSERS = listOf(
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
            "org.chromium.chrome",
            "com.microsoft.bing",
            "com.yahoo.mobile.client.android.search",
            "com.google.android.apps.searchlite",
            "com.baidu.searchbox",
            "ru.yandex.searchplugin",
            "com.ecosia.android",
            "com.qwant.liberty",
            "com.qwantjunior.mobile",
            "com.nhn.android.search",
            "cz.seznam.sbrowser",
            "com.coccoc.trinhduyet"
        )
    }
}
