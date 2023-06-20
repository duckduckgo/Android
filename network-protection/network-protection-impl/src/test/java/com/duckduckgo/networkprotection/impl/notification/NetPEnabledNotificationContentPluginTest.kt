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

package com.duckduckgo.networkprotection.impl.notification

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.mobile.android.vpn.FakeVpnFeaturesRegistry
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.mobile.android.vpn.service.VpnEnabledNotificationContentPlugin
import com.duckduckgo.networkprotection.impl.NetPVpnFeature
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class NetPEnabledNotificationContentPluginTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
    private lateinit var vpnFeaturesRegistry: VpnFeaturesRegistry
    private lateinit var plugin: NetPEnabledNotificationContentPlugin

    @Before
    fun setup() {
        vpnFeaturesRegistry = FakeVpnFeaturesRegistry().apply {
            runBlocking { registerFeature(NetPVpnFeature.NETP_VPN) }
        }

        plugin = NetPEnabledNotificationContentPlugin(resources, vpnFeaturesRegistry) { null }
    }

    @Test
    fun getInitialContentNetPDisabledReturnNull() = runTest {
        vpnFeaturesRegistry.unregisterFeature(NetPVpnFeature.NETP_VPN)
        val content = plugin.getInitialContent()

        assertNull(content)
    }

    @Test
    fun getInitialContentNetPEnabledReturnContent() {
        val content = plugin.getInitialContent()

        assertNotNull(content)
        content!!.assertTitleEquals("Network Protection is enabled and routing traffic through the VPN.")
        content.assertMessageEquals("")
    }

    @Test
    fun getUpdatedContentNetPDisabledReturnNull() = runTest {
        vpnFeaturesRegistry.unregisterFeature(NetPVpnFeature.NETP_VPN)
        plugin.getUpdatedContent().test {
            assertNull(awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun getUpdatedContentNetPEnabledReturnContent() = runTest {
        plugin.getUpdatedContent().test {
            val item = awaitItem()

            assertNotNull(item)
            item!!.assertTitleEquals("Network Protection is enabled and routing traffic through the VPN.")
            item.assertMessageEquals("")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun getPriorityReturnsHigh() {
        assertEquals(VpnEnabledNotificationContentPlugin.VpnEnabledNotificationPriority.HIGH, plugin.getPriority())
    }

    @Test
    fun isActiveNetPDisabledReturnFalse() = runTest {
        vpnFeaturesRegistry.unregisterFeature(NetPVpnFeature.NETP_VPN)
        assertFalse(plugin.isActive())
    }

    @Test
    fun isActiveNetPEnabledReturnTrue() {
        assertTrue(plugin.isActive())
    }
}

private fun VpnEnabledNotificationContentPlugin.VpnEnabledNotificationContent.assertTitleEquals(expected: String) {
    Assert.assertEquals(expected, this.title.toString())
}

private fun VpnEnabledNotificationContentPlugin.VpnEnabledNotificationContent.assertMessageEquals(expected: String) {
    Assert.assertEquals(expected, this.message.toString())
}
