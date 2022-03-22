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

import com.duckduckgo.feature.toggles.api.FeatureName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AppTpFeatureTogglesPluginTest {

    private val appTpFeatureToggleRepository: AppTpFeatureToggleRepository = mock()
    private lateinit var plugin: AppTpFeatureTogglesPlugin

    @Before
    fun setup() {
        plugin = AppTpFeatureTogglesPlugin(appTpFeatureToggleRepository)
    }

    @Test
    fun whenIsEnabledCalledOnAppTpFeatureNameThenReturnRepositoryValue() {
        whenever(appTpFeatureToggleRepository.get(AppTpFeatureName.Ipv6Support().value, false)).thenReturn(null)
        assertNull(plugin.isEnabled(AppTpFeatureName.Ipv6Support(), false))

        whenever(appTpFeatureToggleRepository.get(AppTpFeatureName.Ipv6Support().value, false)).thenReturn(true)
        assertEquals(true, plugin.isEnabled(AppTpFeatureName.Ipv6Support(), false))

        whenever(appTpFeatureToggleRepository.get(AppTpFeatureName.Ipv6Support().value, false)).thenReturn(false)
        assertEquals(false, plugin.isEnabled(AppTpFeatureName.Ipv6Support(), false))
    }

    @Test
    fun whenIsEnabledCalledOnOtherFeatureNameThenReturnRepositoryNull() {
        assertNull(plugin.isEnabled(TestFeatureName(), false))
    }
}

class TestFeatureName(override val value: String = "test") : FeatureName
