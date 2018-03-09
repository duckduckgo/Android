/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.tabs


import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.support.annotation.WorkerThread
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.global.model.SiteMonitor
import com.duckduckgo.app.privacy.model.TermsOfService
import org.jetbrains.anko.doAsync
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class TabDataRepository @Inject constructor(val tabsDao: TabsDao) {

    val liveTabs: LiveData<List<TabEntity>> = tabsDao.liveTabs()

    val liveSelectedTab: LiveData<SelectedTabEntity> = tabsDao.liveSelectedTab()

    val selectedTab: TabEntity?
        @WorkerThread
        get() {
            tabsDao.selectedTab().tabId?.let {
                return tabsDao.tab(it)
            }
            return null
        }

    private val data: LinkedHashMap<String, MutableLiveData<Site>> = LinkedHashMap()

    fun addNewAndSelect(): String {
        val tabId = UUID.randomUUID().toString()
        add(tabId)
        select(tabId)
        return tabId
    }

    fun add(tabId: String, liveSiteData: MutableLiveData<Site> = MutableLiveData()) {
        this.data[tabId] = liveSiteData
        doAsync {
            tabsDao.insertTab(TabEntity(tabId, liveSiteData.value?.url))
        }
    }

    fun update(tabId: String) {
        val storedData = data[tabId]
        doAsync {
            if (tabsDao.tab(tabId) != null) {
                tabsDao.updateTab(TabEntity(tabId, storedData?.value?.url, storedData?.value?.title))
            } else {
                tabsDao.insertTab(TabEntity(tabId, storedData?.value?.url, storedData?.value?.title))
            }
        }
    }

    fun loadData(tab: TabEntity) {
        val storedData = data[tab.tabId]
        if (storedData != null) {
            return
        }
        val tabData = MutableLiveData<Site>()
        tab.url?.let {
            val monitor = SiteMonitor(it, TermsOfService())
            monitor.title = tab.title
            tabData.value = monitor
            data[tab.tabId] = tabData
        }
    }

    /**
     * Returns record if it exists, otherwise creates and returns a new one
     */
    fun retrieve(tabId: String): MutableLiveData<Site> {
        val storedData = data[tabId]
        if (storedData != null) {
            return storedData
        }

        val data = MutableLiveData<Site>()
        add(tabId, data)
        return data
    }

    fun select(tabId: String) {
        doAsync {
            tabsDao.updateSelectedTab(SelectedTabEntity(SELECTED_ENTITY_ID, tabId))
        }
    }

    companion object {
        const val SELECTED_ENTITY_ID = 1
    }

}
