/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.sync.impl.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.sync.api.SyncState.OFF
import com.duckduckgo.sync.api.SyncState.READY
import com.duckduckgo.sync.api.SyncStateMonitor
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.SyncFeatureToggle
import com.duckduckgo.sync.impl.ui.SyncDisabledViewModelTestStates.createAccountDisabledSignedOutUser
import com.duckduckgo.sync.impl.ui.SyncDisabledViewModelTestStates.setupFlowsDisabledSignedOutUser
import com.duckduckgo.sync.impl.ui.SyncDisabledViewModelTestStates.syncDataDisabledSignedInUser
import com.duckduckgo.sync.impl.ui.SyncDisabledViewModelTestStates.syncDataDisabledSignedOutUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class SyncDisabledViewModelTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val syncStateFlow = MutableStateFlow(READY)
    private val syncFeatureToggle: FakeSyncFeatureToggle = FakeSyncFeatureToggle()
    private val syncStateMonitor: SyncStateMonitor = mock<SyncStateMonitor>().apply {
        whenever(this.syncState()).thenReturn(syncStateFlow)
    }

    private val testee = SyncDisabledViewModel(
        syncFeatureToggle = syncFeatureToggle,
        syncStateMonitor = syncStateMonitor,
        dispatcherProvider = coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun whenUnauthenticatedUserDataSyncingDisabledThenWarningMessageIsDisplayed() = runTest {
        givenAllowDataSyncing(enabled = false)
        syncStateFlow.emit(OFF)
        testee.onResume(mock())

        testee.viewState().test {
            val warningMessage = awaitItem().warningMessage
            assertEquals(syncDataDisabledSignedOutUser.disabled, warningMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUnauthenticatedUserDataSyncingEnabledOnNewerVersionThenWarningMessageIsDisplayed() = runTest {
        givenAllowDataSyncing(enabledOnNewerVersion = true)
        syncStateFlow.emit(OFF)
        testee.onResume(mock())

        testee.viewState().test {
            val warningMessage = awaitItem().warningMessage
            assertEquals(syncDataDisabledSignedOutUser.enabledOnNewerVersion, warningMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenAuthenticatedUserDataSyncingDisabledThenWarningMessageIsDisplayed() = runTest {
        givenAllowDataSyncing(enabled = false)
        syncStateFlow.emit(READY)
        testee.onResume(mock())

        testee.viewState().test {
            val warningMessage = awaitItem().warningMessage
            assertEquals(syncDataDisabledSignedInUser.disabled, warningMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenAuthenticatedUserDataSyncingEnabledOnNewerVersionThenWarningMessageIsDisplayed() = runTest {
        givenAllowDataSyncing(enabledOnNewerVersion = true)
        syncStateFlow.emit(READY)
        testee.onResume(mock())

        testee.viewState().test {
            val warningMessage = awaitItem().warningMessage
            assertEquals(syncDataDisabledSignedInUser.enabledOnNewerVersion, warningMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUnauthenticatedSetupFlowsDisabledThenWarningMessageIsDisplayed() = runTest {
        givenAllowSetupFlows(enabled = false)
        syncStateFlow.emit(OFF)
        testee.onResume(mock())

        testee.viewState().test {
            val warningMessage = awaitItem().warningMessage
            assertEquals(setupFlowsDisabledSignedOutUser.disabled, warningMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUnauthenticatedSetupFlowsEnabledOnNewerVersionThenWarningMessageIsDisplayed() = runTest {
        givenAllowSetupFlows(enabledOnNewerVersion = true)
        syncStateFlow.emit(OFF)
        testee.onResume(mock())

        testee.viewState().test {
            val warningMessage = awaitItem().warningMessage
            assertEquals(setupFlowsDisabledSignedOutUser.enabledOnNewerVersion, warningMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenAuthenticatedSetupFlowsDisabledThenWarningMessageIsDisplayed() = runTest {
        givenAllowSetupFlows(enabled = false)
        syncStateFlow.emit(READY)
        testee.onResume(mock())

        testee.viewState().test {
            val warningMessage = awaitItem().warningMessage
            assertNull(warningMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenAuthenticatedSetupFlowsEnabledOnNewerVersionThenWarningMessageIsDisplayed() = runTest {
        givenAllowSetupFlows(enabledOnNewerVersion = true)
        syncStateFlow.emit(READY)
        testee.onResume(mock())

        testee.viewState().test {
            val warningMessage = awaitItem().warningMessage
            assertNull(warningMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUnauthenticatedCreateAccountDisabledThenWarningMessageIsDisplayed() = runTest {
        givenCreateAccountFlows(enabled = false)
        syncStateFlow.emit(OFF)
        testee.onResume(mock())

        testee.viewState().test {
            val warningMessage = awaitItem().warningMessage
            assertEquals(createAccountDisabledSignedOutUser.disabled, warningMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUnauthenticatedCreateAccountEnabledOnNewerVersionThenWarningMessageIsDisplayed() = runTest {
        givenCreateAccountFlows(enabledOnNewerVersion = true)
        syncStateFlow.emit(OFF)
        testee.onResume(mock())

        testee.viewState().test {
            val warningMessage = awaitItem().warningMessage
            assertEquals(createAccountDisabledSignedOutUser.enabledOnNewerVersion, warningMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenAuthenticatedCreateAccountDisabledThenWarningMessageIsDisplayed() = runTest {
        givenCreateAccountFlows(enabled = false)
        syncStateFlow.emit(READY)
        testee.onResume(mock())

        testee.viewState().test {
            val warningMessage = awaitItem().warningMessage
            assertNull(warningMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenAuthenticatedCreateAccountEnabledOnNewerVersionThenWarningMessageIsDisplayed() = runTest {
        givenCreateAccountFlows(enabledOnNewerVersion = true)
        syncStateFlow.emit(READY)
        testee.onResume(mock())

        testee.viewState().test {
            val warningMessage = awaitItem().warningMessage
            assertNull(warningMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun givenAllowDataSyncing(enabled: Boolean = false, enabledOnNewerVersion: Boolean = false) {
        syncFeatureToggle.allowDataSyncing = enabled
        syncFeatureToggle.allowDataSyncingOnNewerVersion = enabledOnNewerVersion
    }

    private fun givenAllowSetupFlows(enabled: Boolean = false, enabledOnNewerVersion: Boolean = false) {
        syncFeatureToggle.allowSetupFlows = enabled
        syncFeatureToggle.allowSetupFlowsOnNewerVersion = enabledOnNewerVersion
    }

    private fun givenCreateAccountFlows(enabled: Boolean = false, enabledOnNewerVersion: Boolean = false) {
        syncFeatureToggle.allowCreateAccount = enabled
        syncFeatureToggle.allowCreateAccountOnNewerVersion = enabledOnNewerVersion
    }
}

object SyncDisabledViewModelTestStates {
    val syncDataDisabledSignedInUser = Variants(
        disabled = R.string.sync_disabled_authenticated_user,
        enabledOnNewerVersion = R.string.sync_disabled_authenticated_user_new_version,
    )

    val syncDataDisabledSignedOutUser = Variants(
        disabled = R.string.sync_flows_disabled,
        enabledOnNewerVersion = R.string.sync_flows_disabled_new_version,
    )

    val setupFlowsDisabledSignedOutUser = Variants(
        disabled = R.string.sync_flows_disabled,
        enabledOnNewerVersion = R.string.sync_flows_disabled_new_version,
    )

    val createAccountDisabledSignedOutUser = Variants(
        disabled = R.string.sync_create_account_disabled,
        enabledOnNewerVersion = R.string.sync_create_account_disabled_new_version,
    )

    class Variants(
        val disabled: Int = 0,
        val enabledOnNewerVersion: Int = 0,
    )
}

class FakeSyncFeatureToggle : SyncFeatureToggle {
    var showSync: Boolean = true
    var allowDataSyncing: Boolean = true
    var allowDataSyncingOnNewerVersion: Boolean = true
    var allowSetupFlows: Boolean = true
    var allowSetupFlowsOnNewerVersion: Boolean = true
    var allowCreateAccount: Boolean = true
    var allowCreateAccountOnNewerVersion: Boolean = true
    override fun showSync() = showSync
    override fun allowDataSyncing() = allowDataSyncing
    override fun allowDataSyncingOnNewerVersion() = allowDataSyncingOnNewerVersion
    override fun allowSetupFlows() = allowSetupFlows
    override fun allowSetupFlowsOnNewerVersion() = allowSetupFlowsOnNewerVersion
    override fun allowCreateAccount() = allowCreateAccount
    override fun allowCreateAccountOnNewerVersion() = allowCreateAccountOnNewerVersion
}
