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

import android.annotation.SuppressLint
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.app.global.DefaultRoleBrowserDialog
import com.duckduckgo.app.widget.WidgetType
import com.duckduckgo.codegen.annotations.ContributesViewModel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.nextsteps.api.NextStepCompletionCheck
import com.duckduckgo.nextsteps.impl.NextStepsItemsStore
import com.duckduckgo.remote.messaging.api.Action
import com.duckduckgo.remote.messaging.api.CardItem
import com.duckduckgo.remote.messaging.api.Content
import com.duckduckgo.remote.messaging.api.RemoteMessage
import com.duckduckgo.remote.messaging.api.RemoteMessageModel
import com.duckduckgo.remote.messaging.api.Surface
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
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@SuppressLint("NoLifecycleObserver") // Completion can change while the host opens another screen.
@ContributesViewModel(ViewScope::class)
class NextStepsItemsViewModel @Inject constructor(
    private val dispatchers: DispatcherProvider,
    private val remoteMessageModel: RemoteMessageModel,
    private val itemsStore: NextStepsItemsStore,
    private val completionChecks: PluginPoint<NextStepCompletionCheck>,
    private val defaultRoleBrowserDialog: DefaultRoleBrowserDialog,
) : ViewModel(), DefaultLifecycleObserver {

    data class ViewState(
        val message: RemoteMessage? = null,
        val title: String = "",
        val items: List<CardItem.ListItem> = emptyList(),
    )

    sealed class Command {
        data class LaunchAddWidget(val widgetType: WidgetType) : Command()
        data object LaunchDefaultBrowserDialog : Command()
        data object LaunchDefaultBrowserSettings : Command()
        data class LaunchScreen(
            val screen: String,
            val payload: String,
        ) : Command()

        data object AnimateFrontCardCompletion : Command()
    }

    private val _viewState = MutableStateFlow(ViewState())
    val viewState = _viewState.asStateFlow()

    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)
    private val refresh = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST).apply { tryEmit(Unit) }

    private var pendingCompletionItemId: String? = null
    private var animatingCompletionItemId: String? = null
    private var dismissedMessageId: String? = null

    fun commands(): Flow<Command> = command.receiveAsFlow()

    private val activeMessage: Flow<Pair<RemoteMessage?, Set<String>>> = remoteMessageModel.observeActiveMessages()
        .map { message -> message?.takeIf { it.isNextStepsForNewTabPage() } }
        .combine(itemsStore.dismissedItemIds()) { message, dismissedIds -> message to dismissedIds }

    private var sectionJob: Job? = null

    fun onViewAttached() {
        sectionJob?.cancel()
        sectionJob = activeMessage
            .combine(refresh) { messageAndDismissed, _ -> messageAndDismissed }
            .onEach { (message, dismissedIds) ->
                val items = visibleItems(message, dismissedIds)
                val content = message?.content as? Content.ActionableItems
                _viewState.value = ViewState(
                    message = message,
                    title = content?.titleText.orEmpty(),
                    items = items,
                )
                if (message != null && items.isEmpty()) {
                    dismissMessage(message)
                }
            }
            .launchIn(viewModelScope)
    }

    override fun onResume(owner: LifecycleOwner) {
        refresh.tryEmit(Unit)
    }

    override fun onStop(owner: LifecycleOwner) {
        pendingCompletionItemId = null
    }

    fun onViewDetached() {
        sectionJob?.cancel()
        sectionJob = null
        pendingCompletionItemId = null
        animatingCompletionItemId = null
    }

    fun onPrimaryActionClicked() {
        pendingCompletionItemId = null
        val item = _viewState.value.items.firstOrNull() ?: return
        val nextCommand = when (val action = item.primaryAction) {
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
        pendingCompletionItemId = item.id
        viewModelScope.launch { command.send(nextCommand) }
    }

    fun onDefaultBrowserDialogShown() {
        defaultRoleBrowserDialog.dialogShown()
    }

    fun onFrontCardDismissed() {
        val dismissedId = _viewState.value.items.firstOrNull()?.id ?: return
        removeFrontCard()
        viewModelScope.launch(dispatchers.io()) { itemsStore.addDismissedItemId(dismissedId) }
    }

    fun onFrontCardCompletionAnimated() {
        val completingId = animatingCompletionItemId ?: return
        animatingCompletionItemId = null
        if (_viewState.value.items.firstOrNull()?.id != completingId) return
        removeFrontCard()
    }

    private fun removeFrontCard() {
        pendingCompletionItemId = null
        animatingCompletionItemId = null
        val remaining = _viewState.updateAndGet { state -> state.copy(items = state.items.drop(1)) }
        if (remaining.items.isEmpty()) {
            remaining.message?.let { dismissMessage(it) }
        }
    }

    private suspend fun visibleItems(
        message: RemoteMessage?,
        dismissedIds: Set<String>,
    ): List<CardItem.ListItem> {
        val configItems = (message?.content as? Content.ActionableItems)?.listItems?.filterIsInstance<CardItem.ListItem>() ?: return emptyList()
        val checks = completionChecks.getPlugins().associateBy { it.itemId }
        val supportedItems = configItems.filter { it.id in checks }
        val completedIds = completedItemIds(supportedItems, checks)
        val remaining = supportedItems.filter { it.id !in completedIds && it.id !in dismissedIds }
        val onScreen = _viewState.value.items
        val isNewMessage = _viewState.value.message?.id != message.id
        return if (onScreen.isEmpty() || isNewMessage) {
            loadItems(message, remaining)
        } else {
            refreshOnScreenItems(onScreen, remaining, completedIds)
        }
    }

    private suspend fun completedItemIds(
        items: List<CardItem.ListItem>,
        checks: Map<String, NextStepCompletionCheck>,
    ): Set<String> = withContext(dispatchers.io()) {
        val completedBefore = itemsStore.completedItemIds()
        val completedNow = items
            .filter { it.id !in completedBefore && checks.getValue(it.id).isComplete() }
            .map { it.id }
            .toSet()
        if (completedNow.isNotEmpty()) {
            itemsStore.addCompletedItemIds(completedNow)
        }
        completedBefore + completedNow
    }

    private suspend fun loadItems(
        message: RemoteMessage,
        remaining: List<CardItem.ListItem>,
    ): List<CardItem.ListItem> = withContext(dispatchers.io()) {
        if (remaining.isEmpty()) return@withContext emptyList()
        val visible = remaining.sortedByOrder(itemsStore.itemOrder()).rotatedIfDue()
        itemsStore.saveItemOrder(visible.map { it.id })
        visible.firstOrNull()?.let { recordImpression(message, it.id) }
        visible
    }

    private fun refreshOnScreenItems(
        onScreen: List<CardItem.ListItem>,
        remaining: List<CardItem.ListItem>,
        completedIds: Set<String>,
    ): List<CardItem.ListItem> {
        val ordered = remaining.sortedByOrder(onScreen.map { it.id })
        val front = onScreen.first()
        if (front.id == pendingCompletionItemId && front.id in completedIds) {
            pendingCompletionItemId = null
            animatingCompletionItemId = front.id
            command.trySend(Command.AnimateFrontCardCompletion)
        }
        return if (front.id == animatingCompletionItemId) listOf(front) + ordered.filter { it.id != front.id } else ordered
    }

    private fun List<CardItem.ListItem>.sortedByOrder(order: List<String>): List<CardItem.ListItem> =
        sortedBy { item -> order.indexOf(item.id).takeIf { it >= 0 } ?: Int.MAX_VALUE }

    private fun List<CardItem.ListItem>.rotatedIfDue(): List<CardItem.ListItem> =
        if (size > 1 && itemsStore.frontImpressions(first().id) >= ROTATION_IMPRESSIONS) drop(1) + first() else this

    private suspend fun recordImpression(
        message: RemoteMessage,
        frontItemId: String,
    ) {
        withContext(dispatchers.io()) {
            itemsStore.incrementFrontImpressions(frontItemId)
        }
        remoteMessageModel.onMessageShown(message)
    }

    private fun dismissMessage(message: RemoteMessage) {
        if (dismissedMessageId != message.id) {
            dismissedMessageId = message.id
            viewModelScope.launch { remoteMessageModel.onMessageDismissed(message) }
        }
    }

    private fun RemoteMessage.isNextStepsForNewTabPage(): Boolean =
        content is Content.ActionableItems && surfaces.contains(Surface.NEW_TAB_PAGE)

    private companion object {
        const val ROTATION_IMPRESSIONS = 5
    }
}
