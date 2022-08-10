/*
 * Copyright (c) 2022 DuckDuckGo
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

import androidx.annotation.VisibleForTesting
import com.duckduckgo.app.global.AppUrl
import com.duckduckgo.app.global.plugins.pixel.PixelInterceptorPlugin
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = PixelInterceptorPlugin::class
)
class PixelAddAtbQueryParamInterceptor @Inject constructor(
    private val statisticsDataStore: StatisticsDataStore,
    private val variantManager: VariantManager,
) : Interceptor, PixelInterceptorPlugin {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
        val pixel = chain.request().url.pathSegments.last()

        val url = if (PIXELS_SET.contains(getPixelName(pixel))) {
            chain.request().url.newBuilder().addQueryParameter(AppUrl.ParamKey.ATB, getAtbInfo()).build()
        } else {
            chain.request().url
        }

        return chain.proceed(request.url(url).build())
    }

    override fun getInterceptor(): Interceptor = this

    private fun getPixelName(pixel: String): String {
        val suffixIndex = pixel.indexOf(PIXEL_PLATFORM_SUFFIX).takeIf { it > 0 } ?: 0
        return pixel.substring(0, suffixIndex)
    }

    private fun getAtbInfo() = statisticsDataStore.atb?.formatWithVariant(variantManager.getVariant()) ?: ""

    companion object {
        private const val PIXEL_PLATFORM_SUFFIX = "_android"

        @VisibleForTesting
        internal val PIXELS_SET = setOf(
            AppPixelName.PRIVACY_DASHBOARD_OPENED.pixelName,
            AppPixelName.PRIVACY_DASHBOARD_SCORECARD.pixelName,
        )
    }
}
