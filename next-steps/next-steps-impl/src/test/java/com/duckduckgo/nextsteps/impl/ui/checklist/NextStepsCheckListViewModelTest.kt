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

import com.duckduckgo.app.global.DefaultRoleBrowserDialog
import com.duckduckgo.app.widget.WidgetType
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.data.store.api.FakeSharedPreferencesProvider
import com.duckduckgo.nextsteps.api.NextStepCompletionCheck
import com.duckduckgo.nextsteps.impl.NextStepsCompletionChecker
import com.duckduckgo.nextsteps.impl.NextStepsItemsStore
import com.duckduckgo.nextsteps.impl.RealNextStepsItemsStore
import com.duckduckgo.nextsteps.impl.ui.checklist.NextStepsCheckListViewModel.Command
import com.duckduckgo.remote.messaging.api.Action
import com.duckduckgo.remote.messaging.api.CardItem
import com.duckduckgo.remote.messaging.api.CardItemType
import com.duckduckgo.remote.messaging.api.Content
import com.duckduckgo.remote.messaging.api.RemoteMessage
import com.duckduckgo.remote.messaging.api.RemoteMessageModel
import com.duckduckgo.remote.messaging.api.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class NextStepsCheckListViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val activeMessage = MutableStateFlow<RemoteMessage?>(null)
    private val remoteMessageModel: RemoteMessageModel = mock {
        whenever(it.observeActiveMessages()).thenReturn(activeMessage)
    }
    private val itemsStore: NextStepsItemsStore = RealNextStepsItemsStore(FakeSharedPreferencesProvider(), coroutineRule.testDispatcherProvider)
    private val defaultBrowserCheck = FakeCompletionCheck(defaultBrowserItem.id)
    private val addWidgetCheck = FakeCompletionCheck(addWidgetItem.id)
    private val syncCheck = FakeCompletionCheck(syncItem.id)
    private val completionChecks = FakePluginPoint(listOf(defaultBrowserCheck, addWidgetCheck, syncCheck))
    private val defaultRoleBrowserDialog: DefaultRoleBrowserDialog = mock()

    private lateinit var testee: NextStepsCheckListViewModel
    private val commands = mutableListOf<Command>()

    @Before
    fun setUp() {
        testee = NextStepsCheckListViewModel(
            dispatchers = coroutineRule.testDispatcherProvider,
            remoteMessageModel = remoteMessageModel,
            itemsStore = itemsStore,
            completionChecker = NextStepsCompletionChecker(itemsStore, completionChecks, coroutineRule.testDispatcherProvider),
            defaultRoleBrowserDialog = defaultRoleBrowserDialog,
        )
        testee.commands().onEach { commands.add(it) }.launchIn(coroutineRule.testScope)
        testee.onViewAttached()
    }

    @Test
    fun `when there is no active message then the state is empty`() = runTest {
        advanceUntilIdle()

        assertNull(testee.viewState.value.message)
        assertTrue(testee.viewState.value.items.isEmpty())
    }

    @Test
    fun `when the active message is not a next steps message then the state is empty`() = runTest {
        activeMessage.value = aMessage(content = Content.Small(titleText = "title", descriptionText = "description"))
        advanceUntilIdle()

        assertNull(testee.viewState.value.message)
    }

    @Test
    fun `when the active message targets another surface then the state is empty`() = runTest {
        activeMessage.value = aMessage(content = nextStepsContent(), surfaces = emptyList())
        advanceUntilIdle()

        assertNull(testee.viewState.value.message)
    }

    @Test
    fun `when a next steps message is active then the title and items are exposed in config order`() = runTest {
        activeMessage.value = aMessage(content = nextStepsContent())
        advanceUntilIdle()

        val state = testee.viewState.value
        assertEquals("Complete your setup", state.title)
        assertEquals(listOf(defaultBrowserItem, addWidgetItem, syncItem), state.items.map { it.item })
        assertTrue(state.items.none { it.completed })
    }

    @Test
    fun `when an item has no completion check then it is not shown`() = runTest {
        val unsupported = defaultBrowserItem.copy(id = "setup_unknown", titleText = "Unknown")
        activeMessage.value = aMessage(content = nextStepsContent(listOf(defaultBrowserItem, unsupported)))
        advanceUntilIdle()

        assertEquals(listOf(defaultBrowserItem), testee.viewState.value.items.map { it.item })
    }

    @Test
    fun `when an item was completed before the section renders then it is shown last as completed without animation`() = runTest {
        defaultBrowserCheck.completed = true
        activeMessage.value = aMessage(content = nextStepsContent())
        advanceUntilIdle()

        val state = testee.viewState.value
        assertEquals(listOf(addWidgetItem, syncItem, defaultBrowserItem), state.items.map { it.item })
        assertTrue(state.items.last().completed)
        assertTrue(commands.isEmpty())
        assertEquals(setOf(defaultBrowserItem.id), itemsStore.completedItemIds())
    }

    @Test
    fun `when the section renders then one impression is reported and resuming does not add another`() = runTest {
        activeMessage.value = aMessage(content = nextStepsContent())
        advanceUntilIdle()
        testee.onResume(mock())
        advanceUntilIdle()

        verify(remoteMessageModel, times(1)).onMessageShown(any())
    }

    @Test
    fun `when the view is attached again then a new impression is reported`() = runTest {
        activeMessage.value = aMessage(content = nextStepsContent())
        advanceUntilIdle()

        testee.onViewDetached()
        testee.onViewAttached()
        advanceUntilIdle()

        verify(remoteMessageModel, times(2)).onMessageShown(any())
    }

    @Test
    fun `when the default browser item is clicked and the dialog is available then the dialog is launched`() = runTest {
        whenever(defaultRoleBrowserDialog.shouldShowDialog()).thenReturn(true)
        activeMessage.value = aMessage(content = nextStepsContent())
        advanceUntilIdle()

        testee.onItemClicked(defaultBrowserItem.id)
        advanceUntilIdle()

        assertEquals(listOf<Command>(Command.LaunchDefaultBrowserDialog), commands)
    }

    @Test
    fun `when the default browser item is clicked and the dialog is not available then settings are launched`() = runTest {
        whenever(defaultRoleBrowserDialog.shouldShowDialog()).thenReturn(false)
        activeMessage.value = aMessage(content = nextStepsContent())
        advanceUntilIdle()

        testee.onItemClicked(defaultBrowserItem.id)
        advanceUntilIdle()

        assertEquals(listOf<Command>(Command.LaunchDefaultBrowserSettings), commands)
    }

    @Test
    fun `when the add widget item is clicked then the widget flow is launched`() = runTest {
        activeMessage.value = aMessage(content = nextStepsContent())
        advanceUntilIdle()

        testee.onItemClicked(addWidgetItem.id)
        advanceUntilIdle()

        assertEquals(listOf<Command>(Command.LaunchAddWidget(WidgetType.SEARCH)), commands)
    }

    @Test
    fun `when a navigation item is clicked then the screen is launched with its payload`() = runTest {
        activeMessage.value = aMessage(content = nextStepsContent())
        advanceUntilIdle()

        testee.onItemClicked(syncItem.id)
        advanceUntilIdle()

        assertEquals(listOf<Command>(Command.LaunchScreen("sync", "{\"source\":\"next_steps\"}")), commands)
    }

    @Test
    fun `when a completed item is clicked then nothing is launched`() = runTest {
        defaultBrowserCheck.completed = true
        activeMessage.value = aMessage(content = nextStepsContent())
        advanceUntilIdle()

        testee.onItemClicked(defaultBrowserItem.id)
        advanceUntilIdle()

        assertTrue(commands.isEmpty())
    }

    @Test
    fun `when the clicked item is complete on resume then the completion animation runs before the item is marked completed`() = runTest {
        activeMessage.value = aMessage(content = nextStepsContent())
        advanceUntilIdle()
        testee.onItemClicked(syncItem.id)
        advanceUntilIdle()

        syncCheck.completed = true
        testee.onResume(mock())
        advanceUntilIdle()

        assertEquals(Command.AnimateItemCompletion(syncItem.id, toPosition = 2), commands.last())
        assertEquals(listOf(defaultBrowserItem, addWidgetItem, syncItem), testee.viewState.value.items.map { it.item })
        assertTrue(testee.viewState.value.items.none { it.completed })

        testee.onItemCompletionAnimated(syncItem.id)

        assertEquals(listOf(defaultBrowserItem, addWidgetItem, syncItem), testee.viewState.value.items.map { it.item })
        assertTrue(testee.viewState.value.items.last().completed)
    }

    @Test
    fun `when the clicked item is complete on resume then after the animation it moves below the remaining items`() = runTest {
        activeMessage.value = aMessage(content = nextStepsContent())
        advanceUntilIdle()
        testee.onItemClicked(defaultBrowserItem.id)
        advanceUntilIdle()

        defaultBrowserCheck.completed = true
        testee.onResume(mock())
        advanceUntilIdle()
        assertEquals(Command.AnimateItemCompletion(defaultBrowserItem.id, toPosition = 2), commands.last())
        testee.onItemCompletionAnimated(defaultBrowserItem.id)

        assertEquals(listOf(addWidgetItem, syncItem, defaultBrowserItem), testee.viewState.value.items.map { it.item })
        assertTrue(testee.viewState.value.items.last().completed)
        assertFalse(testee.viewState.value.items.first().completed)
    }

    @Test
    fun `when another item is already completed then the newly completed item lands right below the remaining ones`() = runTest {
        syncCheck.completed = true
        activeMessage.value = aMessage(content = nextStepsContent())
        advanceUntilIdle()
        testee.onItemClicked(defaultBrowserItem.id)
        advanceUntilIdle()

        defaultBrowserCheck.completed = true
        testee.onResume(mock())
        advanceUntilIdle()

        assertEquals(Command.AnimateItemCompletion(defaultBrowserItem.id, toPosition = 1), commands.last())
        assertEquals(listOf(defaultBrowserItem, addWidgetItem, syncItem), testee.viewState.value.items.map { it.item })
        testee.onItemCompletionAnimated(defaultBrowserItem.id)
        assertEquals(listOf(addWidgetItem, defaultBrowserItem, syncItem), testee.viewState.value.items.map { it.item })
    }

    @Test
    fun `when items complete one after another then the completed ones are ordered newest first`() = runTest {
        activeMessage.value = aMessage(content = nextStepsContent())
        advanceUntilIdle()

        testee.onItemClicked(defaultBrowserItem.id)
        advanceUntilIdle()
        defaultBrowserCheck.completed = true
        testee.onResume(mock())
        advanceUntilIdle()
        assertEquals(Command.AnimateItemCompletion(defaultBrowserItem.id, toPosition = 2), commands.last())
        testee.onItemCompletionAnimated(defaultBrowserItem.id)
        assertEquals(listOf(addWidgetItem, syncItem, defaultBrowserItem), testee.viewState.value.items.map { it.item })

        testee.onItemClicked(addWidgetItem.id)
        advanceUntilIdle()
        addWidgetCheck.completed = true
        testee.onResume(mock())
        advanceUntilIdle()
        assertEquals(Command.AnimateItemCompletion(addWidgetItem.id, toPosition = 1), commands.last())
        testee.onItemCompletionAnimated(addWidgetItem.id)
        assertEquals(listOf(syncItem, addWidgetItem, defaultBrowserItem), testee.viewState.value.items.map { it.item })
    }

    @Test
    fun `when the item right above the completed ones completes then it keeps its position`() = runTest {
        activeMessage.value = aMessage(content = nextStepsContent())
        advanceUntilIdle()
        testee.onItemClicked(defaultBrowserItem.id)
        advanceUntilIdle()
        defaultBrowserCheck.completed = true
        testee.onResume(mock())
        advanceUntilIdle()
        testee.onItemCompletionAnimated(defaultBrowserItem.id)
        assertEquals(listOf(addWidgetItem, syncItem, defaultBrowserItem), testee.viewState.value.items.map { it.item })

        testee.onItemClicked(syncItem.id)
        advanceUntilIdle()
        syncCheck.completed = true
        testee.onResume(mock())
        advanceUntilIdle()

        assertEquals(Command.AnimateItemCompletion(syncItem.id, toPosition = 1), commands.last())
        testee.onItemCompletionAnimated(syncItem.id)
        assertEquals(listOf(addWidgetItem, syncItem, defaultBrowserItem), testee.viewState.value.items.map { it.item })
    }

    @Test
    fun `when an item completes without being clicked then it is marked completed without animation`() = runTest {
        activeMessage.value = aMessage(content = nextStepsContent())
        advanceUntilIdle()

        addWidgetCheck.completed = true
        testee.onResume(mock())
        advanceUntilIdle()

        assertTrue(commands.isEmpty())
        assertEquals(listOf(defaultBrowserItem, syncItem, addWidgetItem), testee.viewState.value.items.map { it.item })
        assertTrue(testee.viewState.value.items.last().completed)
    }

    @Test
    fun `when the clicked item completes after its action opened a full screen then it is marked completed without animation`() = runTest {
        activeMessage.value = aMessage(content = nextStepsContent())
        advanceUntilIdle()
        testee.onItemClicked(syncItem.id)
        advanceUntilIdle()

        testee.onStop(mock())
        syncCheck.completed = true
        testee.onResume(mock())
        advanceUntilIdle()

        assertTrue(commands.none { it is Command.AnimateItemCompletion })
        assertEquals(listOf(defaultBrowserItem, addWidgetItem, syncItem), testee.viewState.value.items.map { it.item })
        assertTrue(testee.viewState.value.items.last().completed)
    }

    @Test
    fun `when the view is detached after a click then no animation runs on the next attach`() = runTest {
        activeMessage.value = aMessage(content = nextStepsContent())
        advanceUntilIdle()
        testee.onItemClicked(syncItem.id)
        advanceUntilIdle()

        testee.onViewDetached()
        syncCheck.completed = true
        testee.onViewAttached()
        advanceUntilIdle()

        assertTrue(commands.none { it is Command.AnimateItemCompletion })
        assertTrue(testee.viewState.value.items.last().completed)
    }

    @Test
    fun `when the section is dismissed then the message is dismissed once`() = runTest {
        val message = aMessage(content = nextStepsContent())
        activeMessage.value = message
        advanceUntilIdle()

        testee.onSectionDismissed()
        testee.onSectionDismissed()
        advanceUntilIdle()

        verify(remoteMessageModel, times(1)).onMessageDismissed(message)
    }

    @Test
    fun `when every item is already completed then the section stays visible`() = runTest {
        defaultBrowserCheck.completed = true
        addWidgetCheck.completed = true
        syncCheck.completed = true
        activeMessage.value = aMessage(content = nextStepsContent())
        advanceUntilIdle()

        assertEquals(3, testee.viewState.value.items.size)
        assertTrue(testee.viewState.value.items.all { it.completed })
        verify(remoteMessageModel, never()).onMessageDismissed(any())
    }

    @Test
    fun `when all items complete outside the page then resuming keeps the completed section visible`() = runTest {
        activeMessage.value = aMessage(content = nextStepsContent())
        advanceUntilIdle()
        testee.onStop(mock())

        defaultBrowserCheck.completed = true
        addWidgetCheck.completed = true
        syncCheck.completed = true
        testee.onResume(mock())
        advanceUntilIdle()

        assertEquals(3, testee.viewState.value.items.size)
        assertTrue(testee.viewState.value.items.all { it.completed })
        assertTrue(commands.isEmpty())
        verify(remoteMessageModel, never()).onMessageDismissed(any())
    }

    @Test
    fun `when the last clicked item completes then the section stays visible after the row animation`() = runTest {
        defaultBrowserCheck.completed = true
        addWidgetCheck.completed = true
        activeMessage.value = aMessage(content = nextStepsContent())
        advanceUntilIdle()
        testee.onItemClicked(syncItem.id)
        advanceUntilIdle()

        syncCheck.completed = true
        testee.onResume(mock())
        advanceUntilIdle()

        assertTrue(commands.last() is Command.AnimateItemCompletion)
        assertFalse(testee.viewState.value.items.all { it.completed })
        testee.onItemCompletionAnimated(syncItem.id)

        assertEquals(3, testee.viewState.value.items.size)
        assertTrue(testee.viewState.value.items.all { it.completed })
        verify(remoteMessageModel, never()).onMessageDismissed(any())
    }

    @Test
    fun `when the completed section is dismissed by the user then the message is dismissed once`() = runTest {
        defaultBrowserCheck.completed = true
        val message = aMessage(content = nextStepsContent(listOf(defaultBrowserItem)))
        activeMessage.value = message
        advanceUntilIdle()

        testee.onSectionDismissed()
        testee.onSectionDismissed()
        advanceUntilIdle()

        verify(remoteMessageModel, times(1)).onMessageDismissed(message)
    }

    private class FakePluginPoint(private val plugins: List<NextStepCompletionCheck>) : PluginPoint<NextStepCompletionCheck> {
        override fun getPlugins(): Collection<NextStepCompletionCheck> = plugins
    }

    private class FakeCompletionCheck(override val itemId: String, var completed: Boolean = false) : NextStepCompletionCheck {
        override suspend fun isComplete(): Boolean = completed
    }

    private fun nextStepsContent(items: List<CardItem> = listOf(defaultBrowserItem, addWidgetItem, syncItem)) = Content.ActionableItems(
        titleText = "Complete your setup",
        listItems = items,
    )

    private fun aMessage(
        content: Content,
        surfaces: List<Surface> = listOf(Surface.NEW_TAB_PAGE),
    ) = RemoteMessage(
        id = "android_complete_your_setup",
        content = content,
        matchingRules = emptyList(),
        exclusionRules = emptyList(),
        surfaces = surfaces,
    )

    private companion object {
        val defaultBrowserItem = CardItem.ListItem(
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
        val addWidgetItem = defaultBrowserItem.copy(
            id = "setup_add_widget",
            titleText = "Try our Home screen widget",
            primaryAction = Action.AddWidget(""),
        )
        val syncItem = defaultBrowserItem.copy(
            id = "setup_sync",
            titleText = "Sync your data",
            primaryAction = Action.Navigation("sync", mapOf("payload" to "{\"source\":\"next_steps\"}")),
        )
    }
}
