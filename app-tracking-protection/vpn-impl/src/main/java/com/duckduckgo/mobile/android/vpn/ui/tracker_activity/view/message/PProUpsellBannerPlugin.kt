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
import com.duckduckgo.common.ui.view.MessageCta
import com.duckduckgo.common.ui.view.MessageCta.Message
import com.duckduckgo.common.ui.view.MessageCta.MessageType.REMOTE_MESSAGE
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnState
import com.duckduckgo.mobile.android.vpn.ui.onboarding.VpnStore
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.view.message.AppTPStateMessagePlugin.Companion.PRIORITY_ACTION_REQUIRED
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.view.message.AppTPStateMessagePlugin.DefaultAppTPMessageAction
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.view.message.PProUpsellBannerPlugin.Companion.PRIORITY_PPRO_UPSELL_BANNER
import com.duckduckgo.subscriptions.api.Subscriptions
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@ContributesActivePlugin(
    scope = AppScope::class,
    boundType = AppTPStateMessagePlugin::class,
    priority = PRIORITY_PPRO_UPSELL_BANNER,
)
class PProUpsellBannerPlugin @Inject constructor(
    private val subscriptions: Subscriptions,
    private val browserNav: BrowserNav,
    private val vpnStore: VpnStore,
    private val deviceShieldPixels: DeviceShieldPixels,
    private val appTPStateMessageToggle: AppTPStateMessageToggle,
) : AppTPStateMessagePlugin {
    override fun getView(
        context: Context,
        vpnState: VpnState,
        clickListener: (DefaultAppTPMessageAction) -> Unit,
    ): View? {
        val isEligible = runBlocking { subscriptions.isUpsellEligible() && !vpnStore.isPproUpsellBannerDismised() }
        return if (isEligible) {
            val actionText: String
            runBlocking {
                actionText = if (subscriptions.isFreeTrialEligible() && appTPStateMessageToggle.freeTrialCopy().isEnabled()) {
                    context.getString(R.string.apptp_PproUpsellBannerAction_freeTrial)
                } else {
                    context.getString(R.string.apptp_PproUpsellBannerAction)
                }
            }
            MessageCta(context)
                .apply {
                    this.setMessage(
                        Message(
                            topIllustration = com.duckduckgo.mobile.android.R.drawable.ic_privacy_pro,
                            title = context.getString(R.string.apptp_PproUpsellBannerTitle),
                            subtitle = context.getString(R.string.apptp_PproUpsellBannerMessage_freeTrial),
                            action = actionText,
                            messageType = REMOTE_MESSAGE,
                        ),
                    )
                    this.onCloseButtonClicked {
                        deviceShieldPixels.reportPproUpsellBannerDismissed()
                        vpnStore.dismissPproUpsellBanner()
                        this.gone()
                    }

                    this.onPrimaryActionClicked {
                        deviceShieldPixels.reportPproUpsellBannerLinkClicked()
                        context.launchPPro()
                    }
                    this.doOnAttach {
                        deviceShieldPixels.reportPproUpsellBannerShown()
                    }
                }
        } else {
            null
        }
    }

    private fun Context.launchPPro() {
        startActivity(browserNav.openInNewTab(this, PPRO_UPSELL_URL))
    }

    companion object {
        internal const val PRIORITY_PPRO_UPSELL_BANNER = PRIORITY_ACTION_REQUIRED - 1
        private const val PPRO_UPSELL_URL = "https://duckduckgo.com/pro?origin=funnel_apptrackingprotection_android__banner"
    }
}
