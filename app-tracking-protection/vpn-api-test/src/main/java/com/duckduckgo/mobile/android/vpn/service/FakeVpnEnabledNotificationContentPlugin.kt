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

import com.duckduckgo.mobile.android.vpn.service.VpnEnabledNotificationContentPlugin.VpnEnabledNotificationPriority
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeVpnEnabledNotificationContentPlugin constructor(
    private val isActive: Boolean,
    private val priority: VpnEnabledNotificationPriority = VpnEnabledNotificationPriority.NORMAL,
) : VpnEnabledNotificationContentPlugin {

    override val uuid: String = "1234"

    override fun getInitialContent(): VpnEnabledNotificationContentPlugin.VpnEnabledNotificationContent? {
        return null
    }

    override fun getUpdatedContent(): Flow<VpnEnabledNotificationContentPlugin.VpnEnabledNotificationContent?> {
        return flowOf(null)
    }

    override fun getPriority(): VpnEnabledNotificationPriority {
        return this.priority
    }

    override fun isActive(): Boolean = this.isActive
}
