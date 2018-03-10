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
import com.duckduckgo.app.global.model.SiteFactory
import io.reactivex.schedulers.Schedulers
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class TabDataRepository @Inject constructor(val tabsDao: TabsDao, val siteFactory: SiteFactory) {

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

    private val siteData: LinkedHashMap<String, MutableLiveData<Site>> = LinkedHashMap()

    fun addNew(): String {
        val tabId = UUID.randomUUID().toString()
        add(tabId, MutableLiveData<Site>())
        return tabId
    }

    fun add(tabId: String, data: MutableLiveData<Site>) {
        siteData[tabId] = data
        Schedulers.io().scheduleDirect {
            tabsDao.insertTab(TabEntity(tabId, data.value?.url, data.value?.title))
        }
    }

    fun update(tabId: String, site: Site?) {
        Schedulers.io().scheduleDirect {
            if (tabsDao.tab(tabId) != null) {
                tabsDao.updateTab(TabEntity(tabId, site?.url, site?.title))
            } else {
                tabsDao.insertTab(TabEntity(tabId, site?.url, site?.title))
            }
        }
    }

    fun loadData(tab: TabEntity) {
        val storedData = siteData[tab.tabId]
        if (storedData != null) {
            return
        }
        val tabData = MutableLiveData<Site>()
        val url = tab.url
        if (url != null) {
            val monitor = siteFactory.build(url, tab.title)
            tabData.value = monitor
        }
        siteData[tab.tabId] = tabData
    }

    /**
     * Returns record if it exists, otherwise creates and returns a new one
     */
    fun retrieve(tabId: String): MutableLiveData<Site> {
        val storedData = siteData[tabId]
        if (storedData != null) {
            return storedData
        }

        val data = MutableLiveData<Site>()
        add(tabId, data)
        return data
    }

    fun delete(tab: TabEntity) {
        Schedulers.io().scheduleDirect {
            tabsDao.deleteTab(tab)
        }
        siteData.remove(tab.tabId)
        //TODO delete fragment too
    }

    fun select(tabId: String) {
        Schedulers.io().scheduleDirect {
            tabsDao.updateSelectedTab(SelectedTabEntity(SELECTED_ENTITY_ID, tabId))
        }
    }

    companion object {
        const val SELECTED_ENTITY_ID = 1
    }

}
