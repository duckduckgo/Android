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

package com.duckduckgo.mobile.android.vpn.service

import com.duckduckgo.common.utils.plugins.PluginPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VpnEnabledNotificationContentPluginPointKtTest {
    @Test
    fun whenEmptyPluginPointThenReturnNull() {
        assertNull(createPluginPoint(emptyList()).getHighestPriorityPlugin())
    }

    @Test
    fun whenInactivePluginsThenReturnNull() {
        assertNull(
            createPluginPoint(
                listOf(
                    FakeVpnEnabledNotificationContentPlugin(isActive = false),
                    FakeVpnEnabledNotificationContentPlugin(isActive = false),
                ),
            ).getHighestPriorityPlugin(),
        )
    }

    @Test
    fun whenActivePluginsThenReturnThemInPriorityOrder() {
        val plugin = createPluginPoint(
            listOf(
                FakeVpnEnabledNotificationContentPlugin(isActive = true, VpnEnabledNotificationContentPlugin.VpnEnabledNotificationPriority.NORMAL),
                FakeVpnEnabledNotificationContentPlugin(isActive = true, VpnEnabledNotificationContentPlugin.VpnEnabledNotificationPriority.LOW),
                FakeVpnEnabledNotificationContentPlugin(isActive = true, VpnEnabledNotificationContentPlugin.VpnEnabledNotificationPriority.HIGH),
            ),
        ).getHighestPriorityPlugin()

        assertEquals(VpnEnabledNotificationContentPlugin.VpnEnabledNotificationPriority.HIGH, plugin?.getPriority())
    }

    private fun createPluginPoint(plugins: List<VpnEnabledNotificationContentPlugin>): PluginPoint<VpnEnabledNotificationContentPlugin> {
        return object : PluginPoint<VpnEnabledNotificationContentPlugin> {
            override fun getPlugins(): Collection<VpnEnabledNotificationContentPlugin> {
                return plugins
            }
        }
    }
}
