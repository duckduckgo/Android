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
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.global.model.SiteFactory
import com.duckduckgo.app.tabs.db.TabsDao
import io.reactivex.Scheduler
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TabDataRepository @Inject constructor(private val tabsDao: TabsDao, private val siteFactory: SiteFactory) : TabRepository {

    override val liveTabs: LiveData<List<TabEntity>> = tabsDao.liveTabs()

    override val liveSelectedTab: LiveData<TabEntity> = tabsDao.liveSelectedTab()

    private val siteData: LinkedHashMap<String, MutableLiveData<Site>> = LinkedHashMap()

    override fun add(url: String?, isDefaultTab: Boolean): String {
        val tabId = generateTabId()
        add(tabId, buildSiteData(url), isDefaultTab = isDefaultTab)
        return tabId
    }

    private fun generateTabId() = UUID.randomUUID().toString()

    private fun buildSiteData(url: String?): MutableLiveData<Site> {
        val data = MutableLiveData<Site>()
        url?.let {
            val siteMonitor = siteFactory.build(it)
            data.value = siteMonitor
        }
        return data
    }

    override fun add(tabId: String, data: MutableLiveData<Site>, isDefaultTab: Boolean) {
        siteData[tabId] = data
        databaseExecutor().scheduleDirect {

            Timber.i("Trying to add tab, is default? $isDefaultTab, current tabs count: ${tabsDao.tabs().size}")

            if (isDefaultTab && tabsDao.tabs().isNotEmpty()) {
                Timber.i("Default tab being added but there are already tabs; will not add this tab")
                return@scheduleDirect
            }

            Timber.i("About to add a new tab, isDefaultTab: $isDefaultTab. $tabId")
            val position = tabsDao.lastTab()?.position ?: 0
            tabsDao.addAndSelectTab(TabEntity(tabId, data.value?.url, data.value?.title, true, position))
        }
    }

    override fun addNewTabAfterExistingTab(url: String?, tabId: String) {
        databaseExecutor().scheduleDirect {
            val position = tabsDao.tab(tabId)?.position ?: -1
            val uri = Uri.parse(url)
            val title = uri.host?.removePrefix("www.") ?: url
            val tab = TabEntity(generateTabId(), url, title, false, position + 1)
            tabsDao.insertTabAtPosition(tab)
        }
    }

    override fun update(tabId: String, site: Site?) {
        databaseExecutor().scheduleDirect {
            val position = tabsDao.tab(tabId)?.position ?: 0
            val tab = TabEntity(tabId, site?.url, site?.title, true, position)
            tabsDao.updateTab(tab)
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

    override fun delete(tab: TabEntity) {
        databaseExecutor().scheduleDirect {
            tabsDao.deleteTabAndUpdateSelection(tab)
        }
        siteData.remove(tab.tabId)
    }

    override fun deleteAll() {
        Timber.i("Deleting tabs right now")
        tabsDao.deleteAllTabs()
        siteData.clear()
    }

    override fun select(tabId: String) {
        databaseExecutor().scheduleDirect {
            val selection = TabSelectionEntity(tabId = tabId)
            tabsDao.insertTabSelection(selection)
        }
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
