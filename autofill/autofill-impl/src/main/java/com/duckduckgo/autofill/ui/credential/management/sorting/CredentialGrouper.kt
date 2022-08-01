/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.autofill.ui.credential.management.sorting

import com.duckduckgo.autofill.domain.app.LoginCredentials
import com.duckduckgo.autofill.ui.credential.management.AutofillManagementRecyclerAdapter.ListItem
import java.util.*
import javax.inject.Inject

class CredentialGrouper @Inject constructor(
    private val initialExtractor: InitialExtractor,
    private val sorter: CredentialListSorter
) {

    fun group(unsortedCredentials: List<LoginCredentials>): List<ListItem> {
        val unsortedGroups = buildGroups(unsortedCredentials)
        val sortedGroups = sortGroups(unsortedGroups)
        return buildFlatList(sortedGroups)
    }

    private fun buildFlatList(sortedGroups: SortedMap<String, MutableList<LoginCredentials>>): MutableList<ListItem> {
        val flattenedList = mutableListOf<ListItem>()

        sortedGroups.forEach { (key, value) ->
            flattenedList.add(ListItem.GroupHeading(key))

            sorter.sort(value).forEach {
                flattenedList.add(ListItem.Credential(it))
            }
        }

        return flattenedList
    }

    private fun sortGroups(groups: MutableMap<String, MutableList<LoginCredentials>>): SortedMap<String, MutableList<LoginCredentials>> {
        return groups.toSortedMap(sorter.comparator())
    }

    private fun buildGroups(unsortedCredentials: List<LoginCredentials>): MutableMap<String, MutableList<LoginCredentials>> {
        val groups = mutableMapOf<String, MutableList<LoginCredentials>>()
        for (credential in unsortedCredentials) {
            val initial = initialExtractor.extractInitial(credential).toString() ?: continue
            val list = groups.getOrPut(initial) { mutableListOf() }
            list.add(credential)
        }
        return groups
    }

    private fun shouldAddNewGroup(groups: List<ListItem>, initial: String): Boolean {
        return !groups.contains(ListItem.GroupHeading(initial))
    }
}
