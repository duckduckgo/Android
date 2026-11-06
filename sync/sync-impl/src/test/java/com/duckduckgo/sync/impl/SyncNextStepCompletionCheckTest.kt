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

package com.duckduckgo.sync.impl

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.sync.api.DeviceSyncState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SyncNextStepCompletionCheckTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val deviceSyncState: DeviceSyncState = mock()
    private val testee = SyncNextStepCompletionCheck(deviceSyncState)

    @Test
    fun whenItemIdRequestedThenMatchesConfig() {
        assertEquals("setup_sync", testee.itemId)
    }

    @Test
    fun whenUserSignedInThenComplete() = runTest {
        whenever(deviceSyncState.isUserSignedInOnDevice()).thenReturn(true)

        assertTrue(testee.isComplete())
    }

    @Test
    fun whenUserSignedOutThenIncomplete() = runTest {
        whenever(deviceSyncState.isUserSignedInOnDevice()).thenReturn(false)

        assertFalse(testee.isComplete())
    }
}
