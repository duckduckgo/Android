/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.feature

import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor
import com.duckduckgo.mobile.android.vpn.store.VpnFeatureToggleStore
import com.duckduckgo.mobile.android.vpn.store.VpnFeatureToggles
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealAppTpFeatureToggleRepositoryTest {

    private val appBuildConfig: AppBuildConfig = mock()
    private val vpnFeatureToggleStore: VpnFeatureToggleStore = mock()

    private lateinit var repository: RealAppTpFeatureToggleRepository

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)

        repository = RealAppTpFeatureToggleRepository(vpnFeatureToggleStore, appBuildConfig)
    }

    @Test
    fun whenDeleteAllThenDelegate() {
        repository.deleteAll()

        verify(vpnFeatureToggleStore).deleteAll()
    }

    @Test
    fun whenInsertThenDelegate() {
        repository.insert(VpnFeatureToggles("feature", false))

        verify(vpnFeatureToggleStore).insert(VpnFeatureToggles("feature", false))
    }

    @Test
    fun whenGetFeatureAndPlayBuildThenReturnFalseOrNull() {
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.PLAY)

        whenever(vpnFeatureToggleStore.get("feature", false)).thenReturn(null)
        assertNull(repository.get("feature", false))

        whenever(vpnFeatureToggleStore.get("feature", false)).thenReturn(true)
        assertEquals(false, repository.get("feature", false))

        whenever(vpnFeatureToggleStore.get("feature", false)).thenReturn(false)
        assertEquals(false, repository.get("feature", false))
    }

    @Test
    fun whenGetFeatureAndFdroidBuildThenReturnFalseOrNull() {
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.FDROID)

        whenever(vpnFeatureToggleStore.get("feature", false)).thenReturn(null)
        assertNull(repository.get("feature", false))

        whenever(vpnFeatureToggleStore.get("feature", false)).thenReturn(true)
        assertEquals(false, repository.get("feature", false))

        whenever(vpnFeatureToggleStore.get("feature", false)).thenReturn(false)
        assertEquals(false, repository.get("feature", false))
    }

    @Test
    fun whenGetFeatureAndInternalBuildThenReturnFalseOrValue() {
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.INTERNAL)

        whenever(vpnFeatureToggleStore.get("feature", false)).thenReturn(null)
        assertNull(repository.get("feature", false))

        whenever(vpnFeatureToggleStore.get("feature", false)).thenReturn(true)
        assertEquals(true, repository.get("feature", false))

        whenever(vpnFeatureToggleStore.get("feature", false)).thenReturn(false)
        assertEquals(false, repository.get("feature", false))
    }
}
