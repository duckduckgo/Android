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
package com.duckduckgo.nextsteps.impl.ui.checklist

import androidx.lifecycle.ViewModel
import com.duckduckgo.codegen.annotations.ContributesViewModel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.nextsteps.api.NextStepCompletionCheck
import com.duckduckgo.nextsteps.impl.NextStepsItemsStore
import com.duckduckgo.nextsteps.impl.isNextStepsForNewTabPage
import com.duckduckgo.remote.messaging.api.CardItem
import com.duckduckgo.remote.messaging.api.Content
import com.duckduckgo.remote.messaging.api.RemoteMessage
import com.duckduckgo.remote.messaging.api.RemoteMessageModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@ContributesViewModel(ViewScope::class)
class NextStepsCheckListViewModel @Inject constructor(
    dispatchers: DispatcherProvider,
    remoteMessageModel: RemoteMessageModel,
    private val itemsStore: NextStepsItemsStore,
    private val completionChecks: PluginPoint<NextStepCompletionCheck>,
) : ViewModel() {

    data class ViewState(
        val message: RemoteMessage? = null,
        val title: String = "",
        val items: List<Item> = emptyList(),
    ) {
        data class Item(
            val item: CardItem.ListItem,
            val completed: Boolean,
        )
    }

    val viewState: Flow<ViewState> = remoteMessageModel.observeActiveMessages()
        .map { message -> message?.takeIf { it.isNextStepsForNewTabPage() } }
        .map { message -> message?.toViewState() ?: ViewState() }
        .flowOn(dispatchers.io())

    private fun RemoteMessage.toViewState(): ViewState {
        val content = this.content as Content.ActionableItems
        val supportedIds = completionChecks.getPlugins().map { it.itemId }.toSet()
        val completedIds = itemsStore.completedItemIds()
        val items = content.listItems
            .filterIsInstance<CardItem.ListItem>()
            .filter { it.id in supportedIds }
            .map { ViewState.Item(item = it, completed = it.id in completedIds) }
            .sortedBy { it.completed }
        return ViewState(message = this, title = content.titleText, items = items)
    }
}
