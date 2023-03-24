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
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.sync.TestSyncFixtures.jsonRecoveryKeyEncoded
import com.duckduckgo.sync.impl.SyncRepository
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.LaunchDeviceSetupFlow
import java.lang.String.format
import kotlin.reflect.KClass
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class SyncActivityViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val syncRepository: SyncRepository = mock()

    private val testee = SyncActivityViewModel(
        syncRepository = syncRepository,
        dispatchers = coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun whenUserSignedInThenDeviceSyncViewStateIsEnabled() = runTest {
        whenever(syncRepository.isSignedIn()).thenReturn(true)

        testee.viewState().test {
            val viewState = awaitItem()
            assertTrue(viewState.isDeviceSyncEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserSignedInThenShowAccount() = runTest {
        whenever(syncRepository.isSignedIn()).thenReturn(true)

        testee.viewState().test {
            val viewState = awaitItem()
            assertTrue(viewState.showAccount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserSignedInThenLoginQRCodeIsNotNull() = runTest {
        whenever(syncRepository.isSignedIn()).thenReturn(true)
        whenever(syncRepository.getRecoveryCode()).thenReturn(jsonRecoveryKeyEncoded)

        testee.viewState().test {
            val viewState = awaitItem()
            assertTrue(viewState.loginQRCode != null)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenToggleDisabledThenLaunchSetupFlow() = runTest {
        testee.onToggleClicked(false)

        testee.viewState().test {
            val viewState = awaitItem()
            assertFalse(viewState.isDeviceSyncEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenRefreshAndUserSignedInThenDeviceSyncViewStateIsEnabled() = runTest {
        whenever(syncRepository.isSignedIn()).thenReturn(true)

        testee.refreshData()

        testee.viewState().test {
            val viewState = awaitItem()
            assertTrue(viewState.isDeviceSyncEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenToggleEnabledThenLaunchSetupFlow() = runTest {
        testee.onToggleClicked(true)

        testee.commands().test {
            awaitItem().assertCommandType(LaunchDeviceSetupFlow::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun Command.assertCommandType(expectedType: KClass<out Command>) {
        assertTrue(format("Unexpected command type: %s", this::class.simpleName), this::class == expectedType)
    }
}
