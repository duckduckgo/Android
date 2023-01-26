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

import com.duckduckgo.anvil.annotations.ContributesPluginPoint
import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.di.scopes.VpnScope

@ContributesPluginPoint(
    scope = VpnScope::class,
    boundType = VpnEnabledNotificationContentPlugin::class,
)
@Suppress("unused")
interface VpnEnabledNotificationContentPluginPoint

fun PluginPoint<VpnEnabledNotificationContentPlugin>.getHighestPriorityPlugin(): VpnEnabledNotificationContentPlugin {
    return getPlugins().maxBy { it.getPriority().ordinal }
}
