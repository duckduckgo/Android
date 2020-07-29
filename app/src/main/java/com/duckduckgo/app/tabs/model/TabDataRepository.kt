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
import com.duckduckgo.app.browser.tabpreview.WebViewPreviewPersister
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.global.model.SiteFactory
import com.duckduckgo.app.global.useourapp.UseOurAppDetector
import com.duckduckgo.app.tabs.db.TabsDao
import io.reactivex.Scheduler
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TabDataRepository @Inject constructor(
    private val tabsDao: TabsDao,
    private val siteFactory: SiteFactory,
    private val webViewPreviewPersister: WebViewPreviewPersister,
    private val useOurAppDetector: UseOurAppDetector
) : TabRepository {

    override val liveTabs: LiveData<List<TabEntity>> = tabsDao.liveTabs()

    override val liveSelectedTab: LiveData<TabEntity> = tabsDao.liveSelectedTab()

    private val siteData: LinkedHashMap<String, MutableLiveData<Site>> = LinkedHashMap()

    override suspend fun add(url: String?, skipHome: Boolean, isDefaultTab: Boolean): String {
        val tabId = generateTabId()
        add(tabId, buildSiteData(url), skipHome = skipHome, isDefaultTab = isDefaultTab)
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

    override suspend fun add(tabId: String, data: MutableLiveData<Site>, skipHome: Boolean, isDefaultTab: Boolean) {
        siteData[tabId] = data
        databaseExecutor().scheduleDirect {

            Timber.i("Trying to add tab, is default? $isDefaultTab, current tabs count: ${tabsDao.tabs().size}")

            if (isDefaultTab && tabsDao.tabs().isNotEmpty()) {
                Timber.i("Default tab being added but there are already tabs; will not add this tab")
                return@scheduleDirect
            }

            val lastTab = tabsDao.lastTab()
            val position = if (lastTab == null) {
                0
            } else {
                lastTab.position + 1
            }
            Timber.i("About to add a new tab, isDefaultTab: $isDefaultTab. $tabId, position: $position")

            tabsDao.addAndSelectTab(TabEntity(tabId, data.value?.url, data.value?.title, skipHome, true, position))
        }
    }

    override suspend fun selectByUrlOrNewTab(url: String) {
        var searchUrl = url
        if (useOurAppDetector.isUseOurAppUrl(url)) {
            searchUrl = "%${UseOurAppDetector.USE_OUR_APP_DOMAIN}%"
        }

        val tabId = tabsDao.selectTabByUrl(searchUrl)
        if (tabId != null) {
            select(tabId)
        } else {
            add(url, skipHome = true, isDefaultTab = false)
        }
    }

    override suspend fun addNewTabAfterExistingTab(url: String?, tabId: String) {
        databaseExecutor().scheduleDirect {
            val position = tabsDao.tab(tabId)?.position ?: -1
            val uri = Uri.parse(url)
            val title = uri.host?.removePrefix("www.") ?: url
            val tab = TabEntity(
                tabId = generateTabId(),
                url = url,
                title = title,
                skipHome = false,
                viewed = false,
                position = position + 1
            )
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

            tabsDao.deleteTabAndUpdateSelection(tab)
        }
        siteData.remove(tab.tabId)
    }

    override fun deleteAll() {
        Timber.i("Deleting tabs right now")
        tabsDao.deleteAllTabs()
        GlobalScope.launch { webViewPreviewPersister.deleteAll() }
        siteData.clear()
    }

    override suspend fun select(tabId: String) {
        databaseExecutor().scheduleDirect {
            val selection = TabSelectionEntity(tabId = tabId)
            tabsDao.insertTabSelection(selection)
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

    private fun deleteOldPreviewImages(tabId: String, currentPreviewImage: String? = null) {
        Timber.i("Deleting old preview image for $tabId. Current image is $currentPreviewImage")
        GlobalScope.launch { webViewPreviewPersister.deletePreviewsForTab(tabId, currentPreviewImage) }
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
}
