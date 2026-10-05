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
package com.duckduckgo.nextsteps.impl.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.remote.messaging.api.CardItem
import com.duckduckgo.remote.messaging.api.Content
import com.duckduckgo.remote.messaging.api.RemoteMessage
import com.duckduckgo.remote.messaging.api.RemoteMessageModel
import com.duckduckgo.remote.messaging.api.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesViewModel(ViewScope::class)
class NextStepsItemsViewModel @Inject constructor(
    private val dispatchers: DispatcherProvider,
    private val remoteMessageModel: RemoteMessageModel,
) : ViewModel() {

    data class ViewState(
        val message: RemoteMessage? = null,
        val title: String = "",
        val items: List<CardItem.ListItem> = emptyList(),
    )

    private val _viewState = MutableStateFlow(ViewState())
    val viewState = _viewState.asStateFlow()

    init {
        remoteMessageModel.observeActiveMessages()
            .map { message -> message?.takeIf { it.isNextStepsForNewTabPage() } }
            .flowOn(dispatchers.io())
            .onEach { message ->
                val content = message?.content as? Content.NextStepsItems
                _viewState.value = ViewState(
                    message = message,
                    title = content?.titleText.orEmpty(),
                    items = content?.listItems?.filterIsInstance<CardItem.ListItem>().orEmpty(),
                )
            }
            .launchIn(viewModelScope)
    }

    fun onFrontCardDismissed() {
        val remaining = _viewState.updateAndGet { state -> state.copy(items = state.items.drop(1)) }
        if (remaining.items.isEmpty()) {
            remaining.message?.let { message ->
                viewModelScope.launch { remoteMessageModel.onMessageDismissed(message) }
            }
        }
    }

    private fun RemoteMessage.isNextStepsForNewTabPage(): Boolean =
        content is Content.NextStepsItems && surfaces.contains(Surface.NEW_TAB_PAGE)
}
