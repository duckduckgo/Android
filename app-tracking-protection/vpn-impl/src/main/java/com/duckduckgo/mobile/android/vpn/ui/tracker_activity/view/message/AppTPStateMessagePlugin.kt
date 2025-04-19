/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.ui.tracker_activity.view.message

import android.content.Context
import android.view.View
import com.duckduckgo.anvil.annotations.ContributesActivePluginPoint
import com.duckduckgo.common.utils.plugins.ActivePlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnState

interface AppTPStateMessagePlugin : ActivePlugin {
    fun getView(
        context: Context,
        vpnState: VpnState,
        clickListener: (DefaultAppTPMessageAction) -> Unit,
    ): View?

    sealed class DefaultAppTPMessageAction {
        data object HandleAlwaysOnActionRequired : DefaultAppTPMessageAction()
        data object ReenableAppTP : DefaultAppTPMessageAction()
        data object LaunchFeedback : DefaultAppTPMessageAction()
    }

    companion object {
        internal const val PRIORITY_ACTION_REQUIRED = 100
        internal const val PRIORITY_ONBOARDING = 110
        internal const val PRIORITY_NEXT_SESSION = 120
        internal const val PRIORITY_REVOKED = 200
        internal const val PRIORITY_DISABLED = 210
        internal const val PRIORITY_DISABLED_BY_SYSTEM = 220
    }
}

@ContributesActivePluginPoint(
    scope = AppScope::class,
    boundType = AppTPStateMessagePlugin::class,
)
private interface AppTPStateMessagePluginPoint
