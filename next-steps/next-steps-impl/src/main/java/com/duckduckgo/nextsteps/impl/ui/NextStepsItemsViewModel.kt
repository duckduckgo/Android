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
import com.duckduckgo.nextsteps.impl.NextStepsTelemetry
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
    private val telemetry: NextStepsTelemetry,
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

    /** The card whose CTA this tab tapped. Its completion animates the card out unless the host was stopped meanwhile. */
    private data class PendingCompletion(
        val cardId: String,
        val animate: Boolean = true,
    )

    private var pendingCompletion: PendingCompletion? = null
    private var animatingCompletionItemId: String? = null

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
        pendingCompletion = pendingCompletion?.copy(animate = false)
    }

    fun onViewDetached() {
        sectionJob?.cancel()
        sectionJob = null
        pendingCompletion = null
        animatingCompletionItemId = null
    }

    fun onPrimaryActionClicked() {
        pendingCompletion = null
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
        pendingCompletion = PendingCompletion(item.id)
        viewModelScope.launch {
            telemetry.onCtaClicked(item.id)
            command.send(nextCommand)
        }
    }

    fun onDefaultBrowserDialogShown() {
        defaultRoleBrowserDialog.dialogShown()
    }

    fun onFrontCardDismissed() {
        val dismissedId = _viewState.value.items.firstOrNull()?.id ?: return
        val remaining = removeFrontCard()
        viewModelScope.launch {
            telemetry.onCardDismissed(dismissedId)
            withContext(dispatchers.io()) { itemsStore.addDismissedItemId(dismissedId) }
            onFrontCardRemoved(remaining)
        }
    }

    fun onFrontCardCompletionAnimated() {
        val completingId = animatingCompletionItemId ?: return
        animatingCompletionItemId = null
        if (_viewState.value.items.firstOrNull()?.id != completingId) return
        val remaining = removeFrontCard()
        viewModelScope.launch { onFrontCardRemoved(remaining) }
    }

    private fun removeFrontCard(): ViewState {
        pendingCompletion = null
        animatingCompletionItemId = null
        return _viewState.updateAndGet { state -> state.copy(items = state.items.drop(1)) }
    }

    private suspend fun onFrontCardRemoved(remaining: ViewState) {
        val message = remaining.message ?: return
        val promotedCard = remaining.items.firstOrNull()
        if (promotedCard == null) {
            dismissMessage(message)
        } else {
            telemetry.onFrontCardShown(message.id, promotedCard.id, remaining.items.ids())
        }
    }

    private suspend fun visibleItems(
        message: RemoteMessage?,
        dismissedIds: Set<String>,
    ): List<CardItem.ListItem> {
        val configItems = (message?.content as? Content.ActionableItems)?.listItems?.filterIsInstance<CardItem.ListItem>() ?: return emptyList()
        val checks = completionChecks.getPlugins().associateBy { it.itemId }
        val supportedItems = configItems.filter { it.id in checks }
        val completedIds = refreshCompletedItemIds(supportedItems, checks)
        val remaining = supportedItems.filter { it.id !in completedIds && it.id !in dismissedIds }
        val onScreen = _viewState.value.items
        val isNewMessage = _viewState.value.message?.id != message.id
        return if (onScreen.isEmpty() || isNewMessage) {
            loadItems(message, remaining)
        } else {
            refreshOnScreenItems(message, onScreen, remaining, completedIds)
        }
    }

    private suspend fun refreshCompletedItemIds(
        items: List<CardItem.ListItem>,
        checks: Map<String, NextStepCompletionCheck>,
    ): Set<String> = withContext(dispatchers.io()) {
        val completedBefore = itemsStore.completedItemIds()
        val idsToCheck = items.map { it.id } - completedBefore
        val completedNow = idsToCheck.filter { checks.getValue(it).isComplete() }.toSet()
        if (completedNow.isNotEmpty()) {
            telemetry.onCardsCompleted(completedNow)
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
        if (visible.isNotEmpty()) {
            countImpressionForRotationAndRmf(message, visible.first().id)
            telemetry.onFrontCardShown(message.id, visible.first().id, visible.ids())
        }
        visible
    }

    private fun refreshOnScreenItems(
        message: RemoteMessage,
        onScreen: List<CardItem.ListItem>,
        remaining: List<CardItem.ListItem>,
        completedIds: Set<String>,
    ): List<CardItem.ListItem> {
        val ordered = remaining.sortedByOrder(onScreen.map { it.id })
        val front = onScreen.first()
        pendingCompletion?.takeIf { it.cardId == front.id && front.id in completedIds }?.let { completed ->
            pendingCompletion = null
            if (completed.animate) {
                animatingCompletionItemId = front.id
                command.trySend(Command.AnimateFrontCardCompletion)
            }
        }
        if (front.id == animatingCompletionItemId) return listOf(front) + ordered.filter { it.id != front.id }
        if (front.id !in ordered.ids()) {
            ordered.firstOrNull()?.let { promotedCard ->
                viewModelScope.launch { telemetry.onFrontCardShown(message.id, promotedCard.id, ordered.ids()) }
            }
        }
        return ordered
    }

    private fun List<CardItem.ListItem>.sortedByOrder(order: List<String>): List<CardItem.ListItem> =
        sortedBy { item -> order.indexOf(item.id).takeIf { it >= 0 } ?: Int.MAX_VALUE }

    private fun List<CardItem.ListItem>.rotatedIfDue(): List<CardItem.ListItem> =
        if (size > 1 && itemsStore.frontImpressions(first().id) >= ROTATION_IMPRESSIONS) drop(1) + first() else this

    private suspend fun countImpressionForRotationAndRmf(
        message: RemoteMessage,
        frontItemId: String,
    ) {
        withContext(dispatchers.io()) {
            itemsStore.incrementFrontImpressions(frontItemId)
        }
        remoteMessageModel.onMessageShown(message)
    }

    private fun List<CardItem.ListItem>.ids(): Set<String> = map { it.id }.toSet()

    private fun dismissMessage(message: RemoteMessage) {
        viewModelScope.launch {
            if (itemsStore.markMessageDismissed(message.id)) {
                telemetry.onAllCardsDone()
                remoteMessageModel.onMessageDismissed(message)
            }
        }
    }

    private companion object {
        const val ROTATION_IMPRESSIONS = 5
    }
}
