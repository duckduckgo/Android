/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.browser

import android.net.Uri
import androidx.core.net.toUri
import com.duckduckgo.app.global.AppUrl.ParamKey
import com.duckduckgo.app.global.AppUrl.ParamValue
import com.duckduckgo.app.referral.AppReferrerDataStore
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import timber.log.Timber

interface RequestRewriter {
    fun shouldRewriteRequest(uri: Uri): Boolean
    fun rewriteRequestWithCustomQueryParams(request: Uri): Uri
    fun addCustomQueryParams(currentUrl: String, builder: Uri.Builder)
}

class DuckDuckGoRequestRewriter(
    private val duckDuckGoUrlDetector: DuckDuckGoUrlDetector,
    private val statisticsStore: StatisticsDataStore,
    private val variantManager: VariantManager,
    private val appReferrerDataStore: AppReferrerDataStore
) : RequestRewriter {

    override fun rewriteRequestWithCustomQueryParams(request: Uri): Uri {
        val builder = Uri.Builder()
            .authority(request.authority)
            .scheme(request.scheme)
            .path(request.path)
            .fragment(request.fragment)

        request.queryParameterNames
            .filter { it != ParamKey.SOURCE && it != ParamKey.ATB }
            .forEach { builder.appendQueryParameter(it, request.getQueryParameter(it)) }

        addCustomQueryParams(request.toString(), builder)
        val newUri = builder.build()

        Timber.d("Rewriting request\n$request [original]\n$newUri [rewritten]")
        return newUri
    }

    override fun shouldRewriteRequest(uri: Uri): Boolean {
        return duckDuckGoUrlDetector.isDuckDuckGoQueryUrl(uri.toString()) &&
                !uri.queryParameterNames.containsAll(arrayListOf(ParamKey.SOURCE, ParamKey.ATB))
    }

    /**
     * Applies cohort (atb) https://duck.co/help/privacy/atb and
     * source (t) https://duck.co/help/privacy/t params to url
     * Also keeps current Vertical selection (Images, News, Videos, etc...)
     */
    override fun addCustomQueryParams(currentUrl: String, builder: Uri.Builder) {
        val atb = statisticsStore.atb
        if (atb != null) {
            builder.appendQueryParameter(ParamKey.ATB, atb.formatWithVariant(variantManager.getVariant()))
        }

        val sourceValue = if (appReferrerDataStore.installedFromEuAuction) ParamValue.SOURCE_EU_AUCTION else ParamValue.SOURCE

        if (variantManager.getVariant().hasFeature(VariantManager.VariantFeature.SerpHeaderRemoval)){
            builder.appendQueryParameter(ParamKey.HIDE_SERP, ParamValue.HIDE_SERP)
        }

        if (duckDuckGoUrlDetector.isDuckDuckGoVerticalUrl(currentUrl)){
            val vertical = duckDuckGoUrlDetector.extractVertical(currentUrl)
            builder.appendQueryParameter(ParamKey.VERTICAL_REWRITE, vertical)
        }

        builder.appendQueryParameter(ParamKey.SOURCE, sourceValue)
    }
}
