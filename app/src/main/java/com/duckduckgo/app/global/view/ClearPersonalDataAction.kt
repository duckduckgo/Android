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

package com.duckduckgo.app.global.view

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.webkit.WebStorageCompat
import com.duckduckgo.app.browser.WebDataManager
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker.WebViewCapability.DeleteBrowsingData
import com.duckduckgo.app.browser.cookies.ThirdPartyCookieManager
import com.duckduckgo.app.fire.AppCacheClearer
import com.duckduckgo.app.fire.FireActivity
import com.duckduckgo.app.fire.UnsentForgetAllPixelStore
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteRepository
import com.duckduckgo.app.fire.store.TabVisitedSitesRepository
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.trackerdetection.api.WebTrackersBlockedRepository
import com.duckduckgo.common.utils.DefaultDispatcherProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.cookies.api.DuckDuckGoCookieManager
import com.duckduckgo.duckchat.api.DuckAiHostProvider
import com.duckduckgo.history.api.NavigationHistory
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.site.permissions.api.SitePermissionsManager
import com.duckduckgo.sync.api.DeviceSyncState
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import logcat.LogPriority.INFO
import logcat.LogPriority.WARN
import logcat.logcat
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume

sealed class ClearDataResult {
    data object Success : ClearDataResult()
    data object FeatureNotSupported : ClearDataResult()
    data class Error(val exception: Exception) : ClearDataResult()
}

interface ClearDataAction {
    /**
     * Clears tabs and all browser data (legacy full clear).
     * @param appInForeground whether the app is in foreground
     * @param shouldFireDataClearPixel whether to fire the data clear pixel
     */
    suspend fun clearTabsAndAllDataAsync(
        appInForeground: Boolean,
        shouldFireDataClearPixel: Boolean,
    ): Unit?

    /**
     * Clears tabs and associated data.
     * @param appInForeground whether the app is in foreground
     */
    suspend fun clearTabsAsync(appInForeground: Boolean)

    /**
     * Clears tabs and associated data.
     */
    suspend fun clearTabsOnly()

    /**
     * Clears browser data except tabs and chats.
     * @param shouldFireDataClearPixel whether to fire the data clear pixel
     */
    suspend fun clearBrowserDataOnly(shouldFireDataClearPixel: Boolean)

    /**
     * Clears only DuckAi chats.
     */
    suspend fun clearDuckAiChatsOnly()

    /**
     * Clears browsing data for specific domains via WebStorageCompat.
     * Duck.ai domains (duckduckgo.com, duck.ai) are always excluded — their data is managed separately.
     * @param domains set of eTLD+1 domains to clear
     * @return [ClearDataResult.Success] if data was cleared, [ClearDataResult.FeatureNotSupported] if WebView doesn't support this feature,
     *         or [ClearDataResult.Error] if an exception occurred during deletion
     */
    suspend fun clearDataForSpecificDomains(domains: Set<String>): ClearDataResult

    /**
     * Sets the flag indicating whether the app has been used since the last data clear.
     * @param appUsedSinceLastClear true if the app has been used since the last clear, false otherwise
     */
    suspend fun setAppUsedSinceLastClearFlag(appUsedSinceLastClear: Boolean)

    /**
     * Kills the current process.
     */
    fun killProcess()

    /**
     * Kills and restarts the current process.
     * @param notifyDataCleared whether to notify that data has been cleared
     * @param enableTransitionAnimation whether to enable transition animation during restart
     * @param deletedTabCount number of tabs that were deleted (shown in the snackbar)
     */
    fun killAndRestartProcess(notifyDataCleared: Boolean, enableTransitionAnimation: Boolean = true, deletedTabCount: Int = 0)
}

