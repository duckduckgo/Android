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

package com.duckduckgo.sync.impl.promotion.devicemigration

import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.sync.impl.promotion.devicemigration.SyncAfterDeviceTransferViewModel.Command.Hide
import com.duckduckgo.sync.impl.promotion.devicemigration.SyncAfterDeviceTransferViewModel.Command.OpenSyncSetup
import com.duckduckgo.sync.impl.ui.dashboard.SyncActivityStartSyncFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SyncAfterDeviceTransferViewModelTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val prompt = FakePrompt()
    private val testee = SyncAfterDeviceTransferViewModel(prompt)

    @Test
    fun whenTurnOnSyncClickedThenOpensTheFirstSyncSetupScreen() = runTest {
        testee.commands().test {
            testee.onTurnOnSyncClicked()

            assertEquals(OpenSyncSetup(SyncActivityStartSyncFlow(source = null)), awaitItem())
        }
    }

    @Test
    fun whenTurnOnSyncClickedThenTheCardIsNotDismissedForGood() = runTest {
        testee.onTurnOnSyncClicked()

        assertFalse(prompt.dismissed)
    }

    @Test
    fun whenDismissClickedThenTheCardIsDismissedForGoodAndHidden() = runTest {
        testee.commands().test {
            testee.onDismissClicked()

            assertEquals(Hide, awaitItem())
            assertTrue(prompt.dismissed)
        }
    }

    private class FakePrompt : SyncAfterDeviceTransferPrompt {
        var dismissed = false
        override suspend fun shouldShow(): Boolean = !dismissed
        override suspend fun dismiss() {
            dismissed = true
        }
    }
}
