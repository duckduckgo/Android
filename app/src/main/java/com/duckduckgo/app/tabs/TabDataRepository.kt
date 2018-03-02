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


import android.arch.lifecycle.MutableLiveData
import com.duckduckgo.app.global.model.Site
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class TabDataRepository @Inject constructor() {

    @Suppress("JoinDeclarationAndAssignment")
    val tabs: MutableLiveData<Tabs> = MutableLiveData()

    val currentTabId: String?
        get() = tabs.value!!.currentId

    init {
        tabs.postValue(Tabs())
    }

    fun hasTabs(): Boolean {
        return tabs.value!!.list.isNotEmpty()
    }

    fun add(tabId: String, data: MutableLiveData<Site>) {
        tabs.value!!.list[tabId] = data
    }

    /**
     * Returns existing record if it exists, otherwise creates and returns a new one
     */
    fun retrieve(tabId: String): MutableLiveData<Site> {
        val data = tabs.value!!.list[tabId]
        if (data == null) {
            val site = MutableLiveData<Site>()
            add(tabId, site)
            return site
        }
        return data
    }

    fun select(tabId: String) {
        tabs.value!!.currentId = tabId
    }

    data class Tabs(
        var currentId: String? = null,
        val list: LinkedHashMap<String, MutableLiveData<Site>> = LinkedHashMap()

    )
}
