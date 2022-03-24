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

package com.duckduckgo.mobile.android.vpn.store

import com.duckduckgo.mobile.android.vpn.feature.AppTpFeatureName
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealAppTpFeatureToggleRepositoryTest {

    private val vpnFeatureToggleStore: VpnFeatureToggleStore = mock()

    private lateinit var repository: RealAppTpFeatureToggleRepository

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)

        repository = RealAppTpFeatureToggleRepository(vpnFeatureToggleStore)
    }

    @Test
    fun whenDeleteAllThenDelegate() {
        repository.deleteAll()

        verify(vpnFeatureToggleStore).deleteAll()
    }

    @Test
    fun whenInsertThenDelegate() {
        repository.insert(VpnFeatureToggles(FEATURE, false))

        verify(vpnFeatureToggleStore).insert(VpnFeatureToggles(FEATURE, false))
    }

    @Test
    fun whenGetFeatureThenReturnStoredValue() {
        whenever(vpnFeatureToggleStore.get(FEATURE, false)).thenReturn(true)
        assertEquals(true, repository.get(FEATURE, false))

        whenever(vpnFeatureToggleStore.get(FEATURE, false)).thenReturn(false)
        assertEquals(false, repository.get(FEATURE, false))

        whenever(vpnFeatureToggleStore.get(AppTpFeatureName.PrivateDnsSupport, false)).thenReturn(true)
        assertEquals(false, repository.get(FEATURE, false))
    }

    companion object {
        private val FEATURE = AppTpFeatureName.Ipv6Support
    }
}
