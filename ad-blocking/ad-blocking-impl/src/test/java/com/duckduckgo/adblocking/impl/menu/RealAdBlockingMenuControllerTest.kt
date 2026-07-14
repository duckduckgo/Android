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

package com.duckduckgo.adblocking.impl.menu

import com.duckduckgo.adblocking.impl.AdBlockingSettingsRepository
import com.duckduckgo.adblocking.impl.domain.AdBlockingState
import com.duckduckgo.adblocking.impl.domain.AdBlockingStatusChecker
import com.duckduckgo.adblocking.impl.store.RealAdBlockingSessionStore
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class RealAdBlockingMenuControllerTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val userEnabledFlow = MutableStateFlow<Boolean?>(true)
    private val settingsRepository = object : AdBlockingSettingsRepository {
        override fun isEnabledFlow(): Flow<Boolean?> = userEnabledFlow
        override suspend fun setEnabled(enabled: Boolean) {
            userEnabledFlow.value = enabled
        }
    }
    private val sessionStore = RealAdBlockingSessionStore()
    private val statusChecker: AdBlockingStatusChecker = mock {
        on { currentState() } doReturn AdBlockingState.Enabled.UserEnabled
    }

    private val controller = RealAdBlockingMenuController(
        settingsRepository,
        sessionStore,
        statusChecker,
        coroutineRule.testScope,
        coroutineRule.testDispatcherProvider,
    )

    @Test
    fun whenStateIsUntilRelaunchThenChoiceIsDisableUntilRelaunch() {
        whenever(statusChecker.currentState()).thenReturn(AdBlockingState.Disabled.UntilRelaunch)

        assertEquals(AdBlockingChoice.DISABLE_UNTIL_RELAUNCH, controller.currentChoice())
    }

    @Test
    fun whenStateIsUserEnabledThenChoiceIsAlwaysOn() {
        whenever(statusChecker.currentState()).thenReturn(AdBlockingState.Enabled.UserEnabled)

        assertEquals(AdBlockingChoice.ALWAYS_ON, controller.currentChoice())
    }

    @Test
    fun whenStateIsEnabledByDefaultThenChoiceIsAlwaysOn() {
        whenever(statusChecker.currentState()).thenReturn(AdBlockingState.Enabled.Default)

        assertEquals(AdBlockingChoice.ALWAYS_ON, controller.currentChoice())
    }

    @Test
    fun whenStateIsPermanentlyDisabledThenChoiceIsAlwaysOff() {
        whenever(statusChecker.currentState()).thenReturn(AdBlockingState.Disabled.Permanent)

        assertEquals(AdBlockingChoice.ALWAYS_OFF, controller.currentChoice())
    }

    @Test
    fun whenAlwaysOnSelectedThenPersistsEnabledAndClearsSession() = runTest {
        sessionStore.setDisabledUntilRelaunch()

        controller.onChoiceSelected(AdBlockingChoice.ALWAYS_ON)

        assertEquals(true, userEnabledFlow.value)
        assertFalse(sessionStore.isDisabledUntilRelaunch())
    }

    @Test
    fun whenDisableUntilRelaunchSelectedThenSetsSessionAndLeavesPersistedUntouched() = runTest {
        userEnabledFlow.value = true

        controller.onChoiceSelected(AdBlockingChoice.DISABLE_UNTIL_RELAUNCH)

        assertTrue(sessionStore.isDisabledUntilRelaunch())
        assertEquals(true, userEnabledFlow.value)
    }

    @Test
    fun whenAlwaysOffSelectedThenPersistsDisabledAndClearsSession() = runTest {
        sessionStore.setDisabledUntilRelaunch()

        controller.onChoiceSelected(AdBlockingChoice.ALWAYS_OFF)

        assertEquals(false, userEnabledFlow.value)
        assertFalse(sessionStore.isDisabledUntilRelaunch())
    }

    @Test
    fun whenEnableThenPersistsEnabledAndClearsSession() = runTest {
        sessionStore.setDisabledUntilRelaunch()

        controller.enable()

        assertEquals(true, userEnabledFlow.value)
        assertFalse(sessionStore.isDisabledUntilRelaunch())
    }
}
