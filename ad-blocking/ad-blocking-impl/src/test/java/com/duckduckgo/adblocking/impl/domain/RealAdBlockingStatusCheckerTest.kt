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

package com.duckduckgo.adblocking.impl.domain

import com.duckduckgo.adblocking.impl.AdBlockingSettingsRepository
import com.duckduckgo.adblocking.impl.remoteconfig.AdBlockingExtensionFeature
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

@OptIn(ExperimentalCoroutinesApi::class)
class RealAdBlockingStatusCheckerTest {

    private var killSwitchEnabled = true
    private var contingencyModeEnabled = false

    private val killSwitchEnabledFlow = MutableStateFlow(true)
    private val enabledByDefaultFlow = MutableStateFlow(false)

    private val selfToggle: Toggle = mock {
        on { isEnabled() } doAnswer { killSwitchEnabled }
        on { enabled() } doReturn killSwitchEnabledFlow
    }
    private val contingencyModeToggle: Toggle = mock {
        on { isEnabled() } doAnswer { contingencyModeEnabled }
    }
    private val enabledByDefaultToggle: Toggle = mock {
        on { isEnabled() } doAnswer { enabledByDefaultFlow.value }
        on { enabled() } doReturn enabledByDefaultFlow
    }
    private val feature: AdBlockingExtensionFeature = mock {
        on { self() } doReturn selfToggle
        on { enableContingencyMode() } doReturn contingencyModeToggle
        on { enabledByDefault() } doReturn enabledByDefaultToggle
    }

    private val userEnabledFlow = MutableStateFlow<Boolean?>(true)
    private val settingsRepository: AdBlockingSettingsRepository = object : AdBlockingSettingsRepository {
        override fun isEnabledFlow(): Flow<Boolean?> = userEnabledFlow
        override suspend fun setEnabled(enabled: Boolean) {
            userEnabledFlow.value = enabled
        }
    }
    private val testScope = CoroutineScope(UnconfinedTestDispatcher())

    private val checker by lazy {
        RealAdBlockingStatusChecker(feature, settingsRepository, testScope)
    }

    @After
    fun tearDown() {
        testScope.cancel()
    }

    @Test
    fun whenKillSwitchIsOnAndContingencyModeIsOffThenCanInject() {
        assertTrue(checker.canInject())
    }

    @Test
    fun whenKillSwitchIsOffThenCannotInject() {
        killSwitchEnabled = false

        assertFalse(checker.canInject())
    }

    @Test
    fun whenContingencyModeIsOnThenCannotInject() {
        contingencyModeEnabled = true

        assertFalse(checker.canInject())
    }

    @Test
    fun whenUserHasDisabledThenCannotInject() {
        userEnabledFlow.value = false

        assertFalse(checker.canInject())
    }

    @Test
    fun whenUserHasNoPreferenceAndEnabledByDefaultIsTrueThenCanInject() {
        userEnabledFlow.value = null
        enabledByDefaultFlow.value = true

        assertTrue(checker.canInject())
    }

    @Test
    fun whenUserHasNoPreferenceAndEnabledByDefaultIsFalseThenCannotInject() {
        userEnabledFlow.value = null
        enabledByDefaultFlow.value = false

        assertFalse(checker.canInject())
    }

    @Test
    fun whenUserHasNoPreferenceAndDefaultFlowChangesThenCanInjectReflectsIt() {
        userEnabledFlow.value = null
        enabledByDefaultFlow.value = false
        assertFalse(checker.canInject())

        enabledByDefaultFlow.value = true
        assertTrue(checker.canInject())
    }

    @Test
    fun whenUserHasSetTrueThenCurrentStateIsUserEnabled() {
        userEnabledFlow.value = true

        assertEquals(AdBlockingState.Enabled.UserEnabled, checker.currentState())
    }

    @Test
    fun whenUserHasSetFalseThenCurrentStateIsDisabledEvenIfDefaultIsTrue() {
        userEnabledFlow.value = false
        enabledByDefaultFlow.value = true

        assertEquals(AdBlockingState.Disabled, checker.currentState())
    }

