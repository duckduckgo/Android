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

package com.duckduckgo.app.browser.trafficquality

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.api.AtbLifecyclePlugin
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autoconsent.api.Autoconsent
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.app.tracking.AppTrackingProtection
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.privacy.config.api.Gpc
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@ContributesMultibinding(AppScope::class)
class AndroidFeaturesPixelSender @Inject constructor(
    private val autoconsent: Autoconsent,
    private val gpc: Gpc,
    private val appTrackingProtection: AppTrackingProtection,
    private val networkProtectionState: NetworkProtectionState,
    private val pixel: Pixel,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : AtbLifecyclePlugin {

    override fun onSearchRetentionAtbRefreshed(oldAtb: String, newAtb: String) {
        coroutineScope.launch(dispatcherProvider.io()) {
            val params = mutableMapOf<String, String>()
            params[PARAM_COOKIE_POP_UP_MANAGEMENT_ENABLED] = autoconsent.isAutoconsentEnabled().toString()
            params[PARAM_GLOBAL_PRIVACY_CONTROL_ENABLED] = gpc.isEnabled().toString()
            params[PARAM_APP_TRACKING_PROTECTION_ENABLED] = appTrackingProtection.isEnabled().toString()
            params[PARAM_PRIVACY_PRO_VPN_ENABLED] = networkProtectionState.isEnabled().toString()
            pixel.fire(AppPixelName.FEATURES_ENABLED_AT_SEARCH_TIME, params)
        }
    }

    companion object {
        internal const val PARAM_COOKIE_POP_UP_MANAGEMENT_ENABLED = "cookie_pop_up_management_enabled"
        internal const val PARAM_GLOBAL_PRIVACY_CONTROL_ENABLED = "global_privacy_control_enabled"
        internal const val PARAM_APP_TRACKING_PROTECTION_ENABLED = "app_tracking_protection_enabled"
        internal const val PARAM_PRIVACY_PRO_VPN_ENABLED = "privacy_pro_vpn_enabled"
    }
}
