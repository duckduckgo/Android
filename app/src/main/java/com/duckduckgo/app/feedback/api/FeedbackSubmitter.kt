/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.feedback.api

import com.duckduckgo.app.browser.BuildConfig
import com.duckduckgo.app.feedback.ui.negative.FeedbackType.MainReason
import com.duckduckgo.app.feedback.ui.negative.FeedbackType.SubReason
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.pixels.Pixel
import timber.log.Timber
import java.util.*


interface FeedbackSubmitter {

    suspend fun sendNegativeFeedback(mainReason: MainReason, subReason: SubReason?, openEnded: String)
    suspend fun sendPositiveFeedback(openEnded: String)
}

class FireAndForgetFeedbackSubmitter(
    private val feedbackService: FeedbackService,
    private val variantManager: VariantManager,
    private val apiKeyMapper: SubReasonApiMapper,
    private val pixel: Pixel
) : FeedbackSubmitter {
    override suspend fun sendNegativeFeedback(mainReason: MainReason, subReason: SubReason?, openEnded: String) {
        val category = categoryFromMainReason(mainReason)
        val subcategory = apiKeyMapper.apiKeyFromSubReason(subReason)

        kotlin.runCatching { feedbackService.submit(category, subcategory, openEnded, version()).await() }
            .onSuccess { Timber.i("Successfully submitted feedback") }
            .onFailure { Timber.w(it, "Failed to send feedback") }

        val pixelName = pixelFromReasons(category, subcategory)

        Timber.i("will eventually fire pixel: $pixelName")
        //pixel.fire(pixelName)
    }

    override suspend fun sendPositiveFeedback(openEnded: String) {
        kotlin.runCatching { feedbackService.submit(null, null, openEnded, version()).await() }
            .onSuccess { Timber.i("Successfully submitted feedback") }
            .onFailure { Timber.w("Failed to send feedback") }
    }

    private fun categoryFromMainReason(mainReason: MainReason): String {
        return when (mainReason) {
            MainReason.MISSING_BROWSING_FEATURES -> "browser_features"
            MainReason.SEARCH_NOT_GOOD_ENOUGH -> "bad_results"
            MainReason.NOT_ENOUGH_CUSTOMIZATIONS -> "customization"
            MainReason.APP_IS_SLOW_OR_BUGGY -> "performance"
            MainReason.OTHER -> "other"
            else -> "unknown"
        }
    }

    private fun pixelFromReasons(category: String, subcategory: String): String {
        val formattedPixel = String.format(Locale.US, Pixel.PixelName.FEEDBACK_SUBMISSION.pixelName, category, subcategory)
        Timber.i("Formatted pixel as [$formattedPixel]")
        return formattedPixel
    }



    private fun version(): String {
        val variantKey = variantManager.getVariant().key
        val formattedVariantKey = if (variantKey.isBlank()) " " else " $variantKey "
        return "${BuildConfig.VERSION_NAME}$formattedVariantKey"
    }
}