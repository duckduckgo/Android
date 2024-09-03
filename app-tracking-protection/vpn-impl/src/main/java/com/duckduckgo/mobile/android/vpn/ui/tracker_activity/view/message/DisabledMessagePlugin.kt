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
import com.duckduckgo.anvil.annotations.ContributesActivePlugin
import com.duckduckgo.common.ui.view.InfoPanel.Companion.REPORT_ISSUES_ANNOTATION
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnRunningState.DISABLED
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnState
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason.SELF_STOP
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.view.message.AppTPStateMessagePlugin.Companion.PRIORITY_DISABLED
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.view.message.AppTPStateMessagePlugin.DefaultAppTPMessageAction
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.view.message.AppTPStateMessagePlugin.DefaultAppTPMessageAction.LaunchFeedback
import javax.inject.Inject

@ContributesActivePlugin(
    scope = AppScope::class,
    boundType = AppTPStateMessagePlugin::class,
    priority = PRIORITY_DISABLED,
)
class DisabledMessagePlugin @Inject constructor() : AppTPStateMessagePlugin {
    override fun getView(
        context: Context,
        vpnState: VpnState,
        clickListener: (DefaultAppTPMessageAction) -> Unit,
    ): View? {
        return if (vpnState.state == DISABLED && vpnState.stopReason is SELF_STOP) {
            AppTpDisabledInfoPanel(context).apply {
                setClickableLink(
                    REPORT_ISSUES_ANNOTATION,
                    context.getText(R.string.atp_ActivityDisabledLabel),
                ) { clickListener.invoke(LaunchFeedback) }
            }
        } else {
            null
        }
    }
}
