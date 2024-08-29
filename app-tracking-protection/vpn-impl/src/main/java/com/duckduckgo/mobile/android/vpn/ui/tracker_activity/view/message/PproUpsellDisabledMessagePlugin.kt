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
import android.view.LayoutInflater
import android.view.View
import com.duckduckgo.anvil.annotations.ContributesActivePlugin
import com.duckduckgo.app.tabs.BrowserNav
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.databinding.ViewMessageInfoDisabledBinding
import com.duckduckgo.mobile.android.vpn.network.ExternalVpnDetector
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnRunningState.DISABLED
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnState
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason.SELF_STOP
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.view.message.AppTPStateMessagePlugin.Companion.PRIORITY_DISABLED
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.view.message.AppTPStateMessagePlugin.DefaultAppTPMessageAction
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.view.message.PproUpsellDisabledMessagePlugin.Companion.PRIORITY_PPRO_DISABLED
import com.duckduckgo.subscriptions.api.Subscriptions
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

@ContributesActivePlugin(
    scope = ActivityScope::class,
    boundType = AppTPStateMessagePlugin::class,
    priority = PRIORITY_PPRO_DISABLED,
)
class PproUpsellDisabledMessagePlugin @Inject constructor(
    private val subscriptions: Subscriptions,
    private val vpnDetector: ExternalVpnDetector,
    private val browserNav: BrowserNav,
) : AppTPStateMessagePlugin {
    override fun getView(
        context: Context,
        vpnState: VpnState,
        clickListener: (DefaultAppTPMessageAction) -> Unit,
    ): View? {
        val isEligible = runBlocking { vpnDetector.isExternalVpnDetected() && subscriptions.isUpsellEligible() }
        return if (vpnState.state == DISABLED && vpnState.stopReason is SELF_STOP && isEligible) {
            ViewMessageInfoDisabledBinding.inflate(LayoutInflater.from(context))
                .apply {
                    this.root.setClickableLink(
                        PPRO_UPSELL_ANNOTATION,
                        context.getText(R.string.apptp_PproUpsellInfoDisabled),
                    ) { context.launchPPro() }
                }
                .root
        } else {
            null
        }
    }

    private fun Context.launchPPro() {
        startActivity(browserNav.openInNewTab(this, PPRO_UPSELL_URL))
    }

    private suspend fun Subscriptions.isUpsellEligible(): Boolean {
        return getAccessToken() == null && isEligible()
    }

    companion object {
        internal const val PRIORITY_PPRO_DISABLED = PRIORITY_DISABLED - 1
        private const val PPRO_UPSELL_ANNOTATION = "ppro_upsell_link"
        private const val PPRO_UPSELL_URL = "https://duckduckgo.com/pro?origin=funnel_pro_android_apptp_upselldisabledinfo"
    }
}
