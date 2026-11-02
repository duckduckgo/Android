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

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.data.store.api.FakeSharedPreferencesProvider
import com.duckduckgo.nextsteps.api.NextStepCompletionCheck
import com.duckduckgo.nextsteps.impl.NextStepsItemsStore
import com.duckduckgo.nextsteps.impl.RealNextStepsItemsStore
import com.duckduckgo.remote.messaging.api.Action
import com.duckduckgo.remote.messaging.api.CardItem
import com.duckduckgo.remote.messaging.api.CardItemType
import com.duckduckgo.remote.messaging.api.Content
import com.duckduckgo.remote.messaging.api.RemoteMessage
import com.duckduckgo.remote.messaging.api.RemoteMessageModel
import com.duckduckgo.remote.messaging.api.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class NextStepsCheckListViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val activeMessage = MutableStateFlow<RemoteMessage?>(null)
    private val remoteMessageModel: RemoteMessageModel = mock {
        whenever(it.observeActiveMessages()).thenReturn(activeMessage)
    }
    private val itemsStore: NextStepsItemsStore = RealNextStepsItemsStore(FakeSharedPreferencesProvider(), coroutineRule.testDispatcherProvider)
    private val completionChecks = FakePluginPoint(
        listOf(FakeCompletionCheck(defaultBrowserItem.id), FakeCompletionCheck(addWidgetItem.id), FakeCompletionCheck(syncItem.id)),
    )

    private val testee = NextStepsCheckListViewModel(
        dispatchers = coroutineRule.testDispatcherProvider,
        remoteMessageModel = remoteMessageModel,
        itemsStore = itemsStore,
        completionChecks = completionChecks,
    )

    @Test
    fun `when there is no active message then the state is empty`() = runTest {
        val state = testee.viewState.first()

        assertNull(state.message)
        assertTrue(state.items.isEmpty())
    }

    @Test
    fun `when the active message is not a next steps message then the state is empty`() = runTest {
        activeMessage.value = aMessage(content = Content.Small(titleText = "title", descriptionText = "description"))

        val state = testee.viewState.first()

        assertNull(state.message)
        assertTrue(state.items.isEmpty())
    }

    @Test
    fun `when the active message targets another surface then the state is empty`() = runTest {
        activeMessage.value = aMessage(content = nextStepsContent(), surfaces = emptyList())

        val state = testee.viewState.first()

        assertNull(state.message)
    }

    @Test
    fun `when a next steps message is active then the title and items are exposed in config order`() = runTest {
        activeMessage.value = aMessage(content = nextStepsContent())

        val state = testee.viewState.first()

        assertEquals("Complete your setup", state.title)
        assertEquals(listOf(defaultBrowserItem, addWidgetItem, syncItem), state.items.map { it.item })
        assertTrue(state.items.none { it.completed })
    }

    @Test
    fun `when an item is already completed then it moves to the bottom flagged as completed`() = runTest {
        itemsStore.addCompletedItemIds(setOf(defaultBrowserItem.id))
        activeMessage.value = aMessage(content = nextStepsContent())

        val state = testee.viewState.first()

        assertEquals(listOf(addWidgetItem, syncItem, defaultBrowserItem), state.items.map { it.item })
        assertFalse(state.items[0].completed)
        assertFalse(state.items[1].completed)
        assertTrue(state.items[2].completed)
    }

    @Test
    fun `when an item has no completion check then it is not shown`() = runTest {
        val unsupported = defaultBrowserItem.copy(id = "setup_unknown", titleText = "Unknown")
        activeMessage.value = aMessage(content = nextStepsContent(listOf(defaultBrowserItem, unsupported)))

        val state = testee.viewState.first()

        assertEquals(listOf(defaultBrowserItem), state.items.map { it.item })
    }

    private class FakePluginPoint(private val plugins: List<NextStepCompletionCheck>) : PluginPoint<NextStepCompletionCheck> {
        override fun getPlugins(): Collection<NextStepCompletionCheck> = plugins
    }

    private class FakeCompletionCheck(override val itemId: String) : NextStepCompletionCheck {
        override suspend fun isComplete(): Boolean = false
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
        val syncItem = defaultBrowserItem.copy(id = "setup_sync", titleText = "Sync your data")
    }
}
