/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.integration

import com.duckduckgo.app.global.plugins.pixel.PixelInterceptorPlugin
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber
import javax.inject.Inject

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = PixelInterceptorPlugin::class
)
class VpnNetworkStackVariantPixelInterceptor @Inject constructor(
    private val vpnNetworkStackVariantStore: VpnNetworkStackVariantStore,
) : Interceptor, PixelInterceptorPlugin {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
        val pixel = chain.request().url.pathSegments.last()
        val url = if (isInPixelList(pixel)) {
            chain.request().url.newBuilder()
                .addQueryParameter("networkLayer", vpnNetworkStackVariantStore.variant ?: "unknown")
                .build()
        } else {
            chain.request().url
        }

        Timber.d("Pixel interceptor: $url")

        return chain.proceed(request.url(url).build())
    }

    private fun isInPixelList(pixel: String): Boolean {
        return PIXELS.firstOrNull { pixel.startsWith(it) } != null
    }

    companion object {
        // list here the pixels that except from this interceptor
        internal val PIXELS = listOf(
            "m_atp_ev_enabled_d",
            "m_atp_ev_enabled_tracker_activity_d",
            "m_atp_ev_enabled_reminder_notification_d",
            "m_atp_ev_sys_kill_c",
            "m_atp_ev_sys_kill_d",
            "m_atp_ev_selected_disable_protection_c",
            "m_atp_ev_submit_disable_app_protection_dialog_c",
            "m_atp_ev_submit_disable_app_protection_dialog_d",
            "m_atp_did_restart_vpn_on_bad_health_c",
            "m_atp_did_restart_vpn_on_bad_health_d",
        )
    }

    override fun getInterceptor(): Interceptor {
        return this
    }
}
