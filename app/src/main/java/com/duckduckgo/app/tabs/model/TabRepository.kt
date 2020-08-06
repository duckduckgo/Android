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

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.duckduckgo.app.global.model.Site

interface TabRepository {

    val liveTabs: LiveData<List<TabEntity>>

    val liveSelectedTab: LiveData<TabEntity>

    /**
     * @return tabId of new record
     */
    suspend fun add(url: String? = null, skipHome: Boolean = false, isDefaultTab: Boolean = false): String

    suspend fun addFromSourceTab(url: String? = null, skipHome: Boolean = false, isDefaultTab: Boolean= false, sourceTabId: String?): String

    suspend fun addNewTabAfterExistingTab(url: String? = null, tabId: String)

    suspend fun update(tabId: String, site: Site?)

    /**
     * @return record if it exists, otherwise a new one
     */
    fun retrieveSiteData(tabId: String): MutableLiveData<Site>

    suspend fun delete(tab: TabEntity)

    suspend fun deleteCurrentTabAndSelectSource()

    fun deleteAll()

    suspend fun select(tabId: String)

    fun updateTabPreviewImage(tabId: String, fileName: String?)

    suspend fun selectByUrlOrNewTab(url: String)
}
