/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.dispatchers

import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.common.test.CoroutineTestRule
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

class ExternalIntentProcessingStateTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var mockTabRepository: TabRepository

    private val selectedTabFlow = MutableStateFlow<TabEntity?>(null)
    private lateinit var testee: ExternalIntentProcessingState

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(mockTabRepository.flowSelectedTab).thenReturn(selectedTabFlow)

        testee = ExternalIntentProcessingStateImpl(
            coroutineScope = TestScope(coroutineRule.testDispatcher),
            tabRepository = mockTabRepository,
        )
    }

    @Test
    fun `when initialized then hasPendingTabLaunch is false`() = runTest {
        assertFalse(testee.hasPendingTabLaunch)
    }

    @Test
    fun `when onIntentRequestToChangeTab called then hasPendingTabLaunch is true`() = runTest {
        testee.onIntentRequestToChangeTab()

        assertTrue(testee.hasPendingTabLaunch)
    }

    @Test
    fun `when tab with url is selected then hasPendingTabLaunch becomes false`() = runTest {
        testee.onIntentRequestToChangeTab()
        assertTrue(testee.hasPendingTabLaunch)

        val tabWithUrl = TabEntity(
            tabId = "tab1",
            url = "https://example.com",
            title = "Example",
        )
        selectedTabFlow.value = tabWithUrl
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        assertFalse(testee.hasPendingTabLaunch)
    }

    @Test
    fun `when tab with blank url is selected then hasPendingTabLaunch remains true`() = runTest {
        testee.onIntentRequestToChangeTab()
        assertTrue(testee.hasPendingTabLaunch)

        val tabWithBlankUrl = TabEntity(
            tabId = "tab1",
            url = "",
            title = "New Tab",
        )
        selectedTabFlow.value = tabWithBlankUrl
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        assertTrue(testee.hasPendingTabLaunch)
    }

    @Test
    fun `when tab with null url is selected then hasPendingTabLaunch remains true`() = runTest {
        testee.onIntentRequestToChangeTab()
        assertTrue(testee.hasPendingTabLaunch)

        val tabWithNullUrl = TabEntity(
            tabId = "tab1",
            url = null,
            title = "New Tab",
        )
        selectedTabFlow.value = tabWithNullUrl
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        assertTrue(testee.hasPendingTabLaunch)
    }

    @Test
    fun `when multiple tabs with urls are selected then hasPendingTabLaunch stays false`() = runTest {
        testee.onIntentRequestToChangeTab()

        val firstTab = TabEntity(
            tabId = "tab1",
            url = "https://example.com",
            title = "Example",
        )
        selectedTabFlow.value = firstTab
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        assertFalse(testee.hasPendingTabLaunch)

        val secondTab = TabEntity(
            tabId = "tab2",
            url = "https://another.com",
            title = "Another",
        )
        selectedTabFlow.value = secondTab
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        assertFalse(testee.hasPendingTabLaunch)
    }

    @Test
    fun `when null tab is selected then hasPendingTabLaunch is unchanged`() = runTest {
        testee.onIntentRequestToChangeTab()
        assertTrue(testee.hasPendingTabLaunch)

        selectedTabFlow.value = null
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        assertTrue(testee.hasPendingTabLaunch)
    }

    @Test
    fun `when initialized then hasPendingDuckAiOpen is false`() = runTest {
        assertFalse(testee.hasPendingDuckAiOpen)
    }

    @Test
    fun `when onIntentRequestToOpenDuckAi called then hasPendingDuckAiOpen is true`() = runTest {
        testee.onIntentRequestToOpenDuckAi()

        assertTrue(testee.hasPendingDuckAiOpen)
    }

    @Test
    fun `when onDuckAiClosed called then hasPendingDuckAiOpen is false`() = runTest {
        testee.onIntentRequestToOpenDuckAi()
        assertTrue(testee.hasPendingDuckAiOpen)

        testee.onDuckAiClosed()

        assertFalse(testee.hasPendingDuckAiOpen)
    }

    @Test
    fun `when initialized then hasPendingSnackbar is false`() = runTest {
        assertFalse(testee.hasPendingSnackbar)
    }

    @Test
    fun `when onIntentRequestToShowSnackbar called then hasPendingSnackbar is true`() = runTest {
        testee.onIntentRequestToShowSnackbar()

        assertTrue(testee.hasPendingSnackbar)
    }

    @Test
    fun `when onPendingSnackbarDisplayed called then hasPendingSnackbar is false`() = runTest {
        testee.onIntentRequestToShowSnackbar()
        assertTrue(testee.hasPendingSnackbar)

        testee.onPendingSnackbarDisplayed()

        assertFalse(testee.hasPendingSnackbar)
    }
}
