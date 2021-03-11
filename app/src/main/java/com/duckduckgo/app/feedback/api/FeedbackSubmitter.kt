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

import android.os.Build
import com.duckduckgo.app.browser.BuildConfig
import com.duckduckgo.app.feedback.ui.negative.FeedbackType.MainReason
import com.duckduckgo.app.feedback.ui.negative.FeedbackType.MainReason.*
import com.duckduckgo.app.feedback.ui.negative.FeedbackType.SubReason
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.AppPixelName.FEEDBACK_NEGATIVE_SUBMISSION
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.*

interface FeedbackSubmitter {

    suspend fun sendNegativeFeedback(mainReason: MainReason, subReason: SubReason?, openEnded: String)
    suspend fun sendPositiveFeedback(openEnded: String?)
    suspend fun sendBrokenSiteFeedback(openEnded: String, brokenSite: String?)
    suspend fun sendUserRated()
}

class FireAndForgetFeedbackSubmitter(
    private val feedbackService: FeedbackService,
    private val variantManager: VariantManager,
    private val apiKeyMapper: SubReasonApiMapper,
    private val statisticsDataStore: StatisticsDataStore,
    private val pixel: Pixel
) : FeedbackSubmitter {
    override suspend fun sendNegativeFeedback(mainReason: MainReason, subReason: SubReason?, openEnded: String) {
        Timber.i("User provided negative feedback: {$openEnded}. mainReason = $mainReason, subReason = $subReason")

        val category = categoryFromMainReason(mainReason)
        val subcategory = apiKeyMapper.apiKeyFromSubReason(subReason)

        sendPixel(pixelForNegativeFeedback(category, subcategory))

        GlobalScope.launch {
            runCatching {
                submitFeedback(
                    openEnded = openEnded,
                    rating = NEGATIVE_FEEDBACK,
                    category = category,
                    subcategory = subcategory
                )
            }
                .onSuccess { Timber.i("Successfully submitted feedback") }
                .onFailure { Timber.w(it, "Failed to send feedback") }
        }
    }

    override suspend fun sendPositiveFeedback(openEnded: String?) {
        Timber.i("User provided positive feedback: {$openEnded}")

        sendPixel(pixelForPositiveFeedback())

        if (openEnded != null) {
            GlobalScope.launch {
                runCatching { submitFeedback(openEnded = openEnded, rating = POSITIVE_FEEDBACK) }
                    .onSuccess { Timber.i("Successfully submitted feedback") }
                    .onFailure { Timber.w(it, "Failed to send feedback") }
            }
        }
    }

    override suspend fun sendBrokenSiteFeedback(openEnded: String, brokenSite: String?) {
        Timber.i("User provided broken site report through feedback, url:{$brokenSite}, comment:{$openEnded}")

        val category = categoryFromMainReason(WEBSITES_NOT_LOADING)
        val subcategory = apiKeyMapper.apiKeyFromSubReason(null)
        sendPixel(pixelForNegativeFeedback(category, subcategory))

        GlobalScope.launch {
            runCatching {
                submitFeedback(
                    rating = NEGATIVE_FEEDBACK,
                    url = brokenSite,
                    openEnded = openEnded,
                    category = category
                )
            }
                .onSuccess { Timber.i("Successfully submitted broken site feedback") }
                .onFailure { Timber.w(it, "Failed to send broken site feedback") }
        }
    }

    override suspend fun sendUserRated() {
        Timber.i("User indicated they'd rate the app")
        sendPixel(pixelForPositiveFeedback())
    }

    private fun sendPixel(pixelName: String) {
        Timber.d("Firing feedback pixel: $pixelName")
        pixel.fire(pixelName)
    }

    private suspend fun submitFeedback(
        openEnded: String,
        rating: String,
        category: String? = null,
        subcategory: String? = null,
        url: String? = null,
        reason: String = FeedbackService.REASON_GENERAL
    ) {
        feedbackService.submitFeedback(
            reason = reason,
            category = category,
            subcategory = subcategory,
            rating = rating,
            url = url,
            comment = openEnded,
            version = version(),
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            api = Build.VERSION.SDK_INT,
            atb = atbWithVariant()
        )
    }

    private fun categoryFromMainReason(mainReason: MainReason): String {
        return when (mainReason) {
            MISSING_BROWSING_FEATURES -> "browserFeatures"
            WEBSITES_NOT_LOADING -> "brokenSites"
            SEARCH_NOT_GOOD_ENOUGH -> "badResults"
            NOT_ENOUGH_CUSTOMIZATIONS -> "customization"
            APP_IS_SLOW_OR_BUGGY -> "performance"
            OTHER -> "other"
        }
    }

    private fun pixelForNegativeFeedback(category: String, subcategory: String): String {
        return String.format(Locale.US, FEEDBACK_NEGATIVE_SUBMISSION.pixelName, NEGATIVE_FEEDBACK, category, subcategory)
    }

    private fun pixelForPositiveFeedback(): String {
        return String.format(Locale.US, Pixel.AppPixelName.FEEDBACK_POSITIVE_SUBMISSION.pixelName, POSITIVE_FEEDBACK)
    }

    private fun version(): String {
        return BuildConfig.VERSION_NAME
    }

    private fun atbWithVariant(): String {
        return statisticsDataStore.atb?.formatWithVariant(variantManager.getVariant()) ?: ""
    }

    companion object {
        private const val POSITIVE_FEEDBACK = "positive"
        private const val NEGATIVE_FEEDBACK = "negative"
    }
}
