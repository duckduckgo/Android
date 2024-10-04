/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.pixels.campaign.params

import com.duckduckgo.sync.api.DeviceSyncState
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SyncAdditionalPixelParamPluginTest {
    @Test
    fun whenUserNotSignedInForSyncThenPluginShouldReturnParamFalse() = runTest {
        val deviceSyncState: DeviceSyncState = mock()
        whenever(deviceSyncState.isUserSignedInOnDevice()).thenReturn(false)
        val plugin = SyncedUsedAdditionalPixelParamPlugin(deviceSyncState)

        Assert.assertEquals("syncUsed" to "false", plugin.params())
    }

    @Test
    fun whenUserIsSignedInForSyncThenPluginShouldReturnParamFalse() = runTest {
        val deviceSyncState: DeviceSyncState = mock()
        whenever(deviceSyncState.isUserSignedInOnDevice()).thenReturn(true)
        val plugin = SyncedUsedAdditionalPixelParamPlugin(deviceSyncState)

        Assert.assertEquals("syncUsed" to "true", plugin.params())
    }
}
