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

package com.duckduckgo.app.generalsettings.showonapplaunch

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.newtabpage.api.NtpAfterIdleManager
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class FirstScreenHandlerImplTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature = mock()
    private val showOnAppLaunchFeature: ShowOnAppLaunchFeature = mock()
    private val settingsDataStore: SettingsDataStore = mock()
    private val showOnAppLaunchOptionHandler: ShowOnAppLaunchOptionHandler = mock()
    private val duckChat: DuckChat = mock()
    private val tabRepository: TabRepository = mock()
    private val idleReturnToggle: Toggle = mock()
    private val showOnAppLaunchToggle: Toggle = mock()
    private val ntpAfterIdleManager: NtpAfterIdleManager = mock()
    private val testScope = coroutineTestRule.testScope

    private lateinit var testee: FirstScreenHandlerImpl

    @Before
    fun setup() {
        whenever(androidBrowserConfigFeature.showNTPAfterIdleReturn()).thenReturn(idleReturnToggle)
        whenever(showOnAppLaunchFeature.self()).thenReturn(showOnAppLaunchToggle)
        whenever(settingsDataStore.userSelectedIdleThresholdSeconds).thenReturn(null)
        whenever(duckChat.isVoiceSessionActive()).thenReturn(false)

        testee = FirstScreenHandlerImpl(
            androidBrowserConfigFeature = androidBrowserConfigFeature,
            showOnAppLaunchFeature = showOnAppLaunchFeature,
            settingsDataStore = settingsDataStore,
            showOnAppLaunchOptionHandler = showOnAppLaunchOptionHandler,
            duckChat = duckChat,
            tabRepository = tabRepository,
            ntpAfterIdleManager = ntpAfterIdleManager,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
            appCoroutineScope = testScope,
        )
    }

    // --- Idle return enabled (covers both fresh and non-fresh launches) ---

    @Test
    fun whenIdleReturnEnabledAndElapsedExceedsTimeoutThenDelegatesWithWasIdleTrue() = runTest {
        whenever(idleReturnToggle.isEnabled()).thenReturn(true)
        whenever(idleReturnToggle.getSettings()).thenReturn("""{"defaultIdleThresholdSeconds": 300}""")
        val sixMinutesAgo = System.currentTimeMillis() - (6 * 60 * 1000)
        whenever(settingsDataStore.lastSessionBackgroundTimestamp).thenReturn(sixMinutesAgo)

        testee.onOpen(isFreshLaunch = false)
        testScope.testScheduler.advanceUntilIdle()

        verify(showOnAppLaunchOptionHandler).handleAfterInactivityOption()
    }

    @Test
    fun whenIdleReturnEnabledAndFreshLaunchAndElapsedExceedsTimeoutThenDelegatesWithWasIdleTrue() = runTest {
        whenever(idleReturnToggle.isEnabled()).thenReturn(true)
        whenever(idleReturnToggle.getSettings()).thenReturn("""{"defaultIdleThresholdSeconds": 300}""")
        val sixMinutesAgo = System.currentTimeMillis() - (6 * 60 * 1000)
        whenever(settingsDataStore.lastSessionBackgroundTimestamp).thenReturn(sixMinutesAgo)

        testee.onOpen(isFreshLaunch = true)
        testScope.testScheduler.advanceUntilIdle()

        verify(showOnAppLaunchOptionHandler).handleAfterInactivityOption()
    }

    @Test
    fun whenIdleReturnEnabledAndElapsedUnderTimeoutThenDoesNothing() = runTest {
        whenever(idleReturnToggle.isEnabled()).thenReturn(true)
        whenever(idleReturnToggle.getSettings()).thenReturn("""{"defaultIdleThresholdSeconds": 300}""")
        val thirtySecondsAgo = System.currentTimeMillis() - (30 * 1000)
        whenever(settingsDataStore.lastSessionBackgroundTimestamp).thenReturn(thirtySecondsAgo)

        testee.onOpen(isFreshLaunch = false)
        testScope.testScheduler.advanceUntilIdle()

        verifyNoInteractions(showOnAppLaunchOptionHandler)
    }

    @Test
    fun whenIdleReturnEnabledAndFreshLaunchAndElapsedUnderTimeoutThenDoesNothing() = runTest {
        whenever(idleReturnToggle.isEnabled()).thenReturn(true)
        whenever(idleReturnToggle.getSettings()).thenReturn("""{"defaultIdleThresholdSeconds": 300}""")
        val thirtySecondsAgo = System.currentTimeMillis() - (30 * 1000)
        whenever(settingsDataStore.lastSessionBackgroundTimestamp).thenReturn(thirtySecondsAgo)

        testee.onOpen(isFreshLaunch = true)
        testScope.testScheduler.advanceUntilIdle()

        verifyNoInteractions(showOnAppLaunchOptionHandler)
    }

    @Test
    fun whenIdleReturnEnabledAndNoTimestampThenDelegatesWithWasIdleFalse() = runTest {
        whenever(idleReturnToggle.isEnabled()).thenReturn(true)
        whenever(idleReturnToggle.getSettings()).thenReturn("""{"defaultIdleThresholdSeconds": 300}""")
        whenever(settingsDataStore.lastSessionBackgroundTimestamp).thenReturn(0L)

        testee.onOpen(isFreshLaunch = false)
        testScope.testScheduler.advanceUntilIdle()

        verify(showOnAppLaunchOptionHandler).handleAfterInactivityOption()
    }

    @Test
    fun whenIdleReturnEnabledAndElapsedExactlyEqualsTimeoutThenDelegatesWithWasIdleTrue() = runTest {
        whenever(idleReturnToggle.isEnabled()).thenReturn(true)
        whenever(idleReturnToggle.getSettings()).thenReturn("""{"defaultIdleThresholdSeconds": 300}""")
        val exactlyFiveMinutesAgo = System.currentTimeMillis() - (5 * 60 * 1000)
        whenever(settingsDataStore.lastSessionBackgroundTimestamp).thenReturn(exactlyFiveMinutesAgo)

        testee.onOpen(isFreshLaunch = false)
        testScope.testScheduler.advanceUntilIdle()

        verify(showOnAppLaunchOptionHandler).handleAfterInactivityOption()
    }

    @Test
    fun whenIdleReturnEnabledAndSettingsNullThenUsesDefaultTimeout() = runTest {
        whenever(idleReturnToggle.isEnabled()).thenReturn(true)
        whenever(idleReturnToggle.getSettings()).thenReturn(null)
        val sixMinutesAgo = System.currentTimeMillis() - (6 * 60 * 1000)
        whenever(settingsDataStore.lastSessionBackgroundTimestamp).thenReturn(sixMinutesAgo)

        testee.onOpen(isFreshLaunch = false)
        testScope.testScheduler.advanceUntilIdle()

        verify(showOnAppLaunchOptionHandler).handleAfterInactivityOption()
    }

    @Test
    fun whenIdleReturnEnabledAndSettingsNullAndUnderDefaultTimeoutThenDoesNothing() = runTest {
        whenever(idleReturnToggle.isEnabled()).thenReturn(true)
        whenever(idleReturnToggle.getSettings()).thenReturn(null)
        val thirtySecondsAgo = System.currentTimeMillis() - (30 * 1000)
        whenever(settingsDataStore.lastSessionBackgroundTimestamp).thenReturn(thirtySecondsAgo)

        testee.onOpen(isFreshLaunch = false)
        testScope.testScheduler.advanceUntilIdle()

        verifyNoInteractions(showOnAppLaunchOptionHandler)
    }

    @Test
    fun whenIdleReturnEnabledAndSettingsMalformedThenUsesDefaultTimeout() = runTest {
        whenever(idleReturnToggle.isEnabled()).thenReturn(true)
        whenever(idleReturnToggle.getSettings()).thenReturn("not json")
        val sixMinutesAgo = System.currentTimeMillis() - (6 * 60 * 1000)
        whenever(settingsDataStore.lastSessionBackgroundTimestamp).thenReturn(sixMinutesAgo)

        testee.onOpen(isFreshLaunch = false)
        testScope.testScheduler.advanceUntilIdle()

        verify(showOnAppLaunchOptionHandler).handleAfterInactivityOption()
    }

    @Test
    fun whenIdleReturnEnabledAndDefaultIdleThresholdSecondsMissingThenUsesDefaultTimeout() = runTest {
        whenever(idleReturnToggle.isEnabled()).thenReturn(true)
        whenever(idleReturnToggle.getSettings()).thenReturn("""{"otherKey": 30}""")
        val sixMinutesAgo = System.currentTimeMillis() - (6 * 60 * 1000)
        whenever(settingsDataStore.lastSessionBackgroundTimestamp).thenReturn(sixMinutesAgo)

        testee.onOpen(isFreshLaunch = false)
        testScope.testScheduler.advanceUntilIdle()

        verify(showOnAppLaunchOptionHandler).handleAfterInactivityOption()
    }

    @Test
    fun whenIdleReturnEnabledAndTimeoutExceededThenDoesNotCheckShowOnAppLaunch() = runTest {
        whenever(idleReturnToggle.isEnabled()).thenReturn(true)
        whenever(idleReturnToggle.getSettings()).thenReturn("""{"defaultIdleThresholdSeconds": 300}""")
        val sixMinutesAgo = System.currentTimeMillis() - (6 * 60 * 1000)
        whenever(settingsDataStore.lastSessionBackgroundTimestamp).thenReturn(sixMinutesAgo)

        testee.onOpen(isFreshLaunch = true)
        testScope.testScheduler.advanceUntilIdle()

        verify(showOnAppLaunchToggle, never()).isEnabled()
    }

    // --- Idle return disabled (legacy ShowOnAppLaunch behavior) ---

    @Test
    fun whenIdleReturnDisabledAndFreshLaunchAndShowOnAppLaunchEnabledThenDelegates() = runTest {
        whenever(idleReturnToggle.isEnabled()).thenReturn(false)
        whenever(showOnAppLaunchToggle.isEnabled()).thenReturn(true)

        testee.onOpen(isFreshLaunch = true)
        testScope.testScheduler.advanceUntilIdle()

        verify(showOnAppLaunchOptionHandler).handleAppLaunchOption()
    }

    @Test
    fun whenIdleReturnDisabledAndFreshLaunchAndShowOnAppLaunchDisabledThenDoesNothing() = runTest {
        whenever(idleReturnToggle.isEnabled()).thenReturn(false)
        whenever(showOnAppLaunchToggle.isEnabled()).thenReturn(false)

        testee.onOpen(isFreshLaunch = true)
        testScope.testScheduler.advanceUntilIdle()

        verifyNoInteractions(showOnAppLaunchOptionHandler)
    }

    @Test
    fun whenIdleReturnDisabledAndNotFreshLaunchThenDoesNothing() = runTest {
        whenever(idleReturnToggle.isEnabled()).thenReturn(false)

        testee.onOpen(isFreshLaunch = false)
        testScope.testScheduler.advanceUntilIdle()

        verifyNoInteractions(showOnAppLaunchOptionHandler)
        verify(showOnAppLaunchToggle, never()).isEnabled()
    }

    // --- Voice session active (idle return path) ---

    @Test
    fun whenIdleReturnEnabledAndElapsedExceedsTimeoutAndVoiceSessionActiveOnDuckAiTabThenDoesNothing() = runTest {
        whenever(idleReturnToggle.isEnabled()).thenReturn(true)
        whenever(idleReturnToggle.getSettings()).thenReturn("""{"defaultIdleThresholdSeconds": 300}""")
        val sixMinutesAgo = System.currentTimeMillis() - (6 * 60 * 1000)
        whenever(settingsDataStore.lastSessionBackgroundTimestamp).thenReturn(sixMinutesAgo)
        whenever(duckChat.isVoiceSessionActive()).thenReturn(true)
        whenever(duckChat.isDuckChatUrl(any())).thenReturn(true)
        val duckAiTab = TabEntity(tabId = "tab1", url = "https://duck.ai/?mode=voice-mode")
        whenever(tabRepository.getSelectedTab()).thenReturn(duckAiTab)

        testee.onOpen(isFreshLaunch = false)
        testScope.testScheduler.advanceUntilIdle()

        verifyNoInteractions(showOnAppLaunchOptionHandler)
    }

    @Test
    fun whenIdleReturnEnabledAndElapsedExceedsTimeoutAndVoiceSessionActiveOnNonDuckAiTabThenDelegates() = runTest {
        whenever(idleReturnToggle.isEnabled()).thenReturn(true)
        whenever(idleReturnToggle.getSettings()).thenReturn("""{"defaultIdleThresholdSeconds": 300}""")
        val sixMinutesAgo = System.currentTimeMillis() - (6 * 60 * 1000)
        whenever(settingsDataStore.lastSessionBackgroundTimestamp).thenReturn(sixMinutesAgo)
        whenever(duckChat.isVoiceSessionActive()).thenReturn(true)
        val nonDuckAiTab = TabEntity(tabId = "tab1", url = "https://example.com")
        whenever(tabRepository.getSelectedTab()).thenReturn(nonDuckAiTab)
        whenever(duckChat.isDuckChatUrl(any())).thenReturn(false)

        testee.onOpen(isFreshLaunch = false)
        testScope.testScheduler.advanceUntilIdle()

        verify(showOnAppLaunchOptionHandler).handleAfterInactivityOption()
    }

    @Test
    fun whenIdleReturnEnabledAndElapsedExceedsTimeoutAndNoVoiceSessionActiveThenDelegates() = runTest {
        whenever(idleReturnToggle.isEnabled()).thenReturn(true)
        whenever(idleReturnToggle.getSettings()).thenReturn("""{"defaultIdleThresholdSeconds": 300}""")
        val sixMinutesAgo = System.currentTimeMillis() - (6 * 60 * 1000)
        whenever(settingsDataStore.lastSessionBackgroundTimestamp).thenReturn(sixMinutesAgo)
        whenever(duckChat.isVoiceSessionActive()).thenReturn(false)

        testee.onOpen(isFreshLaunch = false)
        testScope.testScheduler.advanceUntilIdle()

        verify(showOnAppLaunchOptionHandler).handleAfterInactivityOption()
    }

    // --- Voice session active (legacy fresh launch path) ---

    @Test
    fun whenIdleReturnDisabledAndFreshLaunchAndShowOnAppLaunchEnabledAndVoiceSessionActiveOnDuckAiTabThenDoesNothing() = runTest {
        whenever(idleReturnToggle.isEnabled()).thenReturn(false)
        whenever(showOnAppLaunchToggle.isEnabled()).thenReturn(true)
        whenever(duckChat.isVoiceSessionActive()).thenReturn(true)
        whenever(duckChat.isDuckChatUrl(any())).thenReturn(true)
        val duckAiTab = TabEntity(tabId = "tab1", url = "https://duck.ai/?mode=voice-mode")
        whenever(tabRepository.getSelectedTab()).thenReturn(duckAiTab)

        testee.onOpen(isFreshLaunch = true)
        testScope.testScheduler.advanceUntilIdle()

        verifyNoInteractions(showOnAppLaunchOptionHandler)
    }

    @Test
    fun whenIdleReturnDisabledAndFreshLaunchAndShowOnAppLaunchEnabledAndVoiceSessionActiveOnNonDuckAiTabThenDelegates() = runTest {
        whenever(idleReturnToggle.isEnabled()).thenReturn(false)
        whenever(showOnAppLaunchToggle.isEnabled()).thenReturn(true)
        whenever(duckChat.isVoiceSessionActive()).thenReturn(true)
        val nonDuckAiTab = TabEntity(tabId = "tab1", url = "https://example.com")
        whenever(tabRepository.getSelectedTab()).thenReturn(nonDuckAiTab)
        whenever(duckChat.isDuckChatUrl(any())).thenReturn(false)

        testee.onOpen(isFreshLaunch = true)
        testScope.testScheduler.advanceUntilIdle()

        verify(showOnAppLaunchOptionHandler).handleAppLaunchOption()
    }

    @Test
    fun whenIdleReturnDisabledAndFreshLaunchAndShowOnAppLaunchEnabledAndNoVoiceSessionActiveThenDelegates() = runTest {
        whenever(idleReturnToggle.isEnabled()).thenReturn(false)
        whenever(showOnAppLaunchToggle.isEnabled()).thenReturn(true)
        whenever(duckChat.isVoiceSessionActive()).thenReturn(false)

        testee.onOpen(isFreshLaunch = true)
        testScope.testScheduler.advanceUntilIdle()

        verify(showOnAppLaunchOptionHandler).handleAppLaunchOption()
    }

    // --- Synchronous onIdleReturnTriggered ---

    @Test
    fun whenIdleReturnEnabledAndElapsedExceedsTimeoutThenNotifiesNtpAfterIdleManagerSynchronously() {
        whenever(idleReturnToggle.isEnabled()).thenReturn(true)
        whenever(idleReturnToggle.getSettings()).thenReturn("""{"defaultIdleThresholdSeconds": 300}""")
        val sixMinutesAgo = System.currentTimeMillis() - (6 * 60 * 1000)
        whenever(settingsDataStore.lastSessionBackgroundTimestamp).thenReturn(sixMinutesAgo)

        testee.onOpen(isFreshLaunch = false)

        // Called synchronously from onOpen, before any coroutine advances.
        verify(ntpAfterIdleManager).onIdleReturnTriggered()
    }

    @Test
    fun whenIdleReturnEnabledAndElapsedUnderTimeoutThenDoesNotNotifyNtpAfterIdleManager() {
        whenever(idleReturnToggle.isEnabled()).thenReturn(true)
        whenever(idleReturnToggle.getSettings()).thenReturn("""{"defaultIdleThresholdSeconds": 300}""")
        val thirtySecondsAgo = System.currentTimeMillis() - (30 * 1000)
        whenever(settingsDataStore.lastSessionBackgroundTimestamp).thenReturn(thirtySecondsAgo)

        testee.onOpen(isFreshLaunch = false)

        verify(ntpAfterIdleManager, never()).onIdleReturnTriggered()
    }

    @Test
    fun whenIdleReturnEnabledAndNoPriorTimestampThenDoesNotNotifyNtpAfterIdleManager() {
        whenever(idleReturnToggle.isEnabled()).thenReturn(true)
        whenever(idleReturnToggle.getSettings()).thenReturn("""{"defaultIdleThresholdSeconds": 300}""")
        whenever(settingsDataStore.lastSessionBackgroundTimestamp).thenReturn(0L)

        testee.onOpen(isFreshLaunch = false)

        verify(ntpAfterIdleManager, never()).onIdleReturnTriggered()
    }

    @Test
    fun whenIdleReturnDisabledThenDoesNotNotifyNtpAfterIdleManager() {
        whenever(idleReturnToggle.isEnabled()).thenReturn(false)
        val sixMinutesAgo = System.currentTimeMillis() - (6 * 60 * 1000)
        whenever(settingsDataStore.lastSessionBackgroundTimestamp).thenReturn(sixMinutesAgo)

        testee.onOpen(isFreshLaunch = false)

        verify(ntpAfterIdleManager, never()).onIdleReturnTriggered()
    }

    // --- onClose ---

    @Test
    fun whenOnCloseThenWritesTimestamp() {
        testee.onClose()

        verify(settingsDataStore).lastSessionBackgroundTimestamp = org.mockito.kotlin.any()
    }

    // --- User preference overrides RC default ---

    @Test
    fun whenUserPreferenceSetThenIgnoresRCAndUsesUserValue() = runTest {
        whenever(idleReturnToggle.isEnabled()).thenReturn(true)
        whenever(idleReturnToggle.getSettings()).thenReturn(
            """{"defaultIdleThresholdSeconds": 300}""",
        )
        // User set 60s; last backgrounded 61s ago → should trigger (61 >= 60)
        whenever(settingsDataStore.userSelectedIdleThresholdSeconds).thenReturn(60L)
        val sixtyOneSecondsAgo = System.currentTimeMillis() - (61 * 1000)
        whenever(settingsDataStore.lastSessionBackgroundTimestamp).thenReturn(sixtyOneSecondsAgo)

        testee.onOpen(isFreshLaunch = false)
        testScope.testScheduler.advanceUntilIdle()

        verify(showOnAppLaunchOptionHandler).handleAfterInactivityOption()
    }

    @Test
    fun whenUserPreferenceSetAndUnderUserThresholdThenDoesNothing() = runTest {
        whenever(idleReturnToggle.isEnabled()).thenReturn(true)
        whenever(idleReturnToggle.getSettings()).thenReturn(
            """{"defaultIdleThresholdSeconds": 300}""",
        )
        // User set 60s; RC says 300s; last backgrounded 30s ago → should NOT trigger
        whenever(settingsDataStore.userSelectedIdleThresholdSeconds).thenReturn(60L)
        val thirtySecondsAgo = System.currentTimeMillis() - (30 * 1000)
        whenever(settingsDataStore.lastSessionBackgroundTimestamp).thenReturn(thirtySecondsAgo)

        testee.onOpen(isFreshLaunch = false)
        testScope.testScheduler.advanceUntilIdle()

        verifyNoInteractions(showOnAppLaunchOptionHandler)
    }

    // --- RC default used directly ---

    @Test
    fun whenRCDefaultSetThenUsesRCDefault() = runTest {
        whenever(idleReturnToggle.isEnabled()).thenReturn(true)
        whenever(idleReturnToggle.getSettings()).thenReturn(
            """{"defaultIdleThresholdSeconds": 60}""",
        )
        // RC default 60s; last backgrounded 61s ago → should trigger
        whenever(settingsDataStore.userSelectedIdleThresholdSeconds).thenReturn(null)
        val sixtyOneSecondsAgo = System.currentTimeMillis() - (61 * 1000)
        whenever(settingsDataStore.lastSessionBackgroundTimestamp).thenReturn(sixtyOneSecondsAgo)

        testee.onOpen(isFreshLaunch = false)
        testScope.testScheduler.advanceUntilIdle()

        verify(showOnAppLaunchOptionHandler).handleAfterInactivityOption()
    }

    @Test
    fun whenRCDefaultSetAndUnderThresholdThenDoesNothing() = runTest {
        whenever(idleReturnToggle.isEnabled()).thenReturn(true)
        whenever(idleReturnToggle.getSettings()).thenReturn(
            """{"defaultIdleThresholdSeconds": 120}""",
        )
        // RC default 120s; last backgrounded 60s ago → should NOT trigger
        whenever(settingsDataStore.userSelectedIdleThresholdSeconds).thenReturn(null)
        val sixtySecondsAgo = System.currentTimeMillis() - (60 * 1000)
        whenever(settingsDataStore.lastSessionBackgroundTimestamp).thenReturn(sixtySecondsAgo)

        testee.onOpen(isFreshLaunch = false)
        testScope.testScheduler.advanceUntilIdle()

        verifyNoInteractions(showOnAppLaunchOptionHandler)
    }
}
