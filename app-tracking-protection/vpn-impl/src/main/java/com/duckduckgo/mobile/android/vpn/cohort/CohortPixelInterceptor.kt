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

package com.duckduckgo.mobile.android.vpn.cohort

import com.duckduckgo.app.global.plugins.pixel.PixelInterceptorPlugin
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import timber.log.Timber
import javax.inject.Inject

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = PixelInterceptorPlugin::class
)
class CohortPixelInterceptor @Inject constructor(
    private val cohortCalculator: CohortCalculator,
    private val cohortStore: CohortStore,
) : PixelInterceptorPlugin, Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
        val pixel = chain.request().url.pathSegments.last()

        val url = if (pixel.startsWith(PIXEL_PREFIX) && !EXCEPTIONS.any { exception -> pixel.startsWith(exception) }) {
            // IF there is no cohort for ATP we just drop the pixel request
            // ELSE we add the cohort param
            cohortStore.getCohortStoredLocalDate()?.let {
                chain.request().url.newBuilder().addQueryParameter(COHORT_PARAM, cohortCalculator.calculateCohortForDate(it)).build()
            } ?: return dummyResponse(chain)
        } else {
            chain.request().url
        }

        return chain.proceed(request.url(url).build())
    }

    private fun dummyResponse(chain: Interceptor.Chain): Response {
        Timber.v("Pixel URL request dropped: ${chain.request()}")

        return Response.Builder()
            .code(200)
            .protocol(Protocol.HTTP_2)
            .body("ATP pixel dropped".toResponseBody())
            .message("Dropped ATP pixel because no cohort is assigned")
            .request(chain.request())
            .build()
    }

    override fun getInterceptor(): Interceptor {
        return this
    }

    companion object {
        private const val COHORT_PARAM = "atp_cohort"
        private const val PIXEL_PREFIX = "m_atp_"
        private val EXCEPTIONS = listOf(
            "m_atp_ev_enabled_onboarding_",
        )
    }
}
