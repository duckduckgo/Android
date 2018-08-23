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


import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.net.Uri
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.global.model.SiteFactory
import com.duckduckgo.app.tabs.db.TabsDao
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

    override fun add(url: String?): String {
        val tabId = generateTabId()
        add(tabId, buildSiteData(url))
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

    override fun add(tabId: String, data: MutableLiveData<Site>) {
        siteData[tabId] = data
        Schedulers.io().scheduleDirect {
            val position = tabsDao.lastTab()?.position ?: 0
            tabsDao.addAndSelectTab(TabEntity(tabId, data.value?.url, data.value?.title, true, position))
        }
    }

    override fun addNewTabAfterExistingTab(url: String?, tabId: String) {
        Schedulers.io().scheduleDirect {
            val position = tabsDao.tab(tabId)?.position ?: -1
            val uri = Uri.parse(url)
            val host = uri.host
            val title = if (host == null) url else host.removePrefix("www.")
            val tab = TabEntity(generateTabId(), url, title, false, position + 1)
            tabsDao.insertTabAtPosition(tab)
        }
    }

    override fun update(tabId: String, site: Site?) {
        Schedulers.io().scheduleDirect {
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
        Schedulers.io().scheduleDirect {
            tabsDao.deleteTabAndUpdateSelection(tab)
        }
        siteData.remove(tab.tabId)
    }

    override fun deleteAll() {
        Schedulers.io().scheduleDirect {
            tabsDao.deleteAllTabs()
        }
        siteData.clear()
    }

    override fun select(tabId: String) {
        Schedulers.io().scheduleDirect {
            val selection = TabSelectionEntity(tabId = tabId)
            tabsDao.insertTabSelection(selection)
        }
    }
}
