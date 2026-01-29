/*
 * Copyright (c) 2026 DuckDuckGo
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

import androidx.lifecycle.asFlow
import com.duckduckgo.app.tabs.db.TabGroupsDao
import com.duckduckgo.app.tabs.db.TabsDao
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

/**
 * Data class representing a tab group with its associated tabs.
 */
data class TabGroupWithTabs(
    val group: TabGroupEntity,
    val tabs: List<TabEntity>,
) {
    val previewTabs: List<TabEntity>
        get() = tabs.take(4)

    val additionalTabsCount: Int
        get() = (tabs.size - 4).coerceAtLeast(0)
}

/**
 * Data class representing all tabs organized by groups.
 */
data class TabsWithGroups(
    val groups: List<TabGroupWithTabs>,
    val ungroupedTabs: List<TabEntity>,
)

/**
 * Delegate responsible for combining tabs and groups data for display purposes.
 */
interface TabGroupsDelegate {

    /**
     * Returns a flow of tabs organized by groups.
     * Groups contain their associated tabs, and ungrouped tabs are returned separately.
     */
    fun getTabsWithGroups(): Flow<TabsWithGroups>

    /**
     * Returns all tab groups synchronously.
     */
    fun getGroupsSync(): List<TabGroupEntity>

    /**
     * Returns tabs for a specific group synchronously.
     */
    fun getTabsInGroupSync(groupId: String): List<TabEntity>
}

@ContributesBinding(AppScope::class)
class RealTabGroupsDelegate @Inject constructor(
    private val tabsDao: TabsDao,
    private val tabGroupsDao: TabGroupsDao,
    private val dispatcherProvider: DispatcherProvider,
) : TabGroupsDelegate {

    override fun getTabsWithGroups(): Flow<TabsWithGroups> {
        return combine(
            tabsDao.liveTabs().asFlow(),
            tabGroupsDao.flowAllGroups(),
        ) { tabs, groups ->
            mapToTabsWithGroups(tabs, groups)
        }.flowOn(dispatcherProvider.io())
    }

    private fun mapToTabsWithGroups(
        tabs: List<TabEntity>,
        groups: List<TabGroupEntity>,
    ): TabsWithGroups {
        val tabsByGroupId = tabs.groupBy { it.groupId }
        val ungroupedTabs = tabsByGroupId[null] ?: emptyList()

        val tabGroups = groups.mapNotNull { group ->
            val tabsInGroup = tabsByGroupId[group.groupId] ?: emptyList()
            if (tabsInGroup.isNotEmpty()) {
                TabGroupWithTabs(group, tabsInGroup)
            } else {
                // Group is empty, will be auto-deleted
                null
            }
        }

        return TabsWithGroups(tabGroups, ungroupedTabs)
    }

    override fun getGroupsSync(): List<TabGroupEntity> {
        return tabGroupsDao.getAllGroups()
    }

    override fun getTabsInGroupSync(groupId: String): List<TabEntity> {
        return tabsDao.getTabsInGroup(groupId)
    }
}
