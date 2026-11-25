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
package com.duckduckgo.app.browser.newaddressbaroption

import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.api.DuckChatInputModeState
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState.InputMode
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class DuckAiToggleMigrationObserverTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val migrationFeature = FakeFeatureToggleFactory.create(DuckAiToggleMigrationFeature::class.java)
    private val store = InMemoryMigrationStore()
    private val duckChat: DuckChat = mock()
    private val inputModeState: DuckChatInputModeState = mock()
    private val userStageStore: UserStageStore = mock()
    private val statisticsDataStore: StatisticsDataStore = mock()
    private val pickerDataStore: NewAddressBarPickerDataStore = mock()
    private val legacyPickerStore: LegacyAddressBarOptionStore = mock()
    private val onboardingStore: OnboardingStore = mock()
    private val pixels = RecordingPixels()
    private val timeProvider: CurrentTimeProvider = mock()

    // The user's input screen setting as DuckChat stores it: null until something writes it.
    private var inputScreenSetting: Boolean? = null
    private var inputScreenFlow = MutableStateFlow(false)

    // The user's Duck.ai on/off setting as stored: null until something writes it, which reads as on.
    private var duckAiUserSetting: Boolean? = null
    private var remoteDuckAiEnabled = true

    // DuckChat's in-memory cache behind isEnabled() and inputModeCapability, filled asynchronously after process start.
    private var cacheFilled = false
    private var capabilityFlow = MutableStateFlow(InputMode.SEARCH_ONLY)
    private var cacheFillDelay = CACHE_FILL_DELAY
    private val setInputScreenCalls = mutableListOf<Boolean>()
    private var pickerWasShown = false
    private var now = FLIP_TIME
    private var settingWritesFail = false
    private var beforeSettingWrite: () -> Unit = {}
    private lateinit var gate: DuckAiToggleMigrationGate

    @Before
    fun setUp() = runTest {
        migrationFeature.migration().setRawStoredState(Toggle.State(enable = true))
        whenever(duckChat.isEnabled()).thenAnswer { cacheFilled && remoteDuckAiEnabled && (duckAiUserSetting ?: true) }
        whenever(inputModeState.inputModeCapability).thenAnswer { capabilityFlow }
        whenever(inputModeState.getSelectedInputMode()).thenAnswer {
            if ((duckAiUserSetting ?: true) && inputScreenSetting == true) InputMode.SEARCH_AND_DUCK_AI else InputMode.SEARCH_ONLY
        }
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.ESTABLISHED)
        whenever(statisticsDataStore.duckaiRetentionAtb).thenReturn("v400-1")
        whenever(pickerDataStore.displayCount()).thenReturn(0)
        whenever(pickerDataStore.wasShown()).thenAnswer { pickerWasShown }
        whenever(pickerDataStore.setAsShown()).thenAnswer {
            pickerWasShown = true
            Unit
        }
        whenever(legacyPickerStore.wasShown()).thenReturn(false)
        whenever(onboardingStore.getInputScreenSelection()).thenReturn(null)
        whenever(timeProvider.currentTimeMillis()).thenAnswer { now }

        whenever(duckChat.isInputScreenEverEnabled()).thenAnswer { inputScreenSetting != null }
        whenever(duckChat.observeInputScreenUserSettingEnabled()).thenAnswer { inputScreenFlow }
        whenever(duckChat.setInputScreenUserSetting(any())).thenAnswer {
            beforeSettingWrite()
            if (settingWritesFail) throw IllegalStateException("disk full")
            val enabled = it.getArgument<Boolean>(0)
            setInputScreenCalls += enabled
            inputScreenSetting = enabled
            inputScreenFlow.value = enabled
            recache()
            Unit
        }
    }

    @Test
    fun `when never asked Duck AI user then turns Search and Duck AI on once and marks the picker shown`() = runTest {
        coldStart()

        assertEquals(listOf(true), setInputScreenCalls)
        assertEquals(true, pickerWasShown)
        assertEquals(MigrationState.Migrated(atMillis = FLIP_TIME), store.state)
        assertEquals(1, pixels.flips)
    }

    @Test
    fun `when migrated user cold starts again then does not flip again`() = runTest {
        coldStart()
        coldStart()

        assertEquals(listOf(true), setInputScreenCalls)
        assertEquals(1, pixels.flips)
    }

    @Test
    fun `when migration toggle is off then flips nobody and writes no marker`() = runTest {
        migrationFeature.migration().setRawStoredState(Toggle.State(enable = false))

        coldStart()

        assertNotFlipped(expectedState = MigrationState.NotEvaluated)
    }

    @Test
    fun `when the gate opens then the flip has already landed`() = runTest {
        var gateOpenWhenSettingWritten: Boolean? = null
        beforeSettingWrite = { gateOpenWhenSettingWritten = runBlocking { gate.awaitOpen(1.milliseconds) } }

        coldStart()

        assertEquals(false, gateOpenWhenSettingWritten)
    }

    @Test
    fun `when migration toggle is off then the gate still opens`() = runTest {
        migrationFeature.migration().setRawStoredState(Toggle.State(enable = false))

        coldStart()

        assertGateOpen()
    }

    @Test
    fun `when toggle turns on after it was off then evaluates like a first run`() = runTest {
        migrationFeature.migration().setRawStoredState(Toggle.State(enable = false))
        coldStart()
        migrationFeature.migration().setRawStoredState(Toggle.State(enable = true))

        coldStart()

        assertEquals(listOf(true), setInputScreenCalls)
    }

    // Flow: V2 picker, confirm Search only, on a version where that confirm did not write the setting.
    @Test
    fun `when user confirmed Search only on the V2 picker then is never flipped`() = runTest {
        givenPicker(wasShown = true, displayCount = 1)

        coldStart()

        assertNotFlipped()
    }

    // Flow: V2 picker shown twice without a confirm.
    @Test
    fun `when V2 picker was displayed twice without a confirm then is never flipped`() = runTest {
        givenPicker(wasShown = true, displayCount = 2)

        coldStart()

        assertNotFlipped()
    }

    // Flow: dev skip or Quick Setup skip marks the picker shown without displaying it.
    @Test
    fun `when V2 picker is marked shown without a display then is never flipped`() = runTest {
        givenPicker(wasShown = true, displayCount = 0)

        coldStart()

        assertNotFlipped()
    }

    @Test
    fun `when V2 picker was displayed once but not yet marked shown then is never flipped`() = runTest {
        givenPicker(wasShown = false, displayCount = 1)

        coldStart()

        assertNotFlipped()
    }

    // Flow: V1 picker (5.249.0 to 5.284.x), which never wrote the setting.
    @Test
    fun `when user saw the V1 picker then is never flipped`() = runTest {
        whenever(legacyPickerStore.wasShown()).thenReturn(true)

        coldStart()

        assertNotFlipped()
    }

    // Flow: onboarding pick stored but never written (the drop(1) race).
    @Test
    fun `when onboarding stored a Search only pick then is never flipped`() = runTest {
        whenever(onboardingStore.getInputScreenSelection()).thenReturn(false)

        coldStart()

        assertNotFlipped()
    }

    // Flow: custom Duck.ai onboarding where the End CTA never showed.
    @Test
    fun `when onboarding stored a Search and Duck AI pick then is flipped`() = runTest {
        whenever(onboardingStore.getInputScreenSelection()).thenReturn(true)

        coldStart()

        assertEquals(listOf(true), setInputScreenCalls)
    }

    // Flows: onboarding pick, Settings, the V2 picker on versions where its confirm writes it, and Quick Setup all write the setting.
    @Test
    fun `when user chose Search only in onboarding or Settings then is never flipped`() = runTest {
        inputScreenSetting = false

        coldStart()

        assertNotFlipped()
    }

    @Test
    fun `when user turned Duck AI off then is never flipped`() = runTest {
        duckAiUserSetting = false

        coldStart()

        assertNotFlipped()
    }

    @Test
    fun `when DuckChat fills its cache slowly then the early false does not skip the user`() = runTest {
        cacheFillDelay = 2.seconds

        coldStart()

        assertEquals(listOf(true), setInputScreenCalls)
        assertEquals(MigrationState.Migrated(atMillis = FLIP_TIME), store.state)
    }

    @Test
    fun `when DuckChat has not filled its cache by the timeout then skips and a later start flips`() = runTest {
        cacheFillDelay = 10.seconds
        coldStart()
        assertNotFlipped()
        assertGateOpen()

        cacheFillDelay = CACHE_FILL_DELAY
        coldStart()

        assertEquals(listOf(true), setInputScreenCalls)
    }

    @Test
    fun `when remote Duck AI feature is off then is never flipped`() = runTest {
        remoteDuckAiEnabled = false

        coldStart()

        assertNotFlipped()
    }

    @Test
    fun `when user never prompted Duck AI then is never flipped`() = runTest {
        whenever(statisticsDataStore.duckaiRetentionAtb).thenReturn(null)

        coldStart()

        assertNotFlipped()
    }

    @Test
    fun `when user is still onboarding then is never flipped`() = runTest {
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.DAX_ONBOARDING)

        coldStart()

        assertNotFlipped()
    }

    @Test
    fun `when user skipped for never prompting Duck AI later prompts it then a later cold start flips them`() = runTest {
        whenever(statisticsDataStore.duckaiRetentionAtb).thenReturn(null)
        coldStart()
        assertNotFlipped()
        whenever(statisticsDataStore.duckaiRetentionAtb).thenReturn("v400-1")

        coldStart()

        assertEquals(listOf(true), setInputScreenCalls)
        assertEquals(MigrationState.Migrated(atMillis = FLIP_TIME), store.state)
        assertEquals(1, pixels.flips)
    }

    @Test
    fun `when skipped user answered the picker before becoming eligible then is never flipped`() = runTest {
        whenever(statisticsDataStore.duckaiRetentionAtb).thenReturn(null)
        coldStart()
        givenPicker(wasShown = true, displayCount = 1)
        whenever(statisticsDataStore.duckaiRetentionAtb).thenReturn("v400-1")

        coldStart()

        assertNotFlipped()
        assertGateOpen()
    }

    @Test
    fun `when skipped user is still not eligible then the gate opens after each evaluation`() = runTest {
        whenever(statisticsDataStore.duckaiRetentionAtb).thenReturn(null)
        coldStart()

        coldStart()

        assertNotFlipped()
        assertGateOpen()
    }

    @Test
    fun `when user reverted then is never evaluated again even if they look eligible`() = runTest {
        store.state = MigrationState.Reverted

        coldStart()

        assertNotFlipped(expectedState = MigrationState.Reverted)
        assertGateOpen()
    }

    @Test
    fun `when migrated user's flip landed then is never evaluated again`() = runTest {
        inputScreenSetting = true
        store.state = MigrationState.Migrated(atMillis = FLIP_TIME)

        coldStart()

        assertEquals(emptyList<Boolean>(), setInputScreenCalls)
        assertEquals(0, pixels.flips)
        assertEquals(MigrationState.Migrated(atMillis = FLIP_TIME), store.state)
    }

    @Test
    fun `when the stored state is unrecognized then is never flipped`() = runTest {
        store.state = MigrationState.Unrecognized

        coldStart()

        assertNotFlipped(expectedState = MigrationState.Unrecognized)
    }

    @Test
    fun `when migrated user turns the toggle off then it sticks and the revert is reported once`() = runTest {
        coldStart()
        now = FLIP_TIME + TimeUnit.DAYS.toMillis(3)

        userTurnsToggle(false)
        coldStart()
        coldStart()

        assertEquals(listOf(true, false), setInputScreenCalls)
        assertEquals(MigrationState.Reverted, store.state)
        assertEquals(listOf(TimeUnit.DAYS.toMillis(3)), pixels.reverts)
    }

    @Test
    fun `when migrated user turns Duck AI off then the revert is reported once as duckai_off`() = runTest {
        coldStart()

        userTurnsDuckAi(false)
        coldStart()

        assertEquals(MigrationState.Reverted, store.state)
        assertEquals(listOf(MigrationRevert.DUCKAI_OFF), pixels.revertKinds)
    }

    @Test
    fun `when migrated user turns the toggle off then the revert is reported as toggle_off`() = runTest {
        coldStart()

        userTurnsToggle(false)

        assertEquals(listOf(MigrationRevert.TOGGLE_OFF), pixels.revertKinds)
    }

    @Test
    fun `when migrated user turns the toggle off after turning Duck AI off then no second revert is reported`() = runTest {
        coldStart()
        userTurnsDuckAi(false)

        userTurnsToggle(false)
        coldStart()

        assertEquals(listOf(MigrationRevert.DUCKAI_OFF), pixels.revertKinds)
    }

    @Test
    fun `when the remote Duck AI feature turns off for a migrated user then no revert is reported`() = runTest {
        coldStart()
        remoteTurnsDuckAi(false)

        coldStart()

        assertEquals(MigrationState.Migrated(atMillis = FLIP_TIME), store.state)
        assertEquals(emptyList<MigrationRevert>(), pixels.revertKinds)
    }

    @Test
    fun `when the remote Duck AI feature turns off while a migrated user's app runs then no revert is reported`() = runTest {
        coldStart()

        remoteTurnsDuckAi(false)

        assertEquals(MigrationState.Migrated(atMillis = FLIP_TIME), store.state)
        assertEquals(emptyList<MigrationRevert>(), pixels.revertKinds)
    }

    @Test
    fun `when stored setting is on and the flow starts at false then no revert is reported`() = runTest {
        coldStart()
        inputScreenFlow.value = false // a fresh process emits false before the stored value loads
        coroutineRule.testScope.advanceUntilIdle()

        inputScreenFlow.value = true
        coroutineRule.testScope.advanceUntilIdle()

        assertEquals(MigrationState.Migrated(atMillis = FLIP_TIME), store.state)
        assertEquals(emptyList<Long>(), pixels.reverts)
    }

    @Test
    fun `when migrated user turns the toggle off after the kill switch then the revert is still reported`() = runTest {
        coldStart()
        migrationFeature.migration().setRawStoredState(Toggle.State(enable = false))

        coldStart()
        userTurnsToggle(false)

        assertEquals(MigrationState.Reverted, store.state)
        assertEquals(1, pixels.reverts.size)
    }

    @Test
    fun `when app died after storing the migration but before writing the setting then the next start finishes the flip`() = runTest {
        store.state = MigrationState.Migrated(atMillis = FLIP_TIME)

        coldStart()

        assertEquals(listOf(true), setInputScreenCalls)
        assertEquals(true, pickerWasShown)
        assertEquals(1, pixels.flips)
        assertEquals(MigrationState.Migrated(atMillis = FLIP_TIME), store.state)
    }

    @Test
    fun `when app died after storing the migration and the picker was used since then skips without flipping`() = runTest {
        store.state = MigrationState.Migrated(atMillis = FLIP_TIME)
        givenPicker(wasShown = true, displayCount = 1)

        coldStart()

        assertNotFlipped()
        assertGateOpen()
    }

    @Test
    fun `when app died after storing the migration and the user turned Duck AI off since then skips without flipping`() = runTest {
        store.state = MigrationState.Migrated(atMillis = FLIP_TIME)
        duckAiUserSetting = false

        coldStart()

        assertNotFlipped()
    }

    @Test
    fun `when writing the setting fails then nothing crashes, the gate opens, and the next start finishes the flip`() = runTest {
        settingWritesFail = true

        coldStart()

        assertEquals(MigrationState.Migrated(atMillis = FLIP_TIME), store.state)
        assertEquals(0, pixels.flips)
        assertEquals(false, pickerWasShown)
        assertGateOpen()

        settingWritesFail = false
        coldStart()

        assertEquals(listOf(true), setInputScreenCalls)
        assertEquals(true, pickerWasShown)
        assertEquals(1, pixels.flips)
    }

    @Test
    fun `when a migrated user's setting is unwritten and the flag is off then a picker Search only answer is not a revert`() = runTest {
        store.state = MigrationState.Migrated(atMillis = FLIP_TIME)
        migrationFeature.migration().setRawStoredState(Toggle.State(enable = false))
        coldStart()

        userAnswersPickerSearchOnly()
        coldStart()

        assertEquals(emptyList<MigrationRevert>(), pixels.revertKinds)
        assertEquals(MigrationState.Migrated(atMillis = FLIP_TIME), store.state)
    }

    @Test
    fun `when writing the setting fails then a picker answer in the same process is not a revert`() = runTest {
        settingWritesFail = true
        coldStart()
        settingWritesFail = false

        userAnswersPickerSearchOnly()
        coldStart()

        assertEquals(emptyList<MigrationRevert>(), pixels.revertKinds)
        assertEquals(MigrationState.Migrated(atMillis = FLIP_TIME), store.state)
        assertEquals(0, pixels.flips)
    }

    private fun coldStart() {
        startProcess(DuckAiToggleMigrationGate().also { gate = it })
    }

    private fun startProcess(processGate: DuckAiToggleMigrationGate) {
        // A new process: a new observer, an empty DuckChat cache that fills a moment later, and a settings flow that starts
        // at false until the stored value loads.
        inputScreenFlow = MutableStateFlow(false)
        capabilityFlow = MutableStateFlow(InputMode.SEARCH_ONLY)
        cacheFilled = false
        coroutineRule.testScope.launch {
            delay(cacheFillDelay)
            cacheFilled = true
            recache()
        }
        newObserver(processGate).onCreate(mock())
        coroutineRule.testScope.advanceUntilIdle()
        inputScreenFlow.value = inputScreenSetting == true
        coroutineRule.testScope.advanceUntilIdle()
    }

    private fun recache() {
        capabilityFlow.value = if (duckChat.isEnabled() && inputScreenSetting == true) InputMode.SEARCH_AND_DUCK_AI else InputMode.SEARCH_ONLY
    }

    private fun userTurnsDuckAi(enabled: Boolean) {
        duckAiUserSetting = enabled
        recache()
        coroutineRule.testScope.advanceUntilIdle()
    }

    private fun remoteTurnsDuckAi(enabled: Boolean) {
        remoteDuckAiEnabled = enabled
        recache()
        coroutineRule.testScope.advanceUntilIdle()
    }

    private suspend fun userTurnsToggle(enabled: Boolean) {
        duckChat.setInputScreenUserSetting(enabled)
        coroutineRule.testScope.advanceUntilIdle()
    }

    private suspend fun userAnswersPickerSearchOnly() {
        pickerWasShown = true
        whenever(pickerDataStore.displayCount()).thenReturn(1)
        duckChat.setInputScreenUserSetting(false)
        coroutineRule.testScope.advanceUntilIdle()
    }

    private suspend fun assertGateOpen() {
        assertTrue(gate.awaitOpen(1.milliseconds))
    }

    private fun newObserver(processGate: DuckAiToggleMigrationGate) = DuckAiToggleMigrationObserver(
        feature = migrationFeature,
        store = store,
        duckChat = duckChat,
        inputModeState = inputModeState,
        userStageStore = userStageStore,
        statisticsDataStore = statisticsDataStore,
        pickerDataStore = pickerDataStore,
        legacyPickerStore = legacyPickerStore,
        onboardingStore = onboardingStore,
        pixels = pixels,
        gate = processGate,
        currentTimeProvider = timeProvider,
        appCoroutineScope = coroutineRule.testScope,
        dispatchers = coroutineRule.testDispatcherProvider,
    )

    private suspend fun givenPicker(wasShown: Boolean, displayCount: Int) {
        pickerWasShown = wasShown
        whenever(pickerDataStore.displayCount()).thenReturn(displayCount)
    }

    private fun assertNotFlipped(expectedState: MigrationState = MigrationState.Skipped) {
        assertEquals(emptyList<Boolean>(), setInputScreenCalls.filter { it })
        assertEquals(0, pixels.flips)
        assertEquals(expectedState, store.state)
    }

    private class InMemoryMigrationStore : DuckAiToggleMigrationStore {
        var state: MigrationState = MigrationState.NotEvaluated
        override suspend fun state(): MigrationState = state
        override suspend fun setState(state: MigrationState) {
            this.state = state
        }
    }

    private class RecordingPixels : DuckAiToggleMigrationPixels {
        var flips = 0
        val reverts = mutableListOf<Long>()
        val revertKinds = mutableListOf<MigrationRevert>()
        override fun reportFlipped() {
            flips++
        }
        override fun reportReverted(millisSinceFlip: Long, how: MigrationRevert) {
            reverts += millisSinceFlip
            revertKinds += how
        }
    }

    private companion object {
        const val FLIP_TIME = 1_700_000_000_000L
        val CACHE_FILL_DELAY = 200.milliseconds
    }
}
