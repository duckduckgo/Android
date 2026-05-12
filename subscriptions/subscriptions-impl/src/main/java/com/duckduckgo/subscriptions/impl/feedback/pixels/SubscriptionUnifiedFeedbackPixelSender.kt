/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.subscriptions.impl.feedback.pixels

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface SubscriptionUnifiedFeedbackPixelSender {
    fun sendSubscriptionFeatureRequest(parameters: Map<String, String>)
    fun sendSubscriptionGeneralFeedback(parameters: Map<String, String>)
    fun sendSubscriptionReportIssue(parameters: Map<String, String>)
    fun reportSubscriptionFeedbackGeneralScreenShown()
    fun reportSubscriptionFeedbackActionsScreenShown(parameters: Map<String, String>)
    fun reportSubscriptionFeedbackCategoryScreenShown(parameters: Map<String, String>)
    fun reportSubscriptionFeedbackSubcategoryScreenShown(parameters: Map<String, String>)
    fun reportSubscriptionFeedbackSubmitScreenShown(parameters: Map<String, String>)
    fun reportSubscriptionFeedbackSubmitScreenFaqClicked(parameters: Map<String, String>)
}

@ContributesBinding(AppScope::class)
class RealSubscriptionUnifiedFeedbackPixelSender @Inject constructor(
    private val pixelSender: Pixel,
) : SubscriptionUnifiedFeedbackPixelSender {
    override fun sendSubscriptionFeatureRequest(parameters: Map<String, String>) {
        fire(SubscriptionUnifiedFeedbackPixel.SUBSCRIPTION_FEEDBACK_FEATURE_REQUEST, parameters)
    }

    override fun sendSubscriptionGeneralFeedback(parameters: Map<String, String>) {
        fire(SubscriptionUnifiedFeedbackPixel.SUBSCRIPTION_FEEDBACK_GENERAL_FEEDBACK, parameters)
    }

    override fun sendSubscriptionReportIssue(parameters: Map<String, String>) {
        fire(SubscriptionUnifiedFeedbackPixel.SUBSCRIPTION_FEEDBACK_REPORT_ISSUE, parameters)
    }

    override fun reportSubscriptionFeedbackGeneralScreenShown() {
        fire(SubscriptionUnifiedFeedbackPixel.IMPRESSION_SUBSCRIPTION_FEEDBACK_GENERAL_SCREEN)
    }

    override fun reportSubscriptionFeedbackActionsScreenShown(parameters: Map<String, String>) {
        fire(SubscriptionUnifiedFeedbackPixel.IMPRESSION_SUBSCRIPTION_FEEDBACK_ACTION_SCREEN, parameters)
    }

    override fun reportSubscriptionFeedbackCategoryScreenShown(parameters: Map<String, String>) {
        fire(SubscriptionUnifiedFeedbackPixel.IMPRESSION_SUBSCRIPTION_FEEDBACK_CATEGORY_SCREEN, parameters)
    }

    override fun reportSubscriptionFeedbackSubcategoryScreenShown(parameters: Map<String, String>) {
        fire(SubscriptionUnifiedFeedbackPixel.IMPRESSION_SUBSCRIPTION_FEEDBACK_SUBCATEGORY_SCREEN, parameters)
    }

    override fun reportSubscriptionFeedbackSubmitScreenShown(parameters: Map<String, String>) {
        fire(SubscriptionUnifiedFeedbackPixel.IMPRESSION_SUBSCRIPTION_FEEDBACK_SUBMIT_SCREEN, parameters)
    }

    override fun reportSubscriptionFeedbackSubmitScreenFaqClicked(parameters: Map<String, String>) {
        fire(SubscriptionUnifiedFeedbackPixel.SUBSCRIPTION_FEEDBACK_SUBMIT_SCREEN_FAQ_CLICK, parameters)
    }

    private fun fire(
        pixel: SubscriptionUnifiedFeedbackPixel,
        params: Map<String, String> = emptyMap(),
    ) {
        pixel.getPixelNames().forEach { (pixelType, pixelName) ->
            pixelSender.fire(pixelName = pixelName, type = pixelType, parameters = params)
        }
    }
}
