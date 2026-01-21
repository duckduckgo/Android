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

package com.duckduckgo.sync.impl.ui.setup

import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.SyncFeatureToggle
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.ui.setup.SyncCreateAccountViewModel.Command.FinishSetupFlow
import com.duckduckgo.sync.impl.ui.setup.SyncCreateAccountViewModel.Command.ShowError
import com.duckduckgo.sync.impl.ui.setup.SyncCreateAccountViewModel.ViewMode.CreatingAccount
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class SyncCreateAccountViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val syncRepostitory: SyncAccountRepository = mock()
    private val syncPixels: SyncPixels = mock()
    private val syncFeatureToggle: SyncFeatureToggle = mock()

    private val testee = SyncCreateAccountViewModel(
        syncRepostitory,
        syncPixels,
        coroutineTestRule.testDispatcherProvider,
        syncFeatureToggle,
    )

    @Test
    fun whenUserIsNotSignedInThenAccountCreatedAndViewStateUpdated() = runTest {
        whenever(syncRepostitory.createAccount()).thenReturn(Result.Success(true))

        testee.viewState(source = null).test {
            val viewState = awaitItem()
            Assert.assertTrue(viewState.viewMode is CreatingAccount)
            cancelAndIgnoreRemainingEvents()
        }

        testee.commands().test {
            val command = awaitItem()
            Assert.assertTrue(command is FinishSetupFlow)
            verify(syncPixels).fireSignupDirectPixel(source = null)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserIsNotSignedInWithSourceThenAccountCreatedAndViewStateUpdated() = runTest {
        whenever(syncRepostitory.createAccount()).thenReturn(Result.Success(true))

        testee.viewState(source = "foo").test {
            val viewState = awaitItem()
            Assert.assertTrue(viewState.viewMode is CreatingAccount)
            cancelAndIgnoreRemainingEvents()
        }

        testee.commands().test {
            awaitItem()
            verify(syncPixels).fireSignupDirectPixel(source = "foo")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenCreateAccountFailsThenEmitError() = runTest {
        whenever(syncRepostitory.createAccount()).thenReturn(Result.Error(1, ""))

        testee.viewState(source = null).test {
            val viewState = awaitItem()
            Assert.assertTrue(viewState.viewMode is CreatingAccount)
            cancelAndIgnoreRemainingEvents()
        }

        testee.commands().test {
            val command = awaitItem()
            Assert.assertTrue(command is ShowError)
            verifyNoInteractions(syncPixels)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
