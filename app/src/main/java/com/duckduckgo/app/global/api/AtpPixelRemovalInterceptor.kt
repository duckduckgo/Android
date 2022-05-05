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

package com.duckduckgo.app.global.api

import com.duckduckgo.app.global.AppUrl
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
class AtpPixelRemovalInterceptor @Inject constructor() : Interceptor, PixelInterceptorPlugin {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
        val pixel = chain.request().url.pathSegments.last()
        val url = if (pixel.startsWith(PIXEL_PREFIX) && !isInExceptionList(pixel)) {
            chain.request().url.newBuilder()
                .removeAllQueryParameters(AppUrl.ParamKey.ATB)
                .build()
        } else {
            chain.request().url
        }

        Timber.d("Pixel interceptor: $url")

        return chain.proceed(request.url(url).build())
    }

    private fun isInExceptionList(pixel: String): Boolean {
        return PIXEL_EXCEPTIONS.firstOrNull { pixel.startsWith(it) } != null
    }

    companion object {
        private const val PIXEL_PREFIX = "m_atp_"

        // list here the pixels that except from this interceptor
        private val PIXEL_EXCEPTIONS = listOf(
            "m_atp_imp_beta_instructions_d",
            "m_atp_imp_beta_instructions_c",
            "m_atp_imp_article_d",
            "m_atp_imp_article_c",
            "m_atp_imp_exclusion_list_activity_u",
            "m_atp_imp_exclusion_list_activity_d",
            "m_atp_imp_exclusion_list_activity_c",
            "m_atp_ev_exclusion_list_activity_open_trackers_u",
            "m_atp_ev_exclusion_list_activity_open_trackers_d",
            "m_atp_ev_exclusion_list_activity_open_trackers_c",
            "m_atp_imp_company_trackers_activity_u",
            "m_atp_imp_company_trackers_activity_d",
            "m_atp_imp_company_trackers_activity_c",
            "m_atp_imp_tracker_activity_detail_u",
            "m_atp_imp_tracker_activity_detail_d",
            "m_atp_imp_tracker_activity_detail_c",
            "m_atp_imp_company_trackers_activity_u",
            "m_atp_imp_company_trackers_activity_d",
            "m_atp_imp_company_trackers_activity_c",
            "m_atp_imp_manage_recent_app_settings_activity_u",
            "m_atp_imp_manage_recent_app_settings_activity_d",
            "m_atp_imp_manage_recent_app_settings_activity_c",
        )
    }

    override fun getInterceptor(): Interceptor {
        return this
    }
}
