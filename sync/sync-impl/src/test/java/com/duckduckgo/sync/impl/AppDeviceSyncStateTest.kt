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

import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor
import com.duckduckgo.feature.toggles.api.Toggle
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class AppDeviceSyncStateTest {

    private val appBuildConfig: AppBuildConfig = mock()
    private val syncFeature: SyncFeature = mock()
    private val syncAccountRepository: SyncAccountRepository = mock()
    private val appDeviceSyncState = AppDeviceSyncState(appBuildConfig, syncFeature, syncAccountRepository)

    @Test
    fun whenUserSignedInThenDeviceSyncEnabled() {
        whenever(syncAccountRepository.isSignedIn()).thenReturn(true)

        assertTrue(appDeviceSyncState.isUserSignedInOnDevice())
    }

    @Test
    fun whenInternalBuildThenFeatureEnabled() {
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.INTERNAL)
        givenFeatureFlag(enabled = false)

        assertTrue(appDeviceSyncState.isFeatureEnabled())
    }

    @Test
    fun whenFeatureFlagEnabledThenFeatureEnabled() {
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.PLAY)
        givenFeatureFlag(enabled = true)

        assertTrue(appDeviceSyncState.isFeatureEnabled())
    }

    private fun givenFeatureFlag(enabled: Boolean) {
        val toggle: Toggle = mock()
        whenever(toggle.isEnabled()).thenReturn(enabled)
        whenever(syncFeature.self()).thenReturn(toggle)
    }
}
