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

import com.duckduckgo.app.global.DefaultRoleBrowserDialog
import com.duckduckgo.app.widget.WidgetType
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.data.store.api.FakeSharedPreferencesProvider
import com.duckduckgo.nextsteps.api.NextStepCompletionCheck
import com.duckduckgo.nextsteps.impl.NextStepsItemsStore
import com.duckduckgo.nextsteps.impl.RealNextStepsItemsStore
import com.duckduckgo.nextsteps.impl.ui.NextStepsItemsViewModel.Command
import com.duckduckgo.remote.messaging.api.Action
import com.duckduckgo.remote.messaging.api.CardItem
import com.duckduckgo.remote.messaging.api.CardItemType
import com.duckduckgo.remote.messaging.api.Content
import com.duckduckgo.remote.messaging.api.RemoteMessage
import com.duckduckgo.remote.messaging.api.RemoteMessageModel
import com.duckduckgo.remote.messaging.api.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class NextStepsItemsViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val activeMessage = MutableStateFlow<RemoteMessage?>(null)
    private val remoteMessageModel: RemoteMessageModel = mock {
        whenever(it.observeActiveMessages()).thenReturn(activeMessage)
    }

    private val itemsStore: NextStepsItemsStore = RealNextStepsItemsStore(FakeSharedPreferencesProvider(), coroutineRule.testDispatcherProvider)
    private val defaultBrowserCheck = FakeCompletionCheck("setup_default_browser")
    private val addWidgetCheck = FakeCompletionCheck("setup_add_widget")
    private val syncCheck = FakeCompletionCheck("setup_sync")
    private val completionChecks = FakePluginPoint(mutableListOf(defaultBrowserCheck, addWidgetCheck, syncCheck))
    private val defaultRoleBrowserDialog: DefaultRoleBrowserDialog = mock()

    private lateinit var testee: NextStepsItemsViewModel

    @Before
    fun setUp() {
        testee = aViewModel()
    }

    private fun aViewModel() = NextStepsItemsViewModel(
        dispatchers = coroutineRule.testDispatcherProvider,
        remoteMessageModel = remoteMessageModel,
        itemsStore = itemsStore,
        completionChecks = completionChecks,
        defaultRoleBrowserDialog = defaultRoleBrowserDialog,
    ).also { it.onViewAttached() }

    @Test
    fun `when the section is detached then a new message is ignored`() = runTest {
        val message = aMessage(content = nextStepsContent())
        activeMessage.value = message
        advanceUntilIdle()
        testee.onViewDetached()

        activeMessage.value = message.copy(id = "android_complete_your_setup_v2")
        advanceUntilIdle()

        assertEquals(message, testee.viewState.value.message)
        assertEquals(1, itemsStore.frontImpressions(defaultBrowserItem.id))
        verify(remoteMessageModel, times(1)).onMessageShown(any())
    }

    @Test
    fun `when the section is reattached with the same message then the cards are kept and no impression is counted`() = runTest {
        activeMessage.value = aMessage(content = nextStepsContent())
        advanceUntilIdle()
        testee.onViewDetached()

        testee.onViewAttached()
        advanceUntilIdle()

        assertEquals(listOf(defaultBrowserItem, addWidgetItem), testee.viewState.value.items)
        assertEquals(1, itemsStore.frontImpressions(defaultBrowserItem.id))
        verify(remoteMessageModel, times(1)).onMessageShown(any())
    }

    @Test
    fun `when a different message replaces the one on screen then it loads fresh and counts an impression`() = runTest {
        activeMessage.value = aMessage(content = nextStepsContent())
        advanceUntilIdle()
        assertEquals(1, itemsStore.frontImpressions(defaultBrowserItem.id))

        val newMessage = aMessage(content = Content.ActionableItems(titleText = "Complete your setup", listItems = listOf(syncItem, addWidgetItem)))
            .copy(id = "android_complete_your_setup_v2")
        activeMessage.value = newMessage
        advanceUntilIdle()

        assertEquals(listOf(addWidgetItem, syncItem), testee.viewState.value.items)
        assertEquals(1, itemsStore.frontImpressions(addWidgetItem.id))
        verify(remoteMessageModel).onMessageShown(newMessage)
    }

    @Test
    fun `when the last card goes and the message re-emits then the stored order is not wiped`() = runTest {
        activeMessage.value = aMessage(content = nextStepsContent())
        advanceUntilIdle()
        testee.onFrontCardDismissed()
        testee.onFrontCardDismissed()
        advanceUntilIdle()

        assertEquals(listOf(defaultBrowserItem.id, addWidgetItem.id), itemsStore.itemOrder())
    }

    @Test
    fun `when no active message then state is empty`() = runTest {
        advanceUntilIdle()

        assertNull(testee.viewState.value.message)
        assertEquals(emptyList<CardItem.ListItem>(), testee.viewState.value.items)
    }

    @Test
    fun `when active message is not next steps then state is empty`() = runTest {
        activeMessage.value = aMessage(content = Content.Small(titleText = "title", descriptionText = "description"))

        advanceUntilIdle()

        assertNull(testee.viewState.value.message)
    }

    @Test
    fun `when next steps message targets another surface then state is empty`() = runTest {
        activeMessage.value = aMessage(content = nextStepsContent(), surfaces = listOf(Surface.MODAL))

        advanceUntilIdle()

        assertNull(testee.viewState.value.message)
    }

    @Test
    fun `when next steps message is active then state holds the message and its list items`() = runTest {
        val message = aMessage(content = nextStepsContent())
        activeMessage.value = message

        advanceUntilIdle()

        assertEquals(message, testee.viewState.value.message)
        assertEquals(listOf(defaultBrowserItem, addWidgetItem), testee.viewState.value.items)
        assertEquals("Complete your setup", testee.viewState.value.title)
    }

    @Test
    fun `when active message is cleared then state is emptied`() = runTest {
        activeMessage.value = aMessage(content = nextStepsContent())
        advanceUntilIdle()

        activeMessage.value = null
        advanceUntilIdle()

        assertNull(testee.viewState.value.message)
        assertEquals(emptyList<CardItem.ListItem>(), testee.viewState.value.items)
    }

    @Test
    fun `when front card is dismissed then the next item moves to the front`() = runTest {
        activeMessage.value = aMessage(content = nextStepsContent())
        advanceUntilIdle()

        testee.onFrontCardDismissed()

        assertEquals(listOf(addWidgetItem), testee.viewState.value.items)
        verify(remoteMessageModel, never()).onMessageDismissed(any())
    }

    @Test
    fun `when last card is dismissed then no items are left`() = runTest {
        activeMessage.value = aMessage(content = nextStepsContent())
        advanceUntilIdle()

        testee.onFrontCardDismissed()
        testee.onFrontCardDismissed()
        advanceUntilIdle()

        assertEquals(emptyList<CardItem.ListItem>(), testee.viewState.value.items)
    }

    @Test
    fun `when last card is dismissed then the message is dismissed in RMF`() = runTest {
        val message = aMessage(content = nextStepsContent())
        activeMessage.value = message
        advanceUntilIdle()

        testee.onFrontCardDismissed()
        testee.onFrontCardDismissed()
        advanceUntilIdle()

        verify(remoteMessageModel).onMessageDismissed(message)
    }

    @Test
    fun `when a card was dismissed before then it is not shown again by a new view model`() = runTest {
        activeMessage.value = aMessage(content = nextStepsContent())
        advanceUntilIdle()
        testee.onFrontCardDismissed()
        advanceUntilIdle()

        val newViewModel = aViewModel()
        advanceUntilIdle()

        assertEquals(listOf(addWidgetItem), newViewModel.viewState.value.items)
    }

    @Test
    fun `when a card was dismissed under a previous message then a new message does not show it`() = runTest {
        activeMessage.value = aMessage(content = nextStepsContent())
        advanceUntilIdle()
        testee.onFrontCardDismissed()
        advanceUntilIdle()

        activeMessage.value = aMessage(content = nextStepsContent()).copy(id = "android_complete_your_setup_v2")
        advanceUntilIdle()

        assertEquals(listOf(addWidgetItem), testee.viewState.value.items)
    }

    @Test
    fun `when every card was dismissed before then the section is empty and the message is dismissed`() = runTest {
        itemsStore.addDismissedItemId(defaultBrowserItem.id)
        itemsStore.addDismissedItemId(addWidgetItem.id)
        val message = aMessage(content = nextStepsContent())
        activeMessage.value = message

        advanceUntilIdle()

        assertEquals(emptyList<CardItem.ListItem>(), testee.viewState.value.items)
        verify(remoteMessageModel).onMessageDismissed(message)
    }

    @Test
    fun `when a card is dismissed in one view model then another view model sharing the store drops it`() = runTest {
        activeMessage.value = aMessage(content = nextStepsContent())
        val otherTab = aViewModel()
        advanceUntilIdle()
        assertEquals(listOf(defaultBrowserItem, addWidgetItem), otherTab.viewState.value.items)

        testee.onFrontCardDismissed()
        advanceUntilIdle()

        assertEquals(listOf(addWidgetItem), otherTab.viewState.value.items)
    }

    @Test
    fun `when the last card is dismissed and the state refreshes then the message is dismissed once`() = runTest {
        val message = aMessage(content = nextStepsContent())
        activeMessage.value = message
        advanceUntilIdle()

        testee.onFrontCardDismissed()
        testee.onFrontCardDismissed()
        advanceUntilIdle()
        testee.onResume(mock())
        advanceUntilIdle()

        verify(remoteMessageModel, times(1)).onMessageDismissed(message)
    }

    @Test
    fun `when a new tab page loads the cards then the message is marked as shown once`() = runTest {
        val message = aMessage(content = nextStepsContent())
        activeMessage.value = message
        advanceUntilIdle()

        verify(remoteMessageModel, times(1)).onMessageShown(message)
    }

    @Test
    fun `when the host resumes then no impression is counted`() = runTest {
        val message = aMessage(content = nextStepsContent())
        activeMessage.value = message
        advanceUntilIdle()

        testee.onResume(mock())
        advanceUntilIdle()

        assertEquals(1, itemsStore.frontImpressions(defaultBrowserItem.id))
        verify(remoteMessageModel, times(1)).onMessageShown(message)
    }

    @Test
    fun `when a new tab page loads the cards then the front card counts one impression`() = runTest {
        activeMessage.value = aMessage(content = nextStepsContent())
        advanceUntilIdle()

        assertEquals(1, itemsStore.frontImpressions(defaultBrowserItem.id))
        assertEquals(0, itemsStore.frontImpressions(addWidgetItem.id))
    }

    @Test
    fun `when the message is re-emitted while cards are on screen then no extra impression is counted`() = runTest {
        val message = aMessage(content = nextStepsContent())
        activeMessage.value = message
        advanceUntilIdle()

        activeMessage.value = message.copy(matchingRules = listOf(1))
        advanceUntilIdle()

        assertEquals(1, itemsStore.frontImpressions(defaultBrowserItem.id))
    }

    @Test
    fun `when the front card was loaded fewer than five times then the order is kept`() = runTest {
        activeMessage.value = aMessage(content = nextStepsContent())
        loadNewTabPagesUntil(impressions = 4)

        val newViewModel = aViewModel()
        advanceUntilIdle()

        assertEquals(listOf(defaultBrowserItem, addWidgetItem), newViewModel.viewState.value.items)
    }

    @Test
    fun `when the front card was loaded five times then it moves to the back on the next load`() = runTest {
        activeMessage.value = aMessage(content = nextStepsContent())
        loadNewTabPagesUntil(impressions = 5)

        val newViewModel = aViewModel()
        advanceUntilIdle()

        assertEquals(listOf(addWidgetItem, defaultBrowserItem), newViewModel.viewState.value.items)
    }

    @Test
    fun `when the message is re-emitted while cards are on screen then the order on screen does not change`() = runTest {
        val message = aMessage(content = nextStepsContent())
        activeMessage.value = message
        loadNewTabPagesUntil(impressions = 5)
        val viewModel = aViewModel()
        advanceUntilIdle()
        assertEquals(listOf(addWidgetItem, defaultBrowserItem), viewModel.viewState.value.items)
        repeat(5) { itemsStore.incrementFrontImpressions(addWidgetItem.id) }

        activeMessage.value = message.copy(matchingRules = listOf(1))
        advanceUntilIdle()

        assertEquals(listOf(addWidgetItem, defaultBrowserItem), viewModel.viewState.value.items)
    }

    @Test
    fun `when a card was just rotated to the front then its impressions start from one`() = runTest {
        activeMessage.value = aMessage(content = nextStepsContent())
        loadNewTabPagesUntil(impressions = 5)

        val rotatedViewModel = aViewModel()
        advanceUntilIdle()

        assertEquals(listOf(addWidgetItem, defaultBrowserItem), rotatedViewModel.viewState.value.items)
        assertEquals(1, itemsStore.frontImpressions(addWidgetItem.id))
        assertEquals(0, itemsStore.frontImpressions(defaultBrowserItem.id))
    }

    @Test
    fun `when only one card is left then it never rotates`() = runTest {
        itemsStore.addDismissedItemId(addWidgetItem.id)
        activeMessage.value = aMessage(content = nextStepsContent())
        loadNewTabPagesUntil(impressions = 6)

        val newViewModel = aViewModel()
        advanceUntilIdle()

        assertEquals(listOf(defaultBrowserItem), newViewModel.viewState.value.items)
    }

    @Test
    fun `when the stored order has ids that are gone and the config has new ids then new ids are appended`() = runTest {
        itemsStore.saveItemOrder(listOf("setup_removed", addWidgetItem.id, defaultBrowserItem.id))
        activeMessage.value = aMessage(
            content = Content.ActionableItems(
                titleText = "Complete your setup",
                listItems = listOf(syncItem, defaultBrowserItem, addWidgetItem),
            ),
        )

        advanceUntilIdle()

        assertEquals(listOf(addWidgetItem, defaultBrowserItem, syncItem), testee.viewState.value.items)
    }

    @Test
    fun `when there are no items then no impression is counted`() = runTest {
        advanceUntilIdle()

        assertEquals(0, itemsStore.frontImpressions(defaultBrowserItem.id))
    }

    @Test
    fun `when an item has no completion check then it is dropped`() = runTest {
        completionChecks.plugins.remove(defaultBrowserCheck)
        activeMessage.value = aMessage(content = nextStepsContent())

        advanceUntilIdle()

        assertEquals(listOf(addWidgetItem), testee.viewState.value.items)
        assertEquals(0, itemsStore.frontImpressions(defaultBrowserItem.id))
        assertEquals(1, itemsStore.frontImpressions(addWidgetItem.id))
    }

    @Test
    fun `when an item is complete then it is dropped before counting impressions`() = runTest {
        defaultBrowserCheck.completed = true
        activeMessage.value = aMessage(content = nextStepsContent())

        advanceUntilIdle()

        assertEquals(listOf(addWidgetItem), testee.viewState.value.items)
        assertEquals(0, itemsStore.frontImpressions(defaultBrowserItem.id))
        assertEquals(1, itemsStore.frontImpressions(addWidgetItem.id))
        assertEquals(emptySet<String>(), itemsStore.dismissedItemIds().first())
    }

    @Test
    fun `when an item is complete then it is remembered as completed`() = runTest {
        defaultBrowserCheck.completed = true
        activeMessage.value = aMessage(content = nextStepsContent())

        advanceUntilIdle()

        assertEquals(setOf(defaultBrowserItem.id), itemsStore.completedItemIds())
    }

    @Test
    fun `when an item has no completion check then it is not remembered as completed`() = runTest {
        completionChecks.plugins.remove(defaultBrowserCheck)
        activeMessage.value = aMessage(content = nextStepsContent())

        advanceUntilIdle()

        assertEquals(emptySet<String>(), itemsStore.completedItemIds())
    }

    @Test
    fun `when an item was completed before then it stays hidden after its check turns false`() = runTest {
        addWidgetCheck.completed = true
        activeMessage.value = aMessage(content = nextStepsContent())
        advanceUntilIdle()
        assertEquals(listOf(defaultBrowserItem), testee.viewState.value.items)

        addWidgetCheck.completed = false
        testee = aViewModel()
        advanceUntilIdle()

        assertEquals(listOf(defaultBrowserItem), testee.viewState.value.items)
    }

    @Test
    fun `when every item was completed before then the message is dismissed even if the checks turn false`() = runTest {
        itemsStore.addCompletedItemIds(setOf(defaultBrowserItem.id, addWidgetItem.id))
        val message = aMessage(content = nextStepsContent())
        activeMessage.value = message

        advanceUntilIdle()

        assertEquals(emptyList<CardItem.ListItem>(), testee.viewState.value.items)
        verify(remoteMessageModel).onMessageDismissed(message)
    }

    @Test
    fun `when an item was completed in a previous message then a new message does not show it`() = runTest {
        defaultBrowserCheck.completed = true
        activeMessage.value = aMessage(content = nextStepsContent())
        advanceUntilIdle()

        defaultBrowserCheck.completed = false
        activeMessage.value = aMessage(content = nextStepsContent()).copy(id = "android_complete_your_setup_v2")
        advanceUntilIdle()

        assertEquals(listOf(addWidgetItem), testee.viewState.value.items)
    }

    @Test
    fun `when an item was completed under a previous message then a new message does not show it`() = runTest {
        defaultBrowserCheck.completed = true
        activeMessage.value = aMessage(content = nextStepsContent())
        advanceUntilIdle()

        defaultBrowserCheck.completed = false
        activeMessage.value = aMessage(content = nextStepsContent()).copy(id = "android_complete_your_setup_v2")
        advanceUntilIdle()

        assertEquals(listOf(addWidgetItem), testee.viewState.value.items)
    }

    @Test
    fun `when every item is complete then the message is dismissed`() = runTest {
        defaultBrowserCheck.completed = true
        addWidgetCheck.completed = true
        val message = aMessage(content = nextStepsContent())
        activeMessage.value = message

        advanceUntilIdle()

        assertEquals(emptyList<CardItem.ListItem>(), testee.viewState.value.items)
        assertEquals(0, itemsStore.frontImpressions(defaultBrowserItem.id))
        verify(remoteMessageModel).onMessageDismissed(message)
    }

    @Test
    fun `when no item has a completion check then the message is dismissed`() = runTest {
        completionChecks.plugins.clear()
        val message = aMessage(content = nextStepsContent())
        activeMessage.value = message

        advanceUntilIdle()

        assertEquals(emptyList<CardItem.ListItem>(), testee.viewState.value.items)
        verify(remoteMessageModel).onMessageDismissed(message)
    }

    @Test
    fun `when resumed after completing the front item then it is dropped without a new impression`() = runTest {
        activeMessage.value = aMessage(content = nextStepsContent())
        val commands = mutableListOf<Command>()
        val job = testee.commands().onEach { commands.add(it) }.launchIn(this)
        advanceUntilIdle()
        defaultBrowserCheck.completed = true

        testee.onResume(mock())
        advanceUntilIdle()

        assertEquals(listOf(addWidgetItem), testee.viewState.value.items)
        assertEquals(1, itemsStore.frontImpressions(defaultBrowserItem.id))
        assertEquals(0, itemsStore.frontImpressions(addWidgetItem.id))
        assertEquals(emptySet<String>(), itemsStore.dismissedItemIds().first())
        assertEquals(emptyList<Command>(), commands)
        verify(remoteMessageModel, never()).onMessageDismissed(any())
        job.cancel()
    }

    @Test
    fun `when tapped front item completes on resume then it stays until its completion animation ends`() = runTest {
        whenever(defaultRoleBrowserDialog.shouldShowDialog()).thenReturn(true)
        activeMessage.value = aMessage(content = nextStepsContent())
        advanceUntilIdle()
        testee.onPrimaryActionClicked()
        assertEquals(Command.LaunchDefaultBrowserDialog, testee.commands().first())
        defaultBrowserCheck.completed = true

        testee.onResume(mock())
        advanceUntilIdle()

        assertEquals(Command.AnimateFrontCardCompletion, testee.commands().first())
        assertEquals(listOf(defaultBrowserItem, addWidgetItem), testee.viewState.value.items)
        assertEquals(1, itemsStore.frontImpressions(defaultBrowserItem.id))
        assertEquals(0, itemsStore.frontImpressions(addWidgetItem.id))
        verify(remoteMessageModel, never()).onMessageDismissed(any())

        testee.onFrontCardCompletionAnimated()
        advanceUntilIdle()

        assertEquals(listOf(addWidgetItem), testee.viewState.value.items)
        assertEquals(emptySet<String>(), itemsStore.dismissedItemIds().first())
        assertEquals(1, itemsStore.frontImpressions(defaultBrowserItem.id))
        assertEquals(0, itemsStore.frontImpressions(addWidgetItem.id))
        verify(remoteMessageModel, never()).onMessageDismissed(any())
    }

    @Test
    fun `when last tapped item completes then the message is dismissed after its completion animation`() = runTest {
        val message = aMessage(content = Content.ActionableItems(titleText = "Complete your setup", listItems = listOf(addWidgetItem)))
        activeMessage.value = message
        advanceUntilIdle()
        testee.onPrimaryActionClicked()
        assertEquals(Command.LaunchAddWidget(WidgetType.SEARCH), testee.commands().first())
        addWidgetCheck.completed = true

        testee.onResume(mock())
        advanceUntilIdle()

        assertEquals(Command.AnimateFrontCardCompletion, testee.commands().first())
        assertEquals(listOf(addWidgetItem), testee.viewState.value.items)
        verify(remoteMessageModel, never()).onMessageDismissed(any())

        testee.onFrontCardCompletionAnimated()
        advanceUntilIdle()

        assertEquals(emptyList<CardItem.ListItem>(), testee.viewState.value.items)
        assertEquals(emptySet<String>(), itemsStore.dismissedItemIds().first())
        verify(remoteMessageModel).onMessageDismissed(message)
    }

    @Test
    fun `when host stops after tapping then completed front item is dropped silently on resume`() = runTest {
        activeMessage.value = aMessage(content = nextStepsContent())
        advanceUntilIdle()
        testee.onPrimaryActionClicked()
        assertEquals(Command.LaunchDefaultBrowserSettings, testee.commands().first())
        val commands = mutableListOf<Command>()
        val job = testee.commands().onEach { commands.add(it) }.launchIn(this)
        testee.onStop(mock())
        defaultBrowserCheck.completed = true

        testee.onResume(mock())
        advanceUntilIdle()

        assertEquals(emptyList<Command>(), commands)
        assertEquals(listOf(addWidgetItem), testee.viewState.value.items)
        assertEquals(emptySet<String>(), itemsStore.dismissedItemIds().first())
        assertEquals(1, itemsStore.frontImpressions(defaultBrowserItem.id))
        assertEquals(0, itemsStore.frontImpressions(addWidgetItem.id))
        job.cancel()
    }

    @Test
    fun `when section detaches and reattaches after tapping then completed front item is dropped silently`() = runTest {
        val message = aMessage(content = nextStepsContent())
        activeMessage.value = message
        advanceUntilIdle()
        testee.onPrimaryActionClicked()
        assertEquals(Command.LaunchDefaultBrowserSettings, testee.commands().first())
        val commands = mutableListOf<Command>()
        val job = testee.commands().onEach { commands.add(it) }.launchIn(this)
        testee.onViewDetached()
        defaultBrowserCheck.completed = true

        testee.onViewAttached()
        advanceUntilIdle()

        assertEquals(emptyList<Command>(), commands)
        assertEquals(listOf(addWidgetItem), testee.viewState.value.items)
        assertEquals(emptySet<String>(), itemsStore.dismissedItemIds().first())
        job.cancel()
    }

    @Test
    fun `when non front item completes while front was tapped then only completed item is dropped silently`() = runTest {
        activeMessage.value = aMessage(
            content = Content.ActionableItems(titleText = "Complete your setup", listItems = listOf(defaultBrowserItem, addWidgetItem, syncItem)),
        )
        advanceUntilIdle()
        testee.onPrimaryActionClicked()
        assertEquals(Command.LaunchDefaultBrowserSettings, testee.commands().first())
        val commands = mutableListOf<Command>()
        val job = testee.commands().onEach { commands.add(it) }.launchIn(this)
        addWidgetCheck.completed = true

        testee.onResume(mock())
        advanceUntilIdle()

        assertEquals(emptyList<Command>(), commands)
        assertEquals(listOf(defaultBrowserItem, syncItem), testee.viewState.value.items)
        assertEquals(emptySet<String>(), itemsStore.dismissedItemIds().first())
        assertEquals(1, itemsStore.frontImpressions(defaultBrowserItem.id))
        assertEquals(0, itemsStore.frontImpressions(syncItem.id))
        job.cancel()
    }

    @Test
    fun `when refreshed during completion animation then front stays and animation command is not repeated`() = runTest {
        val message = aMessage(
            content = Content.ActionableItems(titleText = "Complete your setup", listItems = listOf(defaultBrowserItem, addWidgetItem, syncItem)),
        )
        activeMessage.value = message
        advanceUntilIdle()
        repeat(4) { itemsStore.incrementFrontImpressions(defaultBrowserItem.id) }
        testee.onPrimaryActionClicked()
        assertEquals(Command.LaunchDefaultBrowserSettings, testee.commands().first())
        val commands = mutableListOf<Command>()
        val job = testee.commands().onEach { commands.add(it) }.launchIn(this)
        defaultBrowserCheck.completed = true
        testee.onResume(mock())
        advanceUntilIdle()

        testee.onResume(mock())
        advanceUntilIdle()
        addWidgetCheck.completed = true
        activeMessage.value = message.copy(matchingRules = listOf(1))
        advanceUntilIdle()

        assertEquals(listOf(Command.AnimateFrontCardCompletion), commands)
        assertEquals(listOf(defaultBrowserItem, syncItem), testee.viewState.value.items)
        assertEquals(5, itemsStore.frontImpressions(defaultBrowserItem.id))
        assertEquals(0, itemsStore.frontImpressions(syncItem.id))

        testee.onFrontCardCompletionAnimated()
        advanceUntilIdle()
        testee.onResume(mock())
        advanceUntilIdle()

        assertEquals(listOf(Command.AnimateFrontCardCompletion), commands)
        assertEquals(listOf(syncItem), testee.viewState.value.items)
        assertEquals(emptySet<String>(), itemsStore.dismissedItemIds().first())
        assertEquals(0, itemsStore.frontImpressions(syncItem.id))
        job.cancel()
    }

    @Test
    fun `when tapped front loses its completion check then it is dropped without animation`() = runTest {
        activeMessage.value = aMessage(content = nextStepsContent())
        advanceUntilIdle()
        testee.onPrimaryActionClicked()
        assertEquals(Command.LaunchDefaultBrowserSettings, testee.commands().first())
        val commands = mutableListOf<Command>()
        val job = testee.commands().onEach { commands.add(it) }.launchIn(this)
        completionChecks.plugins.remove(defaultBrowserCheck)

        testee.onResume(mock())
        advanceUntilIdle()

        assertEquals(emptyList<Command>(), commands)
        assertEquals(listOf(addWidgetItem), testee.viewState.value.items)
        job.cancel()
    }

    @Test
    fun `when resumed after completing an item then on screen order is kept even when rotation is due`() = runTest {
        itemsStore.saveItemOrder(listOf(syncItem.id, defaultBrowserItem.id, addWidgetItem.id))
        activeMessage.value = aMessage(
            content = Content.ActionableItems(
                titleText = "Complete your setup",
                listItems = listOf(defaultBrowserItem, addWidgetItem, syncItem),
            ),
        )
        advanceUntilIdle()
        repeat(4) { itemsStore.incrementFrontImpressions(syncItem.id) }
        defaultBrowserCheck.completed = true

        testee.onResume(mock())
        advanceUntilIdle()

        assertEquals(listOf(syncItem, addWidgetItem), testee.viewState.value.items)
        assertEquals(5, itemsStore.frontImpressions(syncItem.id))
    }

    @Test
    fun `when resumed after completing every item then the message is dismissed`() = runTest {
        val message = aMessage(content = nextStepsContent())
        activeMessage.value = message
        advanceUntilIdle()
        defaultBrowserCheck.completed = true
        addWidgetCheck.completed = true

        testee.onResume(mock())
        advanceUntilIdle()

        assertEquals(emptyList<CardItem.ListItem>(), testee.viewState.value.items)
        verify(remoteMessageModel).onMessageDismissed(message)
    }

    @Test
    fun `when resumed after the active message is cleared then state stays empty`() = runTest {
        activeMessage.value = aMessage(content = nextStepsContent())
        advanceUntilIdle()
        activeMessage.value = null
        advanceUntilIdle()

        testee.onResume(mock())
        advanceUntilIdle()

        assertNull(testee.viewState.value.message)
        assertEquals(emptyList<CardItem.ListItem>(), testee.viewState.value.items)
        verify(remoteMessageModel, never()).getActiveMessage()
    }

    @Test
    fun `when resumed before the initial message is evaluated then only one impression is counted`() = runTest {
        activeMessage.value = aMessage(content = nextStepsContent())

        testee.onResume(mock())
        advanceUntilIdle()

        assertEquals(listOf(defaultBrowserItem, addWidgetItem), testee.viewState.value.items)
        assertEquals(1, itemsStore.frontImpressions(defaultBrowserItem.id))
    }

    @Test
    fun `when default browser primary action is clicked then settings command is emitted without dismissing the item`() = runTest {
        whenever(defaultRoleBrowserDialog.shouldShowDialog()).thenReturn(false)
        activeMessage.value = aMessage(content = nextStepsContent())
        advanceUntilIdle()

        testee.onPrimaryActionClicked()

        assertEquals(Command.LaunchDefaultBrowserSettings, testee.commands().first())
        assertEquals(listOf(defaultBrowserItem, addWidgetItem), testee.viewState.value.items)
        assertEquals(emptySet<String>(), itemsStore.dismissedItemIds().first())
        verify(remoteMessageModel, never()).onMessageDismissed(any())
        verify(defaultRoleBrowserDialog, never()).dialogShown()
    }

    @Test
    fun `when default browser dialog can be shown then dialog command is emitted without dismissing the item`() = runTest {
        whenever(defaultRoleBrowserDialog.shouldShowDialog()).thenReturn(true)
        activeMessage.value = aMessage(content = nextStepsContent())
        advanceUntilIdle()

        testee.onPrimaryActionClicked()

        assertEquals(Command.LaunchDefaultBrowserDialog, testee.commands().first())
        assertEquals(listOf(defaultBrowserItem, addWidgetItem), testee.viewState.value.items)
        assertEquals(emptySet<String>(), itemsStore.dismissedItemIds().first())
        verify(remoteMessageModel, never()).onMessageDismissed(any())
        verify(defaultRoleBrowserDialog, never()).dialogShown()
    }

    @Test
    fun `when default browser dialog is shown then the shared attempt count is updated`() {
        testee.onDefaultBrowserDialogShown()

        verify(defaultRoleBrowserDialog).dialogShown()
    }

    @Test
    fun `when add widget primary action is clicked then widget command is emitted`() = runTest {
        val item = syncItem.copy(primaryAction = Action.AddWidget("searchOnly"))
        activeMessage.value = aMessage(content = Content.ActionableItems(titleText = "Complete your setup", listItems = listOf(item)))
        advanceUntilIdle()

        testee.onPrimaryActionClicked()

        assertEquals(Command.LaunchAddWidget(WidgetType.SEARCH_ONLY), testee.commands().first())
        assertEquals(listOf(item), testee.viewState.value.items)
        assertEquals(emptySet<String>(), itemsStore.dismissedItemIds().first())
        verify(remoteMessageModel, never()).onMessageDismissed(any())
        verify(defaultRoleBrowserDialog, never()).shouldShowDialog()
    }

    @Test
    fun `when navigation primary action is clicked then screen and payload command is emitted`() = runTest {
        val item = defaultBrowserItem.copy(primaryAction = Action.Navigation("sync", mapOf("payload" to "{\"source\":\"next_steps\"}")))
        activeMessage.value = aMessage(content = Content.ActionableItems(titleText = "Complete your setup", listItems = listOf(item)))
        advanceUntilIdle()

        testee.onPrimaryActionClicked()

        assertEquals(Command.LaunchScreen("sync", "{\"source\":\"next_steps\"}"), testee.commands().first())
    }

    @Test
    fun `when navigation primary action has no parameters then payload is empty`() = runTest {
        val item = defaultBrowserItem.copy(primaryAction = Action.Navigation("sync", null))
        activeMessage.value = aMessage(content = Content.ActionableItems(titleText = "Complete your setup", listItems = listOf(item)))
        advanceUntilIdle()

        testee.onPrimaryActionClicked()

        assertEquals(Command.LaunchScreen("sync", ""), testee.commands().first())
    }

    @Test
    fun `when front card changes then primary action uses the new front item`() = runTest {
        val item = syncItem.copy(primaryAction = Action.Navigation("sync", null))
        activeMessage.value = aMessage(
            content = Content.ActionableItems(titleText = "Complete your setup", listItems = listOf(defaultBrowserItem, item)),
        )
        advanceUntilIdle()
        testee.onFrontCardDismissed()

        testee.onPrimaryActionClicked()

        assertEquals(Command.LaunchScreen("sync", ""), testee.commands().first())
    }

    @Test
    fun `when dismiss primary action is clicked then no command is emitted`() = runTest {
        assertNoCommandFor(Action.Dismiss)
    }

    @Test
    fun `when url primary action is clicked then no command is emitted`() = runTest {
        assertNoCommandFor(Action.Url("https://duckduckgo.com"))
    }

    @Test
    fun `when url in context primary action is clicked then no command is emitted`() = runTest {
        assertNoCommandFor(Action.UrlInContext("https://duckduckgo.com"))
    }

    @Test
    fun `when primary action is null then no command is emitted`() = runTest {
        assertNoCommandFor(null)
    }

    @Test
    fun `when primary action is unsupported then no command is emitted`() = runTest {
        assertNoCommandFor(Action.DefaultCredentialProvider)
    }

    @Test
    fun `when no front item then primary action emits no command`() = runTest {
        val commands = mutableListOf<Command>()
        val job = testee.commands().onEach { commands.add(it) }.launchIn(this)
        advanceUntilIdle()

        testee.onPrimaryActionClicked()
        advanceUntilIdle()

        assertEquals(emptyList<Command>(), commands)
        job.cancel()
    }

    private fun TestScope.assertNoCommandFor(action: Action?) {
        val item = defaultBrowserItem.copy(primaryAction = action)
        activeMessage.value = aMessage(content = Content.ActionableItems(titleText = "Complete your setup", listItems = listOf(item)))
        val commands = mutableListOf<Command>()
        val job = testee.commands().onEach { commands.add(it) }.launchIn(this)
        advanceUntilIdle()

        testee.onPrimaryActionClicked()
        advanceUntilIdle()

        assertEquals(emptyList<Command>(), commands)
        assertEquals(listOf(item), testee.viewState.value.items)
        job.cancel()
    }

    private class FakePluginPoint(val plugins: MutableList<NextStepCompletionCheck>) : PluginPoint<NextStepCompletionCheck> {
        override fun getPlugins(): Collection<NextStepCompletionCheck> = plugins
    }

    private class FakeCompletionCheck(override val itemId: String, var completed: Boolean = false) : NextStepCompletionCheck {
        override suspend fun isComplete(): Boolean = completed
    }

    private fun TestScope.loadNewTabPagesUntil(impressions: Int) {
        advanceUntilIdle()
        val frontId = testee.viewState.value.items.first().id
        while (itemsStore.frontImpressions(frontId) < impressions) {
            aViewModel()
            advanceUntilIdle()
        }
        assertEquals(impressions, itemsStore.frontImpressions(frontId))
    }

    private val defaultBrowserItem = CardItem.ListItem(
        id = "setup_default_browser",
        type = CardItemType.ONE_ACTION_ITEM,
        titleText = "Browse privately by default",
        descriptionText = "Search privately every time with DuckDuckGo as your default browser",
        placeholder = Content.Placeholder.ANNOUNCE,
        primaryAction = Action.DefaultBrowser,
        primaryActionText = "Set as Default",
        matchingRules = emptyList(),
        exclusionRules = emptyList(),
    )

    private val addWidgetItem = defaultBrowserItem.copy(
        id = "setup_add_widget",
        titleText = "Try our Home screen widget",
        primaryAction = Action.AddWidget(""),
    )

    private val syncItem = defaultBrowserItem.copy(id = "setup_sync", titleText = "Sync your data")

    private fun nextStepsContent() = Content.ActionableItems(
        titleText = "Complete your setup",
        listItems = listOf(defaultBrowserItem, addWidgetItem),
    )

    private fun aMessage(
        content: Content,
        surfaces: List<Surface> = listOf(Surface.NEW_TAB_PAGE),
    ) = RemoteMessage(
        id = MESSAGE_ID,
        content = content,
        matchingRules = emptyList(),
        exclusionRules = emptyList(),
        surfaces = surfaces,
    )

    private companion object {
        const val MESSAGE_ID = "android_complete_your_setup"
    }
}
