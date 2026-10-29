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

import android.annotation.SuppressLint
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.app.global.DefaultRoleBrowserDialog
import com.duckduckgo.app.widget.WidgetType
import com.duckduckgo.codegen.annotations.ContributesViewModel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.nextsteps.impl.NextStepsCompletionChecker
import com.duckduckgo.nextsteps.impl.NextStepsItemsStore
import com.duckduckgo.nextsteps.impl.isNextStepsForNewTabPage
import com.duckduckgo.remote.messaging.api.Action
import com.duckduckgo.remote.messaging.api.CardItem
import com.duckduckgo.remote.messaging.api.Content
import com.duckduckgo.remote.messaging.api.RemoteMessage
import com.duckduckgo.remote.messaging.api.RemoteMessageModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@SuppressLint("NoLifecycleObserver") // Completion can change while the host opens another screen.
@ContributesViewModel(ViewScope::class)
class NextStepsCheckListViewModel @Inject constructor(
    private val dispatchers: DispatcherProvider,
    private val remoteMessageModel: RemoteMessageModel,
    private val itemsStore: NextStepsItemsStore,
    private val completionChecker: NextStepsCompletionChecker,
    private val defaultRoleBrowserDialog: DefaultRoleBrowserDialog,
) : ViewModel(), DefaultLifecycleObserver {

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

    sealed class Command {
        data class LaunchAddWidget(val widgetType: WidgetType) : Command()
        data object LaunchDefaultBrowserDialog : Command()
        data object LaunchDefaultBrowserSettings : Command()
        data class LaunchScreen(
            val screen: String,
            val payload: String,
        ) : Command()

        /** [toPosition] is the index the item will hold once the sorted state is applied. */
        data class AnimateItemCompletion(
            val itemId: String,
            val toPosition: Int,
        ) : Command()
    }

    private val _viewState = MutableStateFlow(ViewState())
    val viewState = _viewState.asStateFlow()

    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)
    private val refresh = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST).apply { tryEmit(Unit) }

    private data class PendingCompletion(
        val itemId: String,
        val animate: Boolean = true,
    )

    private var pendingCompletion: PendingCompletion? = null
    private var animatingCompletionItemId: String? = null
    private var shownMessageId: String? = null
    private var completionOrder: List<String> = emptyList()
    private var sectionJob: Job? = null

    fun commands(): Flow<Command> = command.receiveAsFlow()

    private val activeMessage: Flow<RemoteMessage?> = remoteMessageModel.observeActiveMessages()
        .map { message -> message?.takeIf { it.isNextStepsForNewTabPage() } }

    fun onViewAttached() {
        sectionJob?.cancel()
        sectionJob = activeMessage
            .combine(refresh) { message, _ -> message }
            .onEach { message -> _viewState.value = message?.toViewState() ?: ViewState() }
            .launchIn(viewModelScope)
    }

    override fun onResume(owner: LifecycleOwner) {
        refresh.tryEmit(Unit)
    }

    override fun onStop(owner: LifecycleOwner) {
        pendingCompletion = pendingCompletion?.copy(animate = false)
    }

    fun onViewDetached() {
        sectionJob?.cancel()
        sectionJob = null
        pendingCompletion = null
        animatingCompletionItemId = null
        shownMessageId = null
    }

    fun onItemClicked(itemId: String) {
        val item = _viewState.value.items.firstOrNull { it.item.id == itemId }?.takeIf { !it.completed } ?: return
        val nextCommand = when (val action = item.item.primaryAction) {
            is Action.DefaultBrowser -> {
                if (defaultRoleBrowserDialog.shouldShowDialog()) {
                    Command.LaunchDefaultBrowserDialog
                } else {
                    Command.LaunchDefaultBrowserSettings
                }
            }

            is Action.AddWidget -> Command.LaunchAddWidget(WidgetType.fromValue(action.value))
            is Action.Navigation -> Command.LaunchScreen(action.value, action.additionalParameters?.get("payload").orEmpty())
            else -> null
        } ?: return
        pendingCompletion = PendingCompletion(itemId)
        viewModelScope.launch { command.send(nextCommand) }
    }

    fun onDefaultBrowserDialogShown() {
        defaultRoleBrowserDialog.dialogShown()
    }

    fun onItemCompletionAnimated(itemId: String) {
        if (animatingCompletionItemId == itemId) {
            animatingCompletionItemId = null
            _viewState.update { state ->
                state.copy(items = state.items.map { if (it.item.id == itemId) it.copy(completed = true) else it }.sortedItems())
            }
        }
    }

    fun onSectionDismissed() {
        val message = _viewState.value.message ?: return
        viewModelScope.launch {
            val firstDismissal = withContext(dispatchers.io()) { itemsStore.markMessageDismissed(message.id) }
            if (firstDismissal) {
                remoteMessageModel.onMessageDismissed(message)
            }
        }
    }

    private suspend fun RemoteMessage.toViewState(): ViewState {
        val content = this.content as Content.ActionableItems
        val supportedItems = completionChecker.supportedItems(content.listItems)
        val completedIds = completionChecker.refresh(supportedItems)
        completionOrder = completedIds.toList()
        val settledItems = supportedItems.map { ViewState.Item(item = it, completed = it.id in completedIds) }.sortedItems()
        startCompletionAnimationIfDue(settledItems)
        val items = supportedItems
            .map { ViewState.Item(item = it, completed = it.id in completedIds && it.id != animatingCompletionItemId) }
            .sortedItems()
        if (items.isNotEmpty() && shownMessageId != id) {
            shownMessageId = id
            remoteMessageModel.onMessageShown(this)
        }
        return ViewState(message = this, title = content.titleText, items = items)
    }

    private fun startCompletionAnimationIfDue(settledItems: List<ViewState.Item>) {
        val pending = pendingCompletion ?: return
        val toPosition = settledItems.indexOfFirst { it.item.id == pending.itemId && it.completed }
        if (toPosition < 0) return
        pendingCompletion = null
        if (pending.animate) {
            animatingCompletionItemId = pending.itemId
            command.trySend(Command.AnimateItemCompletion(pending.itemId, toPosition))
        }
    }

    private fun List<ViewState.Item>.sortedItems(): List<ViewState.Item> =
        filter { !it.completed } + filter { it.completed }.sortedByDescending { completionOrder.indexOf(it.item.id) }
}
