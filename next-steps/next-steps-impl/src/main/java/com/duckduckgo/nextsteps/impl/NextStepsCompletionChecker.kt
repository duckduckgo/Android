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

class NextStepsCompletionChecker @Inject constructor(
    private val itemsStore: NextStepsItemsStore,
    private val completionChecks: PluginPoint<NextStepCompletionCheck>,
    private val dispatchers: DispatcherProvider,
) {

    fun supportedItems(items: List<CardItem>): List<CardItem.ListItem> {
        val supportedIds = completionChecks.getPlugins().map { it.itemId }.toSet()
        return items.filterIsInstance<CardItem.ListItem>().filter { it.id in supportedIds }
    }

    suspend fun refresh(
        items: List<CardItem.ListItem>,
        onNewlyCompleted: suspend (Set<String>) -> Unit = {},
    ): Set<String> = withContext(dispatchers.io()) {
        val checks = completionChecks.getPlugins().associateBy { it.itemId }
        val completedBefore = itemsStore.completedItemIds()
        val idsToCheck = items.map { it.id } - completedBefore
        val completedNow = idsToCheck.filter { checks[it]?.isComplete() == true }.toSet()
        if (completedNow.isNotEmpty()) {
            onNewlyCompleted(completedNow)
            itemsStore.addCompletedItemIds(completedNow)
        }
        completedBefore + completedNow
    }
}
