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

package com.duckduckgo.app.tabs.model

import android.net.Uri
import androidx.annotation.MainThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asFlow
import androidx.lifecycle.distinctUntilChanged
import androidx.webkit.Profile
import androidx.webkit.ProfileStore
import androidx.webkit.WebStorageCompat
import androidx.webkit.WebViewFeature
import com.duckduckgo.adclick.api.AdClickManager
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.browser.session.WebViewSessionStorage
import com.duckduckgo.app.browser.tabpreview.WebViewPreviewPersister
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.fire.FireproofRepository
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.global.model.SiteFactory
import com.duckduckgo.app.tabs.TabManagerFeatureFlags
import com.duckduckgo.app.tabs.db.TabsDao
import com.duckduckgo.app.tabs.model.TabSwitcherData.LayoutType
import com.duckduckgo.app.tabs.model.TabSwitcherData.UserState
import com.duckduckgo.app.tabs.store.TabSwitcherDataStore
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import dagger.SingleInstanceIn
import io.reactivex.Scheduler
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority.INFO
import logcat.LogPriority.WARN
import logcat.logcat
import java.util.UUID
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
class TabDataRepository @Inject constructor(
    private val tabsDao: TabsDao,
    private val siteFactory: SiteFactory,
    private val webViewPreviewPersister: WebViewPreviewPersister,
    private val faviconManager: FaviconManager,
    private val tabSwitcherDataStore: TabSwitcherDataStore,
    private val timeProvider: CurrentTimeProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
    private val adClickManager: AdClickManager,
    private val webViewSessionStorage: WebViewSessionStorage,
    private val tabManagerFeatureFlags: TabManagerFeatureFlags,
    private val fireproofRepository: FireproofRepository,
) : TabRepository {

    private fun webViewMultiProfileSupported() = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
    private fun webViewDeleteBrowsingDataSupported() = WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)

    private val profileStore: ProfileStore? = if (webViewMultiProfileSupported()) {
        ProfileStore.getInstance()
    } else {
        null
    }

    override val liveTabs: LiveData<List<TabEntity>> = tabsDao.liveTabs().distinctUntilChanged()

    override val flowTabs: Flow<List<TabEntity>> = liveTabs.asFlow()

    private val childTabClosedSharedFlow = MutableSharedFlow<String>()

    override val childClosedTabs = childTabClosedSharedFlow.asSharedFlow()

    // We only want the new emissions when subscribing, however Room does not honour that contract so we
    // need to drop the first emission always (this is equivalent to the Observable semantics)
    override val flowDeletableTabs: Flow<List<TabEntity>> = tabsDao.flowDeletableTabs()
        .drop(1)
        .distinctUntilChanged()

    override val liveSelectedTab: LiveData<TabEntity> = tabsDao.liveSelectedTab()

    override val flowSelectedTab: Flow<TabEntity?> = liveSelectedTab.asFlow().distinctUntilChanged()

    override val tabSwitcherData: Flow<TabSwitcherData> = tabSwitcherDataStore.data

    private val siteData: LinkedHashMap<String, MutableLiveData<Site>> = LinkedHashMap()

    private var purgeDeletableTabsJob = ConflatedJob()

    private var tabInsertionFixesFlag: Boolean? = null

    override suspend fun add(
        url: String?,
        skipHome: Boolean,
        isFireTab: Boolean,
    ): String = withContext(dispatchers.io()) {
        val tabId = generateTabId()
        if (isFireTab) {
            withContext(dispatchers.main()) {
                profileStore?.getOrCreateProfile(tabId)
            }
        }
        val flag = getAndCacheTabInsertionFixesFlag()
        val siteData = if (flag) {
            buildSiteData(url, tabId)
        } else {
            buildSiteDataSync(url, tabId)
        }
        add(tabId, siteData, skipHome = skipHome, isDefaultTab = false, updateIfBlankParent = flag)

        return@withContext tabId
    }

    override suspend fun addFromSourceTab(
        url: String?,
        skipHome: Boolean,
        sourceTabId: String,
        isFireTab: Boolean,
    ): String = withContext(dispatchers.io()) {
        val tabId = generateTabId()
        if (isFireTab) {
            profileStore?.getOrCreateProfile(tabId)
        }
        val flag = getAndCacheTabInsertionFixesFlag()
        val siteData = if (flag) {
            buildSiteData(url, tabId)
        } else {
            buildSiteDataSync(url, tabId)
        }
        add(
            tabId = tabId,
            data = siteData,
            skipHome = skipHome,
            isDefaultTab = false,
            sourceTabId = sourceTabId,
            updateIfBlankParent = flag,
        )

        return@withContext tabId
    }

    override suspend fun addDefaultTab(): String = withContext(dispatchers.io()) {
        val tabId = generateTabId()
        val flag = getAndCacheTabInsertionFixesFlag()
        val siteData = if (flag) {
            buildSiteData(url = null, tabId = tabId)
        } else {
            buildSiteDataSync(url = null, tabId)
        }
        add(
            tabId = tabId,
            data = siteData,
            skipHome = false,
            isDefaultTab = true,
            updateIfBlankParent = flag,
        )

        return@withContext tabId
    }

    private suspend fun getAndCacheTabInsertionFixesFlag(): Boolean {
        return tabInsertionFixesFlag ?: withContext(dispatchers.io()) {
            tabManagerFeatureFlags.tabInsertionFixes().isEnabled()
        }.also { tabInsertionFixesFlag = it }
    }

    private fun generateTabId() = UUID.randomUUID().toString()

    private fun buildSiteDataSync(url: String?, tabId: String): MutableLiveData<Site> {
        val data = MutableLiveData<Site>()
        url?.let {
            val siteMonitor = siteFactory.buildSite(url = it, tabId = tabId)
            data.postValue(siteMonitor)
        }
        return data
    }

    private suspend fun buildSiteData(url: String?, tabId: String): MutableLiveData<Site> {
        val data = MutableLiveData<Site>()
        url ?: return data
        val siteMonitor = siteFactory.buildSite(url = url, tabId = tabId)
        withContext(dispatchers.main()) {
            data.value = siteMonitor
        }
        return data
    }

    private fun add(
        tabId: String,
        data: MutableLiveData<Site>,
        skipHome: Boolean,
        isDefaultTab: Boolean,
        sourceTabId: String? = null,
        updateIfBlankParent: Boolean = false,
    ) {
        siteData[tabId] = data
        databaseExecutor().scheduleDirect {
            logcat(INFO) { "Trying to add tab, is default? $isDefaultTab, current tabs count: ${tabsDao.tabs().size}" }

            if (isDefaultTab && tabsDao.tabs().isNotEmpty()) {
                logcat(INFO) { "Default tab being added but there are already tabs; will not add this tab" }
                return@scheduleDirect
            }

            val lastTab = tabsDao.lastTab()
            val position = if (lastTab == null) {
                0
            } else {
                lastTab.position + 1
            }
            logcat(INFO) { "About to add a new tab, isDefaultTab: $isDefaultTab. $tabId, position: $position" }

            tabsDao.addAndSelectTab(
                TabEntity(
                    tabId = tabId,
                    url = data.value?.url,
                    title = data.value?.title,
                    skipHome = skipHome,
                    viewed = true,
                    position = position,
                    sourceTabId = sourceTabId,
                ),
                updateIfBlankParent = updateIfBlankParent,
            )
        }
    }

    override suspend fun selectByUrlOrNewTab(url: String) {
        val tabId = tabsDao.selectTabByUrl(url)
        if (tabId != null) {
            select(tabId)
        } else {
            add(url, skipHome = true)
        }
    }

    override suspend fun getTabId(url: String): String? = tabsDao.selectTabByUrl(url)

    override suspend fun setIsUserNew(isUserNew: Boolean) {
        if (tabSwitcherDataStore.data.first().userState == UserState.UNKNOWN) {
            val userState = if (isUserNew) UserState.NEW else UserState.EXISTING
            tabSwitcherDataStore.setUserState(userState)
        }
    }

    override suspend fun setTabLayoutType(layoutType: LayoutType) {
        tabSwitcherDataStore.setTabLayoutType(layoutType)
    }

    override suspend fun getTabs() = withContext(dispatchers.io()) {
        tabsDao.tabs()
    }

    override fun getOpenTabCount(): Int {
        return tabsDao.tabs().size
    }

    override fun countTabsAccessedWithinRange(accessOlderThan: Long, accessNotMoreThan: Long?): Int {
        val now = timeProvider.localDateTimeNow()
        val start = now.minusDays(accessOlderThan)
        val end = accessNotMoreThan?.let { now.minusDays(it).minusSeconds(1) } // subtracted a second to make the end limit inclusive
        return tabsDao.tabs().filter {
            it.lastAccessTime?.isBefore(start) == true && (end == null || it.lastAccessTime?.isAfter(end) == true)
        }.size
    }

    override suspend fun addNewTabAfterExistingTab(
        url: String?,
        tabId: String,
        isFireTab: Boolean,
    ) {
        val newTabId = generateTabId()
        if (isFireTab) {
            profileStore?.getOrCreateProfile(newTabId)
        }
        databaseExecutor().scheduleDirect {
            val position = tabsDao.tab(tabId)?.position ?: -1
            val uri = Uri.parse(url)
            val title = uri.host?.removePrefix("www.") ?: url
            val tab = TabEntity(
                tabId = newTabId,
                url = url,
                title = title,
                skipHome = false,
                viewed = false,
                position = position + 1,
                sourceTabId = tabId,
            )
            tabsDao.insertTabAtPosition(tab)
        }
    }

    override suspend fun update(
        tabId: String,
        site: Site?,
    ) {
        databaseExecutor().scheduleDirect {
            tabsDao.updateUrlAndTitle(tabId, site?.url, site?.title, viewed = true)
        }
    }

    override suspend fun updateTabPosition(from: Int, to: Int) {
        databaseExecutor().scheduleDirect {
            tabsDao.updateTabsOrder(from, to)
        }
    }

    override suspend fun updateTabLastAccess(tabId: String) {
        databaseExecutor().scheduleDirect {
            tabsDao.updateTabLastAccess(tabId, timeProvider.localDateTimeNow())
        }
    }

    override fun retrieveSiteData(tabId: String): MutableLiveData<Site> {
        val storedData = siteData[tabId]
        if (storedData != null) {
            return storedData
        }

        val data = MutableLiveData<Site>()
        siteData[tabId] = data
        return data
    }

    override suspend fun delete(tab: TabEntity) {
        databaseExecutor().scheduleDirect {
            deleteOldPreviewImages(tab.tabId)
            deleteOldFavicon(tab.tabId)
            tabsDao.deleteTabAndUpdateSelection(tab)
        }
        siteData.remove(tab.tabId)
    }

    override suspend fun deleteTabs(tabIds: List<String>) {
        databaseExecutor().scheduleDirect {
            tabsDao.deleteTabsAndUpdateSelection(tabIds)
            clearAllSiteData(tabIds)
        }
    }

    private fun clearAllSiteData(tabIds: List<String>) {
        tabIds.forEach { tabId ->
            webViewSessionStorage.deleteSession(tabId)
            adClickManager.clearTabId(tabId)
            deleteOldPreviewImages(tabId)
            deleteOldFavicon(tabId)
            siteData.remove(tabId)
            appCoroutineScope.launch(dispatchers.main()) {
                if (webViewDeleteBrowsingDataSupported()) {
                    val profile = profileStore?.getProfile(tabId)
                    if (profile != null) {
                        WebStorageCompat.deleteBrowsingData(profile.webStorage) {
                            logcat { "lp_test; All web storage data removed for tabId: $tabId" }
                        }
                    }
                } else {
                    logcat { "lp_test; DELETE_BROWSING_DATA not supported" }
                }
                // profile.webStorage.deleteAllData()
                // profile.cookieManager.flush()
                // profile.cookieManager.removeAllCookies { removed ->
                //     logcat { "lp_test; All cookies removed for tabId: $tabId : $removed" }
                // }








                // var success = false
                // while (!success) {
                //     try {
                //         logcat { "lp_test; Trying to delete profile for tabId: $tabId" }
                //
                //         if (profileStore.deleteProfile(tabId)) {
                //             logcat { "lp_test; Deleted profile for tabId: $tabId" }
                //         } else {
                //             logcat { "lp_test; Failed to delete profile for tabId: $tabId" }
                //         }
                //         success = true
                //     } catch (ex: Exception) {
                //         logcat(WARN) { "lp_test; Exception deleting profile for tabId: $tabId : $ex" }
                //         delay(1000)
                //         continue
                //     }
                // }
            }
        }
    }

    override suspend fun markDeletable(tab: TabEntity) {
        databaseExecutor().scheduleDirect {
            tabsDao.markTabAsDeletable(tab)
        }
    }

    override suspend fun markDeletable(tabIds: List<String>) {
        databaseExecutor().scheduleDirect {
            tabsDao.markTabsAsDeletable(tabIds)
        }
    }

    override suspend fun undoDeletable(tab: TabEntity) {
        databaseExecutor().scheduleDirect {
            tabsDao.undoDeletableTab(tab)
        }
    }

    override suspend fun undoDeletable(tabIds: List<String>, moveActiveTabToEnd: Boolean) {
        databaseExecutor().scheduleDirect {
            tabsDao.undoDeletableTabs(tabIds, moveActiveTabToEnd)
        }
    }

    override suspend fun purgeDeletableTabs() = withContext(dispatchers.io()) {
        clearAllSiteData(getDeletableTabIds())

        purgeDeletableTabsJob += appCoroutineScope.launch(dispatchers.io()) {
            tabsDao.purgeDeletableTabsAndUpdateSelection()
        }
        purgeDeletableTabsJob.join()
    }

    override suspend fun getDeletableTabIds(): List<String> = withContext(dispatchers.io()) {
        return@withContext tabsDao.getDeletableTabIds()
    }

    override suspend fun deleteTabAndSelectSource(tabId: String) {
        databaseExecutor().scheduleDirect {
            val tabToDelete = tabsDao.tab(tabId) ?: return@scheduleDirect

            deleteOldPreviewImages(tabToDelete.tabId)
            val tabToSelect = tabToDelete.sourceTabId
                .takeUnless { it.isNullOrBlank() }
                ?.let {
                    tabsDao.tab(it)
                }
            tabsDao.deleteTabAndUpdateSelection(tabToDelete, tabToSelect)
            siteData.remove(tabToDelete.tabId)

            tabToSelect?.let {
                appCoroutineScope.launch(dispatchers.io()) {
                    childTabClosedSharedFlow.emit(tabToSelect.tabId)
                }
            }
        }
    }

    override suspend fun deleteAll() {
        logcat(INFO) { "Deleting tabs right now" }
        tabsDao.deleteAllTabs()
        webViewPreviewPersister.deleteAll()
        faviconManager.deleteAllTemp()
        siteData.clear()
    }

    override suspend fun getSelectedTab(): TabEntity? =
        withContext(dispatchers.io()) { tabsDao.selectedTab() }

    override suspend fun select(tabId: String) {
        databaseExecutor().scheduleDirect {
            val selection = TabSelectionEntity(tabId = tabId)
            tabsDao.insertTabSelection(selection)
        }
    }

    override suspend fun getTab(tabId: String): TabEntity? {
        return withContext(dispatchers.io()) { tabsDao.tab(tabId) }
    }

    override fun updateTabFavicon(
        tabId: String,
        fileName: String?,
    ) {
        databaseExecutor().scheduleDirect {
            val tab = tabsDao.tab(tabId)
            if (tab == null) {
                logcat(WARN) { "Cannot find tab for tab ID" }
                return@scheduleDirect
            }
            logcat(INFO) { "Updated tab favicon. $tabId now uses $fileName" }
            deleteOldFavicon(tabId, fileName)
        }
    }

    override fun updateTabPreviewImage(
        tabId: String,
        fileName: String?,
    ) {
        databaseExecutor().scheduleDirect {
            val tab = tabsDao.tab(tabId)
            if (tab == null) {
                logcat(WARN) { "Cannot find tab for tab ID" }
                return@scheduleDirect
            }
            tabsDao.updateTab(tab.copy(tabPreviewFile = fileName))

            logcat(INFO) { "Updated tab preview image. $tabId now uses $fileName" }
            deleteOldPreviewImages(tabId, fileName)
        }
    }

    private fun deleteOldFavicon(
        tabId: String,
        currentFavicon: String? = null,
    ) {
        logcat(INFO) { "Deleting old favicon for $tabId. Current favicon is $currentFavicon" }
        appCoroutineScope.launch(dispatchers.io()) { faviconManager.deleteOldTempFavicon(tabId, currentFavicon) }
    }

    private fun deleteOldPreviewImages(
        tabId: String,
        currentPreviewImage: String? = null,
    ) {
        logcat(INFO) { "Deleting old preview image for $tabId. Current image is $currentPreviewImage" }
        appCoroutineScope.launch(dispatchers.io()) { webViewPreviewPersister.deletePreviewsForTab(tabId, currentPreviewImage) }
    }

    /**
     * In this class we typically delegate DB work to a scheduler
     * Historically, this was the IO scheduler, which can use multiple threads to do the work
     * However, this presented the possibility of race conditions. One such case was when the blank tab was shown to the user and the URL
     * they actually wanted was opened in a background tab.
     *
     * While there are likely even better ways of doing this, moving to a single-threaded executor is likely good enough to fix this for now
     */
    private fun databaseExecutor(): Scheduler {
        return Schedulers.single()
    }

    override fun getTabProfileName(tabId: String): String? {
        return try {
            // profileStore.getProfile(tabId)?.name
            if (profileStore?.allProfileNames?.contains(tabId) == true) {
                tabId
            } else {
                null
            }
        } catch (ex: Exception) {
            logcat { "lp_test; getTabProfileName $ex" }
            null
        }
    }

    override fun isFireTab(tabId: String): Boolean {
        return getTabProfileName(tabId) != null
    }

    override fun clearStaleProfiles() {
        appCoroutineScope.launch(dispatchers.main()) {
            logcat { "lp_test; clearStaleProfiles" }
            val tabIds = withContext(dispatchers.io()) {
                tabsDao.tabs().map { it.tabId }
            }
            logcat { "lp_test; tabIds: $tabIds" }
            val profileNames = profileStore?.allProfileNames
            logcat { "lp_test; profileNames: $profileNames" }
            profileNames?.filterNot { it == Profile.DEFAULT_PROFILE_NAME }?.forEach { profileName ->
                if (!tabIds.contains(profileName)) {
                    logcat { "lp_test; deleting: $profileName" }
                    profileStore?.deleteProfile(profileName)
                }
            }
        }
    }

    override fun deleteBrowsingDataForTab(
        tabId: String,
        sites: Set<String>,
    ) {
        appCoroutineScope.launch(dispatchers.main()) {
            if (webViewDeleteBrowsingDataSupported()) {
                val profileName = getTabProfileName(tabId) ?: Profile.DEFAULT_PROFILE_NAME
                val profile = profileStore?.getProfile(profileName)
                if (profile != null) {
                    sites.forEach { site ->
                        WebStorageCompat.deleteBrowsingDataForSite(
                            profile.webStorage,
                            site,
                        ) {
                            logcat { "lp_test; Data removed for profile: $profileName; site: $site" }
                        }
                    }
                }
            } else {
                logcat { "lp_test; deleteBrowsingDataForTab; DELETE_BROWSING_DATA not supported" }
            }
        }
    }

    override fun deleteBrowsingData() {
        appCoroutineScope.launch(dispatchers.main()) {
            profileStore?.allProfileNames?.forEach { profileName ->
                val storage = profileStore.getProfile(profileName)?.webStorage
                if (storage != null) {
                    WebStorageCompat.deleteBrowsingData(storage) {
                        logcat {
                            "lp_test; All web storage data removed for profile: $profileName"
                        }
                    }
                }
            }
        }
    }

    private var defaultProfileName = "Default"

    override fun getDefaultProfileName(): String {
        return defaultProfileName
    }

    override suspend fun createNewDefaultProfile() = withContext(dispatchers.main()) {
        val oldProfile = profileStore?.getProfile(defaultProfileName)
        defaultProfileName = "ddg_default"
        val newProfile = profileStore?.getOrCreateProfile(defaultProfileName)
        val websites = withContext(dispatchers.io()) { fireproofRepository.fireproofWebsites() }
        websites.forEach { website ->
            oldProfile?.cookieManager?.getCookie(website)?.split("; ")?.forEach { cookie ->
                logcat { "lp_test; Setting cookie from '${oldProfile?.name}' to '${newProfile?.name}' for $website: $cookie" }
                newProfile?.cookieManager?.setCookie(website, cookie) {
                    logcat { "lp_test; Cookie set result: $it" }
                }
            }
        }
    }
}