class ClearPersonalDataAction(
    private val context: Context,
    private val dataManager: WebDataManager,
    private val clearingStore: UnsentForgetAllPixelStore,
    private val tabRepository: TabRepository,
    private val settingsDataStore: SettingsDataStore,
    private val cookieManager: DuckDuckGoCookieManager,
    private val appCacheClearer: AppCacheClearer,
    private val thirdPartyCookieManager: ThirdPartyCookieManager,
    private val fireproofWebsiteRepository: FireproofWebsiteRepository,
    private val sitePermissionsManager: SitePermissionsManager,
    private val deviceSyncState: DeviceSyncState,
    private val savedSitesRepository: SavedSitesRepository,
    private val navigationHistory: NavigationHistory,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
    private val webTrackersBlockedRepository: WebTrackersBlockedRepository,
    private val tabVisitedSitesRepository: TabVisitedSitesRepository,
    private val webViewCapabilityChecker: WebViewCapabilityChecker,
    duckAiHostProvider: DuckAiHostProvider,
) : ClearDataAction {

    override fun killAndRestartProcess(notifyDataCleared: Boolean, enableTransitionAnimation: Boolean, deletedTabCount: Int) {
        logcat(INFO) { "Restarting process" }
        FireActivity.triggerRestart(context, notifyDataCleared, enableTransitionAnimation, deletedTabCount)
    }

    override fun killProcess() {
        logcat(INFO) { "Killing process" }
        System.exit(0)
    }

    override suspend fun clearTabsAndAllDataAsync(
        appInForeground: Boolean,
        shouldFireDataClearPixel: Boolean,
    ) {
        withContext(dispatchers.io()) {
            val fireproofDomains = fireproofWebsiteRepository.fireproofWebsitesSync().map { it.domain }
            cookieManager.flush()
            sitePermissionsManager.clearAllButFireproof(fireproofDomains)
            thirdPartyCookieManager.clearAllData()

            // https://app.asana.com/0/69071770703008/1204375817149200/f
            if (!deviceSyncState.isUserSignedInOnDevice()) {
                savedSitesRepository.pruneDeleted()
            }

            clearTabsAsync(appInForeground)

            webTrackersBlockedRepository.deleteAll()

            navigationHistory.clearHistory()
            tabVisitedSitesRepository.clearAll()
        }

        clearDataAsync(shouldFireDataClearPixel)

        logcat(INFO) { "Finished clearing everything" }
    }

    override suspend fun clearTabsAsync(appInForeground: Boolean) {
        withContext(dispatchers.io()) {
            tabRepository.deleteAll()
            setAppUsedSinceLastClearFlag(appInForeground)
            logcat { "Finished clearing tabs" }
        }
    }

    override suspend fun clearTabsOnly() {
        withContext(dispatchers.io()) {
            tabRepository.deleteAll()
            logcat { "Finished clearing tabs" }
        }
    }

    override suspend fun clearBrowserDataOnly(shouldFireDataClearPixel: Boolean) {
        withContext(dispatchers.io()) {
            val fireproofDomains = fireproofWebsiteRepository.fireproofWebsitesSync().map { it.domain }
            cookieManager.flush()
            sitePermissionsManager.clearAllButFireproof(fireproofDomains)
            thirdPartyCookieManager.clearAllData()

            // https://app.asana.com/0/69071770703008/1204375817149200/f
            if (!deviceSyncState.isUserSignedInOnDevice()) {
                savedSitesRepository.pruneDeleted()
            }

            webTrackersBlockedRepository.deleteAll()

            navigationHistory.clearHistory()
            tabVisitedSitesRepository.clearAll()
        }

        clearDataGranularlyAsync(shouldFireDataClearPixel)

        logcat(INFO) { "Finished clearing browser data" }
    }

    override suspend fun clearDuckAiChatsOnly() {
        withContext(dispatchers.main()) {
            dataManager.clearData(
                webView = createWebView(),
                webStorage = createWebStorage(),
                shouldClearBrowserData = false,
                shouldClearDuckAiData = true,
            )

            logcat(INFO) { "Finished clearing chats" }
        }
    }

    @SuppressLint("RequiresFeature")
    override suspend fun clearDataForSpecificDomains(
        domains: Set<String>,
    ): ClearDataResult {
        if (!webViewCapabilityChecker.isSupported(DeleteBrowsingData)) {
            logcat(WARN) { "DeleteBrowsingData feature not supported by WebView" }
            return ClearDataResult.FeatureNotSupported
        }

        return try {
            withContext(dispatchers.main()) {
                val webStorage = createWebStorage()
                domains
                    .filter { !duckDuckGoDomains.contains(it) }
                    .forEach { domain ->
                        suspendCancellableCoroutine { continuation ->
                            WebStorageCompat.deleteBrowsingDataForSite(webStorage, domain) {
                                continuation.resume(Unit)
                            }
                        }
                    }
                logcat(INFO) { "Cleared site data for ${domains.size} domains" }
            }
            ClearDataResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(WARN) { "Failed to clear site data: ${e.message}" }
            ClearDataResult.Error(e)
        }
    }

    private suspend fun clearDataAsync(shouldFireDataClearPixel: Boolean) {
        withContext(dispatchers.main()) {
            if (shouldFireDataClearPixel) {
                clearingStore.incrementCount()
            }

            dataManager.clearData(createWebView(), createWebStorage())
            appCacheClearer.clearCache()

            logcat(INFO) { "Finished clearing data" }
        }
    }

    private suspend fun clearDataGranularlyAsync(shouldFireDataClearPixel: Boolean) {
        withContext(dispatchers.main()) {
            if (shouldFireDataClearPixel) {
                clearingStore.incrementCount()
            }

            dataManager.clearData(
                webView = createWebView(),
                webStorage = createWebStorage(),
                shouldClearBrowserData = true,
                shouldClearDuckAiData = false,
            )
            appCacheClearer.clearCache()

            logcat(INFO) { "Finished clearing data" }
        }
    }

    private fun createWebView(): WebView {
        return WebView(context)
    }

    private fun createWebStorage(): WebStorage {
        return WebStorage.getInstance()
    }

    override suspend fun setAppUsedSinceLastClearFlag(appUsedSinceLastClear: Boolean) {
        withContext(dispatchers.io()) {
            settingsDataStore.appUsedSinceLastClear = appUsedSinceLastClear
            logcat { "Set appUsedSinceClear flag to $appUsedSinceLastClear" }
        }
    }

    private val duckDuckGoDomains: Set<String> = setOf("duckduckgo.com", duckAiHostProvider.getHost())
}
