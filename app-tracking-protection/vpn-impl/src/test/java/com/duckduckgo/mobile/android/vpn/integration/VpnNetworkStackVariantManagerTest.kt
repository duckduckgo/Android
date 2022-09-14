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

package com.duckduckgo.mobile.android.vpn.integration

import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.app.statistics.IndexRandomizer
import com.duckduckgo.app.statistics.Probabilistic
import com.duckduckgo.mobile.android.vpn.network.VpnNetworkStack
import junit.framework.TestCase
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class VpnNetworkStackVariantManagerTest {

    private val ngNetworkStack: VpnNetworkStack = mock()
    private val legacyNetworkStack: VpnNetworkStack = mock()
    private val vpnNetworkStackVariantStore: VpnNetworkStackVariantStore = mock()
    private val weightedRandomizer: IndexRandomizer = mock()

    private val vpnNetworkStackPluginPoint: PluginPoint<VpnNetworkStack> = VpnNetworkStackPluginPoint(listOf(ngNetworkStack, legacyNetworkStack))
    private val variants = listOf(
        TestVpnIntegrationVariant(NG, 1.0),
        TestVpnIntegrationVariant(LEGACY, 1.0),
    )
    private lateinit var vpnNetworkStackVariantManager: VpnNetworkStackVariantManager

    @Before
    fun setup() {
        whenever(ngNetworkStack.name).thenReturn(NG)
        whenever(legacyNetworkStack.name).thenReturn(LEGACY)

        vpnNetworkStackVariantManager = VpnNetworkStackVariantManagerImpl(
            { vpnNetworkStackPluginPoint }, vpnNetworkStackVariantStore, weightedRandomizer
        )
    }

    @Test
    fun whenNoVariantStoredThenVariantIsRandomlySelected() {
        whenever(vpnNetworkStackVariantStore.variant).thenReturn(null)
        whenever(weightedRandomizer.random(variants)).thenReturn(0)

        TestCase.assertEquals(NG, vpnNetworkStackVariantManager.getVariant())
    }

    @Test
    fun whenVariantStoredThenVariantIsRetrieved() {
        whenever(vpnNetworkStackVariantStore.variant).thenReturn(LEGACY)

        TestCase.assertEquals(LEGACY, vpnNetworkStackVariantManager.getVariant())
    }

    @Test
    fun whenVariantStoredThenVariantIsRetrievedEvenIfRandomlySelected() {
        whenever(vpnNetworkStackVariantStore.variant).thenReturn(LEGACY)
        whenever(weightedRandomizer.random(variants)).thenReturn(0)

        TestCase.assertEquals(LEGACY, vpnNetworkStackVariantManager.getVariant())
    }
}

private const val LEGACY = "legacy"
private const val NG = "ng"

private class VpnNetworkStackPluginPoint(private val plugins: List<VpnNetworkStack>) : PluginPoint<VpnNetworkStack> {
    override fun getPlugins(): List<VpnNetworkStack> {
        return plugins
    }
}

private data class TestVpnIntegrationVariant(val name: String, override val weight: Double) : Probabilistic