    @Test
    fun whenUserHasNoPreferenceAndEnabledByDefaultThenCurrentStateIsEnabledDefault() {
        userEnabledFlow.value = null
        enabledByDefaultFlow.value = true

        assertEquals(AdBlockingState.Enabled.Default, checker.currentState())
    }

    @Test
    fun whenUserHasNoPreferenceAndDefaultChangesThenCurrentStateReflectsIt() {
        userEnabledFlow.value = null
        enabledByDefaultFlow.value = false
        assertEquals(AdBlockingState.Disabled, checker.currentState())

        enabledByDefaultFlow.value = true
        assertEquals(AdBlockingState.Enabled.Default, checker.currentState())
    }

    @Test
    fun whenKillSwitchIsOnThenIsShownInSettings() {
        assertTrue(checker.isShownInSettings())
    }

    @Test
    fun whenKillSwitchIsOffThenIsNotShownInSettings() {
        killSwitchEnabled = false

        assertFalse(checker.isShownInSettings())
    }

    @Test
    fun whenKillSwitchEnabledThenIsShownInSettingsFlowEmitsTrue() = runTest {
        killSwitchEnabledFlow.value = true

        assertTrue(checker.isShownInSettingsFlow().first())
    }

    @Test
    fun whenKillSwitchDisabledThenIsShownInSettingsFlowEmitsFalse() = runTest {
        killSwitchEnabledFlow.value = false

        assertFalse(checker.isShownInSettingsFlow().first())
    }

    @Test
    fun whenKillSwitchFlowChangesThenIsShownInSettingsFlowReflectsIt() = runTest {
        killSwitchEnabledFlow.value = true
        assertTrue(checker.isShownInSettingsFlow().first())

        killSwitchEnabledFlow.value = false
        assertFalse(checker.isShownInSettingsFlow().first())
    }

    @Test
    fun whenUserHasEnabledThenObserveStateEmitsUserEnabled() = runTest {
        userEnabledFlow.value = true

        assertEquals(AdBlockingState.Enabled.UserEnabled, checker.observeState().first())
    }

    @Test
    fun whenUserHasDisabledThenObserveStateEmitsDisabled() = runTest {
        userEnabledFlow.value = false

        assertEquals(AdBlockingState.Disabled, checker.observeState().first())
    }

    @Test
    fun whenUserHasDisabledThenObserveStateEmitsDisabledEvenIfDefaultIsTrue() = runTest {
        userEnabledFlow.value = false
        enabledByDefaultFlow.value = true

        assertEquals(AdBlockingState.Disabled, checker.observeState().first())
    }

    @Test
    fun whenNotSetAndEnabledByDefaultThenObserveStateEmitsEnabledDefault() = runTest {
        userEnabledFlow.value = null
        enabledByDefaultFlow.value = true

        assertEquals(AdBlockingState.Enabled.Default, checker.observeState().first())
    }

    @Test
    fun whenNotSetAndDisabledByDefaultThenObserveStateEmitsDisabled() = runTest {
        userEnabledFlow.value = null
        enabledByDefaultFlow.value = false

        assertEquals(AdBlockingState.Disabled, checker.observeState().first())
    }

    @Test
    fun whenNotSetAndDefaultFlowChangesThenObserveStateReflectsIt() = runTest {
        userEnabledFlow.value = null
        enabledByDefaultFlow.value = false
        assertEquals(AdBlockingState.Disabled, checker.observeState().first())

        enabledByDefaultFlow.value = true
        assertEquals(AdBlockingState.Enabled.Default, checker.observeState().first())
    }

    @Test
    fun whenUpstreamHasNotEmittedYetThenCurrentStateIsUninitialized() {
        val pendingRepository = object : AdBlockingSettingsRepository {
            override fun isEnabledFlow(): Flow<Boolean?> = emptyFlow()
            override suspend fun setEnabled(enabled: Boolean) = Unit
        }

        val checker = RealAdBlockingStatusChecker(feature, pendingRepository, testScope)

        assertEquals(AdBlockingState.Uninitialized, checker.currentState())
    }
}
