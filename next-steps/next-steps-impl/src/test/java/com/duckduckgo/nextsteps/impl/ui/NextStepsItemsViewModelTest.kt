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

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.data.store.api.FakeSharedPreferencesProvider
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
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class NextStepsItemsViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val activeMessage = MutableStateFlow<RemoteMessage?>(null)
    private val remoteMessageModel: RemoteMessageModel = mock {
        whenever(it.observeActiveMessages()).thenReturn(activeMessage)
    }

    private val itemsStore: NextStepsItemsStore = RealNextStepsItemsStore(FakeSharedPreferencesProvider())

    private lateinit var testee: NextStepsItemsViewModel

    @Before
    fun setUp() {
        testee = aViewModel()
    }

    private fun aViewModel() = NextStepsItemsViewModel(
        dispatchers = coroutineRule.testDispatcherProvider,
        remoteMessageModel = remoteMessageModel,
        itemsStore = itemsStore,
    )

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

    private val addWidgetItem = defaultBrowserItem.copy(id = "setup_add_widget", titleText = "Try our Home screen widget")

    private val syncItem = defaultBrowserItem.copy(id = "setup_sync", titleText = "Sync your data")

    private fun nextStepsContent() = Content.ActionableItems(
        titleText = "Complete your setup",
        listItems = listOf(defaultBrowserItem, addWidgetItem),
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
}
