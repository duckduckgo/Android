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
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.browser.tabpreview.WebViewPreviewPersister
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.global.model.SiteFactory
import com.duckduckgo.app.tabs.db.TabsDao
import com.duckduckgo.di.scopes.AppScope
import dagger.SingleInstanceIn
import io.reactivex.Scheduler
import io.reactivex.schedulers.Schedulers
import java.util.*
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@SingleInstanceIn(AppScope::class)
class TabDataRepository
@Inject
constructor(
    private val tabsDao: TabsDao,
    private val siteFactory: SiteFactory,
    private val webViewPreviewPersister: WebViewPreviewPersister,
    private val faviconManager: FaviconManager,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope
) : TabRepository {

    override val liveTabs: LiveData<List<TabEntity>> = tabsDao.liveTabs()

    override val flowTabs: Flow<List<TabEntity>> = tabsDao.flowTabs()

    private val childTabClosedSharedFlow = MutableSharedFlow<String>()

    override val childClosedTabs = childTabClosedSharedFlow.asSharedFlow()

    // We only want the new emissions when subscribing, however Room does not honour that contract
    // so we
    // need to drop the first emission always (this is equivalent to the Observable semantics)
    override val flowDeletableTabs: Flow<List<TabEntity>> =
        tabsDao.flowDeletableTabs().drop(1).distinctUntilChanged()

    override val liveSelectedTab: LiveData<TabEntity> = tabsDao.liveSelectedTab()

    private val siteData: LinkedHashMap<String, MutableLiveData<Site>> = LinkedHashMap()

    override suspend fun add(url: String?, skipHome: Boolean): String {
        val tabId = generateTabId()
        add(tabId, buildSiteData(url), skipHome = skipHome, isDefaultTab = false)
        return tabId
    }

    override suspend fun addFromSourceTab(
        url: String?,
        skipHome: Boolean,
        sourceTabId: String
    ): String {
        val tabId = generateTabId()

        add(
            tabId = tabId,
            data = buildSiteData(url),
            skipHome = skipHome,
            isDefaultTab = false,
            sourceTabId = sourceTabId)

        return tabId
    }

    override suspend fun addDefaultTab(): String {
        val tabId = generateTabId()

        add(tabId = tabId, data = buildSiteData(null), skipHome = false, isDefaultTab = true)

        return tabId
    }

    private fun generateTabId() = UUID.randomUUID().toString()

    private fun buildSiteData(url: String?): MutableLiveData<Site> {
        val data = MutableLiveData<Site>()
        url?.let {
            val siteMonitor = siteFactory.buildSite(it)
            data.postValue(siteMonitor)
        }
        return data
    }

    private fun add(
        tabId: String,
        data: MutableLiveData<Site>,
        skipHome: Boolean,
        isDefaultTab: Boolean,
        sourceTabId: String? = null
    ) {
        siteData[tabId] = data
        databaseExecutor().scheduleDirect {
            Timber.i(
                "Trying to add tab, is default? $isDefaultTab, current tabs count: ${tabsDao.tabs().size}")

            if (isDefaultTab && tabsDao.tabs().isNotEmpty()) {
                Timber.i(
                    "Default tab being added but there are already tabs; will not add this tab")
                return@scheduleDirect
            }

            val lastTab = tabsDao.lastTab()
            val position =
                if (lastTab == null) {
                    0
                } else {
                    lastTab.position + 1
                }
            Timber.i(
                "About to add a new tab, isDefaultTab: $isDefaultTab. $tabId, position: $position")

            tabsDao.addAndSelectTab(
                TabEntity(
                    tabId = tabId,
                    url = data.value?.url,
                    title = data.value?.title,
                    skipHome = skipHome,
                    viewed = true,
                    position = position,
                    sourceTabId = sourceTabId))
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

    override suspend fun addNewTabAfterExistingTab(url: String?, tabId: String) {
        databaseExecutor().scheduleDirect {
            val position = tabsDao.tab(tabId)?.position ?: -1
            val uri = Uri.parse(url)
            val title = uri.host?.removePrefix("www.") ?: url
            val tab =
                TabEntity(
                    tabId = generateTabId(),
                    url = url,
                    title = title,
                    skipHome = false,
                    viewed = false,
                    position = position + 1,
                    sourceTabId = tabId)
            tabsDao.insertTabAtPosition(tab)
        }
    }

    override suspend fun update(tabId: String, site: Site?) {
        databaseExecutor().scheduleDirect {
            tabsDao.updateUrlAndTitle(tabId, site?.url, site?.title, viewed = true)
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

    override suspend fun markDeletable(tab: TabEntity) {
        databaseExecutor().scheduleDirect { tabsDao.markTabAsDeletable(tab) }
    }

    override suspend fun undoDeletable(tab: TabEntity) {
        databaseExecutor().scheduleDirect { tabsDao.undoDeletableTab(tab) }
    }

    override suspend fun purgeDeletableTabs() =
        withContext(Dispatchers.IO) {
            appCoroutineScope.launch { tabsDao.purgeDeletableTabsAndUpdateSelection() }.join()
        }

    override suspend fun deleteTabAndSelectSource(tabId: String) {
        databaseExecutor().scheduleDirect {
            val tabToDelete = tabsDao.tab(tabId) ?: return@scheduleDirect

            deleteOldPreviewImages(tabToDelete.tabId)
            val tabToSelect =
                tabToDelete.sourceTabId.takeUnless { it.isNullOrBlank() }?.let { tabsDao.tab(it) }
            tabsDao.deleteTabAndUpdateSelection(tabToDelete, tabToSelect)
            siteData.remove(tabToDelete.tabId)

            tabToSelect?.let {
                appCoroutineScope.launch { childTabClosedSharedFlow.emit(tabToSelect.tabId) }
            }
        }
    }

    override suspend fun deleteAll() {
        Timber.i("Deleting tabs right now")
        tabsDao.deleteAllTabs()
        webViewPreviewPersister.deleteAll()
        faviconManager.deleteAllTemp()
        siteData.clear()
    }

    override suspend fun select(tabId: String) {
        databaseExecutor().scheduleDirect {
            val selection = TabSelectionEntity(tabId = tabId)
            tabsDao.insertTabSelection(selection)
        }
    }

    override fun updateTabFavicon(tabId: String, fileName: String?) {
        databaseExecutor().scheduleDirect {
            val tab = tabsDao.tab(tabId)
            if (tab == null) {
                Timber.w("Cannot find tab for tab ID")
                return@scheduleDirect
            }
            Timber.i("Updated tab favicon. $tabId now uses $fileName")
            deleteOldFavicon(tabId, fileName)
        }
    }

    override fun updateTabPreviewImage(tabId: String, fileName: String?) {
        databaseExecutor().scheduleDirect {
            val tab = tabsDao.tab(tabId)
            if (tab == null) {
                Timber.w("Cannot find tab for tab ID")
                return@scheduleDirect
            }
            tab.tabPreviewFile = fileName
            tabsDao.updateTab(tab)

            Timber.i("Updated tab preview image. $tabId now uses $fileName")
            deleteOldPreviewImages(tabId, fileName)
        }
    }

    private fun deleteOldFavicon(tabId: String, currentFavicon: String? = null) {
        Timber.i("Deleting old favicon for $tabId. Current favicon is $currentFavicon")
        appCoroutineScope.launch { faviconManager.deleteOldTempFavicon(tabId, currentFavicon) }
    }

    private fun deleteOldPreviewImages(tabId: String, currentPreviewImage: String? = null) {
        Timber.i("Deleting old preview image for $tabId. Current image is $currentPreviewImage")
        appCoroutineScope.launch {
            webViewPreviewPersister.deletePreviewsForTab(tabId, currentPreviewImage)
        }
    }

    /**
     * In this class we typically delegate DB work to a scheduler Historically, this was the IO
     * scheduler, which can use multiple threads to do the work However, this presented the
     * possibility of race conditions. One such case was when the blank tab was shown to the user
     * and the URL they actually wanted was opened in a background tab.
     *
     * While there are likely even better ways of doing this, moving to a single-threaded executor
     * is likely good enough to fix this for now
     */
    private fun databaseExecutor(): Scheduler {
        return Schedulers.single()
    }
}
