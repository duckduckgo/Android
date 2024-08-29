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
import com.duckduckgo.mobile.android.vpn.feature.AppTpRemoteFeatures
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnRunningState.DISABLED
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnState
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason.REVOKED
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.view.message.AppTPStateMessagePlugin.DefaultAppTPMessageAction
import com.duckduckgo.subscriptions.api.Subscriptions
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

@ContributesActivePlugin(
    scope = ActivityScope::class,
    boundType = AppTPStateMessagePlugin::class,
    priority = 199,
)
class PproUpsellRevokedMessagePlugin @Inject constructor(
    private val subscriptions: Subscriptions,
    private val appTpRemoteFeatures: AppTpRemoteFeatures,
    private val browserNav: BrowserNav,
) : AppTPStateMessagePlugin {
    override fun getView(
        context: Context,
        vpnState: VpnState,
        clickListener: (DefaultAppTPMessageAction) -> Unit,
    ): View? {
        val isEligible = runBlocking { subscriptions.isUpsellEligible() }
        return if (vpnState.state == DISABLED && vpnState.stopReason == REVOKED && isEligible) {
            ViewMessageInfoDisabledBinding.inflate(LayoutInflater.from(context))
                .apply {
                    this.root.setClickableLink(
                        PPRO_UPSELL_ANNOTATION,
                        context.getText(R.string.apptp_PproUpsellInfoRevoked),
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
        return appTpRemoteFeatures.showPrivacyProUpsell().isEnabled() && getAccessToken() == null // && isEligible()
    }

    companion object {
        private const val PPRO_UPSELL_ANNOTATION = "ppro_upsell_link"
        private const val PPRO_UPSELL_URL = "https://duckduckgo.com/pro?origin=funnel_pro_android_apptp_upsellrevokedinfo"
    }
}
