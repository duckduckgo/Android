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
package com.duckduckgo.nextsteps.impl

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.nextsteps.api.NextStepCompletionCheck
import com.duckduckgo.remote.messaging.api.CardItem
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Resolves which Next Steps items this app version can act on and which of them the user has already completed. */
class NextStepsCompletionChecker @Inject constructor(
    private val itemsStore: NextStepsItemsStore,
    private val completionChecks: PluginPoint<NextStepCompletionCheck>,
    private val dispatchers: DispatcherProvider,
) {

    data class Completion(
        val completedIds: Set<String>,
        val newlyCompletedIds: Set<String>,
    )

    /** Items without a completion check cannot be tracked, so they are never shown. */
    fun supportedItems(items: List<CardItem>): List<CardItem.ListItem> {
        val supportedIds = completionChecks.getPlugins().map { it.itemId }.toSet()
        return items.filterIsInstance<CardItem.ListItem>().filter { it.id in supportedIds }
    }

    /** Runs the checks for the items not yet known to be complete, records the new ones, and returns every completed id. */
    suspend fun refresh(items: List<CardItem.ListItem>): Completion = withContext(dispatchers.io()) {
        val checks = completionChecks.getPlugins().associateBy { it.itemId }
        val completedBefore = itemsStore.completedItemIds()
        val idsToCheck = items.map { it.id } - completedBefore
        val completedNow = idsToCheck.filter { checks[it]?.isComplete() == true }.toSet()
        if (completedNow.isNotEmpty()) {
            itemsStore.addCompletedItemIds(completedNow)
        }
        Completion(completedIds = completedBefore + completedNow, newlyCompletedIds = completedNow)
    }
}
