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

package com.duckduckgo.networkprotection.impl.cohort

import androidx.annotation.VisibleForTesting
import com.duckduckgo.common.utils.plugins.pixel.PixelInterceptorPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.NETP_SETTINGS_PRESSED
import com.squareup.anvil.annotations.ContributesMultibinding
import logcat.logcat
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import javax.inject.Inject

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = PixelInterceptorPlugin::class,
)
class NetpCohortPixelInterceptor @Inject constructor(
    private val cohortStore: NetpCohortStore,
) : PixelInterceptorPlugin, Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
        val pixel = chain.request().url.pathSegments.last()

        val url = if (pixel.startsWith(PIXEL_PREFIX) && !EXCEPTIONS.any { exception -> pixel.startsWith(exception) }) {
            // IF there is no cohort for NetP we just drop the pixel request
            cohortStore.cohortLocalDate?.let {
                chain.request().url.newBuilder().build()
            } ?: return dummyResponse(chain)
        } else {
            chain.request().url
        }

        return chain.proceed(request.url(url).build())
    }

    private fun dummyResponse(chain: Interceptor.Chain): Response {
        logcat { "Pixel URL request dropped: ${chain.request()}" }

        return Response.Builder()
            .code(200)
            .protocol(Protocol.HTTP_2)
            .body("NetP pixel dropped".toResponseBody())
            .message("Dropped NetP pixel because no cohort is assigned")
            .request(chain.request())
            .build()
    }

    override fun getInterceptor(): Interceptor {
        return this
    }

    companion object {
        @VisibleForTesting
        private const val PIXEL_PREFIX = "m_netp"
        private val EXCEPTIONS = listOf(
            "m_netp_ev_backend_api_error",
            "m_netp_ev_wireguard_error",
            "m_netp_imp_vpn_conflict_dialog",
            "m_netp_imp_always_on_conflict_dialog",
            "m_netp_imp_info_vpn",
            "m_netp_imp_faqs",
            "m_netp_imp_terms",
            "m_netp_ev_waitlist_notification_shown",
            "m_netp_ev_waitlist_notification_cancelled",
            "m_netp_ev_waitlist_notification_launched",
            "m_netp_ev_waitlist_enabled",
            "m_netp_ev_terms_accepted",
            "m_netp_imp_geoswitching",
            "m_netp_ev_geoswitching",
            NETP_SETTINGS_PRESSED.pixelName,
        )
    }
}
