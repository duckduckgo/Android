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
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.global.model.SiteFactory
import com.duckduckgo.app.tabs.db.TabsDao
import io.reactivex.schedulers.Schedulers
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class TabDataRepository @Inject constructor(val tabsDao: TabsDao, val siteFactory: SiteFactory) {

    val liveTabs: LiveData<List<TabEntity>> = tabsDao.liveTabs()

    val liveSelectedTab: LiveData<TabEntity> = tabsDao.liveSelectedTab()

    private val siteData: LinkedHashMap<String, MutableLiveData<Site>> = LinkedHashMap()

    fun addNew(): String {
        val tabId = UUID.randomUUID().toString()
        add(tabId, MutableLiveData())
        return tabId
    }

    fun add(tabId: String, data: MutableLiveData<Site>) {
        siteData[tabId] = data
        Schedulers.io().scheduleDirect {
            tabsDao.addAndSelectTab(TabEntity(tabId, data.value?.url, data.value?.title))
        }
    }

    fun update(tabId: String, site: Site?) {
        Schedulers.io().scheduleDirect {
            val tab = TabEntity(tabId, site?.url, site?.title)
            tabsDao.updateTab(tab)
        }
    }

    /**
     * Returns record if it exists, otherwise creates and returns a new one
     */
    fun retrieveSiteData(tabId: String): MutableLiveData<Site> {
        val storedData = siteData[tabId]
        if (storedData != null) {
            return storedData
        }

        val data = MutableLiveData<Site>()
        siteData[tabId] = data
        return data
    }


    fun delete(tab: TabEntity) {
        Schedulers.io().scheduleDirect {
            tabsDao.deleteTabAndUpdateSelection(tab)
        }
        siteData.remove(tab.tabId)
    }

    fun deleteAll() {
        Schedulers.newThread().scheduleDirect {
            tabsDao.deleteAllTabs()
        }
        siteData.clear()
    }

    fun select(tabId: String) {
        Schedulers.io().scheduleDirect {
            val selection = TabSelectionEntity(tabId = tabId)
            tabsDao.insertTabSelection(selection)
        }
    }
}
