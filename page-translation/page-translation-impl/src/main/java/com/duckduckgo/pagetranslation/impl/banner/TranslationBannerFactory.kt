/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.pagetranslation.impl.banner

import android.content.Context
import android.os.Build
import android.view.accessibility.AccessibilityManager
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.pagetranslation.impl.language.DeviceLanguages
import com.duckduckgo.pagetranslation.impl.offer.TranslationOfferSource
import com.duckduckgo.pagetranslation.impl.translate.TranslateRequest
import javax.inject.Inject

class TranslationBannerFactory @Inject constructor(
    private val offerSource: TranslationOfferSource,
    private val translateRequest: TranslateRequest,
    private val deviceLanguages: DeviceLanguages,
    private val dispatchers: DispatcherProvider,
) {
    fun create(context: Context): TranslationBannerView {
        val controller = BannerController(
            offerSource = offerSource,
            translateRequest = translateRequest,
            timeoutMillis = recommendedTimeoutMillis(context),
        )
        return TranslationBannerView(context).also { it.bind(controller, deviceLanguages, dispatchers.main()) }
    }

    private fun recommendedTimeoutMillis(context: Context): Long {
        val accessibilityManager = context.getSystemService(AccessibilityManager::class.java)
        return when {
            Build.VERSION.SDK_INT >= 29 -> accessibilityManager.getRecommendedTimeoutMillis(
                TIMEOUT_MILLIS,
                AccessibilityManager.FLAG_CONTENT_ICONS or AccessibilityManager.FLAG_CONTENT_TEXT or AccessibilityManager.FLAG_CONTENT_CONTROLS,
            ).toLong()
            accessibilityManager.isTouchExplorationEnabled -> Long.MAX_VALUE
            else -> TIMEOUT_MILLIS.toLong()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000
    }
}
