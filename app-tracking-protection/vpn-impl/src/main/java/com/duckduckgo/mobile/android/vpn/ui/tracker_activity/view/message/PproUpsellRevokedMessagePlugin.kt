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
import androidx.core.view.doOnAttach
import com.duckduckgo.anvil.annotations.ContributesActivePlugin
import com.duckduckgo.app.tabs.BrowserNav
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnRunningState.DISABLED
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnState
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason.REVOKED
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.view.message.AppTPStateMessagePlugin.Companion.PRIORITY_REVOKED
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.view.message.AppTPStateMessagePlugin.DefaultAppTPMessageAction
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.view.message.PproUpsellRevokedMessagePlugin.Companion.PRIORITY_PPRO_REVOKED
import com.duckduckgo.subscriptions.api.Subscriptions
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

@ContributesActivePlugin(
    scope = AppScope::class,
    boundType = AppTPStateMessagePlugin::class,
    priority = PRIORITY_PPRO_REVOKED,
)
class PproUpsellRevokedMessagePlugin @Inject constructor(
    private val subscriptions: Subscriptions,
    private val browserNav: BrowserNav,
    private val deviceShieldPixels: DeviceShieldPixels,
) : AppTPStateMessagePlugin {
    override fun getView(
        context: Context,
        vpnState: VpnState,
        clickListener: (DefaultAppTPMessageAction) -> Unit,
    ): View? {
        val isEligible = runBlocking { subscriptions.isUpsellEligible() }
        return if (vpnState.state == DISABLED && vpnState.stopReason == REVOKED && isEligible) {
            AppTpDisabledInfoPanel(context).apply {
                setClickableLink(
                    PPRO_UPSELL_ANNOTATION,
                    context.getText(R.string.apptp_PproUpsellInfoRevoked),
                ) { context.launchPPro() }
                doOnAttach {
                    deviceShieldPixels.reportPproUpsellRevokedInfoShown()
                }
            }
        } else {
            null
        }
    }

    private fun Context.launchPPro() {
        deviceShieldPixels.reportPproUpsellRevokedInfoLinkClicked()
        startActivity(browserNav.openInNewTab(this, PPRO_UPSELL_URL))
    }

    private suspend fun Subscriptions.isUpsellEligible(): Boolean {
        return getAccessToken() == null && isEligible()
    }

    companion object {
        internal const val PRIORITY_PPRO_REVOKED = PRIORITY_REVOKED - 1
        private const val PPRO_UPSELL_ANNOTATION = "ppro_upsell_link"
        private const val PPRO_UPSELL_URL = "https://duckduckgo.com/pro?origin=funnel_pro_android_apptp_revoked"
    }
}
