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

package com.duckduckgo.mobile.android.vpn.pixels

import com.duckduckgo.common.utils.plugins.pixel.PixelParamRemovalPlugin
import com.duckduckgo.common.utils.plugins.pixel.PixelParamRemovalPlugin.PixelParameter
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class VpnPixelParamRemovalPlugin @Inject constructor() : PixelParamRemovalPlugin {
    override fun names(): List<Pair<String, Set<PixelParameter>>> {
        return listOf(
            ATP_PIXEL_PREFIX to PixelParameter.removeAtb(),
            NETP_PIXEL_PREFIX to PixelParameter.removeAtb(),
            VPN_PIXEL_PREFIX to PixelParameter.removeAtb(),
            "m_atp_unprotected_apps_bucket_" to PixelParameter.removeAll(),
            "m_vpn_ev_moto_g_fix_" to PixelParameter.removeAll(),
            ATP_PPRO_UPSELL_PREFIX to PixelParameter.removeAtb(),
        )
    }

    companion object {
        private const val ATP_PIXEL_PREFIX = "m_atp_"
        private const val NETP_PIXEL_PREFIX = "m_netp_"
        private const val VPN_PIXEL_PREFIX = "m_vpn_"
        private const val ATP_PPRO_UPSELL_PREFIX = "m_atp_ppro-upsell"
    }
}
