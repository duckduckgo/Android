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

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.data.store.api.FakeSharedPreferencesProvider
import com.duckduckgo.nextsteps.api.NextStepCompletionCheck
import com.duckduckgo.remote.messaging.api.Action
import com.duckduckgo.remote.messaging.api.CardItem
import com.duckduckgo.remote.messaging.api.CardItemType
import com.duckduckgo.remote.messaging.api.Content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NextStepsCompletionCheckerTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val itemsStore: NextStepsItemsStore = RealNextStepsItemsStore(FakeSharedPreferencesProvider(), coroutineRule.testDispatcherProvider)
    private val defaultBrowserCheck = FakeCompletionCheck(defaultBrowserItem.id)
    private val addWidgetCheck = FakeCompletionCheck(addWidgetItem.id)

    private val testee = NextStepsCompletionChecker(
        itemsStore = itemsStore,
        completionChecks = FakePluginPoint(listOf(defaultBrowserCheck, addWidgetCheck)),
        dispatchers = coroutineRule.testDispatcherProvider,
    )

    @Test
    fun `when an item completes then it is reported once and recorded`() = runTest {
        val reported = mutableListOf<Set<String>>()
        defaultBrowserCheck.completed = true

        val firstRefresh = testee.refresh(items) { reported += it }
        val secondRefresh = testee.refresh(items) { reported += it }

        assertEquals(listOf(setOf(defaultBrowserItem.id)), reported)
        assertEquals(setOf(defaultBrowserItem.id), firstRefresh)
        assertEquals(setOf(defaultBrowserItem.id), secondRefresh)
        assertEquals(setOf(defaultBrowserItem.id), itemsStore.completedItemIds())
    }

    @Test
    fun `when reporting a completion is cancelled then the item is not recorded and is reported on the next refresh`() = runTest {
        val reported = mutableListOf<Set<String>>()
        defaultBrowserCheck.completed = true

        runCatching { testee.refresh(items) { throw CancellationException() } }
        testee.refresh(items) { reported += it }

        assertEquals(listOf(setOf(defaultBrowserItem.id)), reported)
    }

    @Test
    fun `when nothing new completes then nothing is reported`() = runTest {
        val reported = mutableListOf<Set<String>>()

        val completedIds = testee.refresh(items) { reported += it }

        assertTrue(reported.isEmpty())
        assertTrue(completedIds.isEmpty())
    }

    private class FakePluginPoint(private val plugins: List<NextStepCompletionCheck>) : PluginPoint<NextStepCompletionCheck> {
        override fun getPlugins(): Collection<NextStepCompletionCheck> = plugins
    }

    private class FakeCompletionCheck(override val itemId: String, var completed: Boolean = false) : NextStepCompletionCheck {
        override suspend fun isComplete(): Boolean = completed
    }

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
        val items = listOf(defaultBrowserItem, addWidgetItem)
    }
}
