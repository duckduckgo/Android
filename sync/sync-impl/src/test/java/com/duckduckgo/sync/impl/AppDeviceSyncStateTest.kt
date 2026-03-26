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

package com.duckduckgo.sync.impl

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class AppDeviceSyncStateTest {

    private val syncFeatureToggle: SyncFeatureToggle = mock()
    private val syncAccountRepository: SyncAccountRepository = mock()
    private val appDeviceSyncState = AppDeviceSyncState(syncFeatureToggle, syncAccountRepository)

    @Test
    fun whenUserSignedInThenDeviceSyncEnabled() {
        whenever(syncAccountRepository.isSignedIn()).thenReturn(true)

        assertTrue(appDeviceSyncState.isUserSignedInOnDevice())
    }

    @Test
    fun whenShowSyncDisabledThenFeatureDisabled() {
        givenFeatureFlag(enabled = false)

        assertFalse(appDeviceSyncState.isFeatureEnabled())
    }

    @Test
    fun whenShowSyncEnabledThenFeatureEnabled() {
        givenFeatureFlag(enabled = true)

        assertTrue(appDeviceSyncState.isFeatureEnabled())
    }

    @Test
    fun whenAllowAiChatSyncEnabledThenIsDuckChatSyncFeatureEnabledReturnsTrue() {
        whenever(syncFeatureToggle.allowAiChatSync()).thenReturn(true)

        assertTrue(appDeviceSyncState.isDuckChatSyncFeatureEnabled())
    }

    @Test
    fun whenAllowAiChatSyncDisabledThenIsDuckChatSyncFeatureEnabledReturnsFalse() {
        whenever(syncFeatureToggle.allowAiChatSync()).thenReturn(false)

        assertFalse(appDeviceSyncState.isDuckChatSyncFeatureEnabled())
    }

    private fun givenFeatureFlag(enabled: Boolean) {
        whenever(syncFeatureToggle.showSync()).thenReturn(enabled)
    }
}
