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

package com.duckduckgo.duckchat.impl.voice

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class RealVoiceSessionStateManagerTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val tabRepository: TabRepository = mock()
    private val tabsFlow = MutableStateFlow<List<TabEntity>>(emptyList())
    private val duckChatFeature: DuckChatFeature = mock()
    private val voiceChatServiceToggle: Toggle = mock()

    private lateinit var testee: RealVoiceSessionStateManager

    @After
    fun teardown() {
        // Cancels the flowTabs collect coroutine so runTest doesn't fail with UncompletedCoroutinesError
        testee.onVoiceSessionEnded()
    }

    @Before
    fun setup() {
        whenever(tabRepository.flowTabs).thenReturn(tabsFlow)
        whenever(duckChatFeature.duckAiVoiceChatService()).thenReturn(voiceChatServiceToggle)
        whenever(voiceChatServiceToggle.isEnabled()).thenReturn(true)
        testee = RealVoiceSessionStateManager(
            context = context,
            tabRepository = tabRepository,
            appCoroutineScope = coroutineTestRule.testScope,
            duckChatFeature = duckChatFeature,
        )
    }

    @Test
    fun whenCreatedThenVoiceSessionIsNotActive() {
        assertFalse(testee.isVoiceSessionActive)
    }

    @Test
    fun whenVoiceSessionStartedThenIsVoiceSessionActiveIsTrue() {
        testee.onVoiceSessionStarted(TAB_ID)

        assertTrue(testee.isVoiceSessionActive)
    }

    @Test
    fun whenVoiceSessionEndedThenIsVoiceSessionActiveIsFalse() {
        testee.onVoiceSessionStarted(TAB_ID)
        testee.onVoiceSessionEnded()

        assertFalse(testee.isVoiceSessionActive)
    }

    @Test
    fun whenVoiceSessionEndedWithoutStartThenIsVoiceSessionActiveIsFalse() {
        testee.onVoiceSessionEnded()

        assertFalse(testee.isVoiceSessionActive)
    }

    @Test
    fun whenVoiceSessionStartedMultipleTimesThenIsVoiceSessionActiveIsTrue() {
        testee.onVoiceSessionStarted(TAB_ID)
        testee.onVoiceSessionStarted(TAB_ID)

        assertTrue(testee.isVoiceSessionActive)
    }

    @Test
    fun whenFreshLaunchAndVoiceSessionWasActiveThenIsVoiceSessionActiveIsFalse() {
        testee.onVoiceSessionStarted(TAB_ID)
        testee.onOpen(isFreshLaunch = true)

        assertFalse(testee.isVoiceSessionActive)
    }

    @Test
    fun whenNotFreshLaunchAndVoiceSessionWasActiveThenIsVoiceSessionActiveRemainsTrue() {
        testee.onVoiceSessionStarted(TAB_ID)
        testee.onOpen(isFreshLaunch = false)

        assertTrue(testee.isVoiceSessionActive)
    }

    @Test
    fun whenFreshLaunchAndNoActiveSessionThenRemainsInactive() {
        testee.onOpen(isFreshLaunch = true)

        assertFalse(testee.isVoiceSessionActive)
    }

    @Test
    fun whenExitAndVoiceSessionWasActiveThenIsVoiceSessionActiveIsFalse() {
        testee.onVoiceSessionStarted(TAB_ID)
        testee.onExit()

        assertFalse(testee.isVoiceSessionActive)
    }

    @Test
    fun whenExitAndNoActiveSessionThenRemainsInactive() {
        testee.onExit()

        assertFalse(testee.isVoiceSessionActive)
    }

    @Test
    fun whenVoiceSessionStartedWithBlankTabIdThenSessionIsActiveButTabRemovalNotTracked() = coroutineTestRule.testScope.runTest {
        tabsFlow.value = listOf(tabEntity(TAB_ID))
        testee.onVoiceSessionStarted("")

        tabsFlow.value = emptyList()
        advanceUntilIdle()

        // Session stays active — no tab to track, so tab removal has no effect
        assertTrue(testee.isVoiceSessionActive)
        testee.onVoiceSessionEnded()
    }

    @Test
    fun whenActiveTabIsClosedThenSessionEnded() = coroutineTestRule.testScope.runTest {
        tabsFlow.value = listOf(tabEntity(TAB_ID))
        testee.onVoiceSessionStarted(TAB_ID)

        tabsFlow.value = emptyList()
        advanceUntilIdle()

        assertFalse(testee.isVoiceSessionActive)
    }

    @Test
    fun whenDifferentTabIsClosedThenSessionRemainsActive() = coroutineTestRule.testScope.runTest {
        tabsFlow.value = listOf(tabEntity(TAB_ID), tabEntity(OTHER_TAB_ID))
        testee.onVoiceSessionStarted(TAB_ID)

        tabsFlow.value = listOf(tabEntity(TAB_ID))
        advanceUntilIdle()

        assertTrue(testee.isVoiceSessionActive)
        testee.onVoiceSessionEnded() // cancel collect coroutine before runTest checks for leaks
    }

    @Test
    fun whenNewTabAddedThenSessionRemainsActive() = coroutineTestRule.testScope.runTest {
        tabsFlow.value = listOf(tabEntity(TAB_ID))
        testee.onVoiceSessionStarted(TAB_ID)

        tabsFlow.value = listOf(tabEntity(TAB_ID), tabEntity(OTHER_TAB_ID))
        advanceUntilIdle()

        assertTrue(testee.isVoiceSessionActive)
        testee.onVoiceSessionEnded() // cancel collect coroutine before runTest checks for leaks
    }

    @Test
    fun whenSessionEndedManuallyAndActiveTabSubsequentlyRemovedThenNoAdditionalEffect() = coroutineTestRule.testScope.runTest {
        tabsFlow.value = listOf(tabEntity(TAB_ID))
        testee.onVoiceSessionStarted(TAB_ID)
        testee.onVoiceSessionEnded()

        tabsFlow.value = emptyList()
        advanceUntilIdle()

        assertFalse(testee.isVoiceSessionActive)
    }

    @Test
    fun whenVoiceSessionStartedAndServiceFlagDisabledThenSessionIsActive() = coroutineTestRule.testScope.runTest {
        whenever(voiceChatServiceToggle.isEnabled()).thenReturn(false)
        testee = RealVoiceSessionStateManager(
            context = context,
            tabRepository = tabRepository,
            appCoroutineScope = coroutineTestRule.testScope,
            duckChatFeature = duckChatFeature,
        )

        testee.onVoiceSessionStarted(TAB_ID)

        assertTrue(testee.isVoiceSessionActive)
        testee.onVoiceSessionEnded() // cancel collect coroutine before runTest checks for leaks
    }

    @Test
    fun whenVoiceSessionEndedAndServiceFlagDisabledThenSessionIsInactive() = coroutineTestRule.testScope.runTest {
        whenever(voiceChatServiceToggle.isEnabled()).thenReturn(false)
        testee = RealVoiceSessionStateManager(
            context = context,
            tabRepository = tabRepository,
            appCoroutineScope = coroutineTestRule.testScope,
            duckChatFeature = duckChatFeature,
        )
        testee.onVoiceSessionStarted(TAB_ID)

        testee.onVoiceSessionEnded()

        assertFalse(testee.isVoiceSessionActive)
    }

    private fun tabEntity(tabId: String) = TabEntity(tabId = tabId)

    companion object {
        private const val TAB_ID = "test-tab-id"
        private const val OTHER_TAB_ID = "other-tab-id"
    }
}
