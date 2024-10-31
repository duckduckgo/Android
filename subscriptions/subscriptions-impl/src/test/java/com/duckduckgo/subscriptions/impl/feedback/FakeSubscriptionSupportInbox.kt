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

package com.duckduckgo.subscriptions.impl.feedback

import com.duckduckgo.subscriptions.api.PrivacyProUnifiedFeedback.PrivacyProFeedbackSource
import com.duckduckgo.subscriptions.impl.services.FeedbackBody

class FakeSubscriptionSupportInbox : SubscriptionSupportInbox {
    private var _sendSucceeds: Boolean = true
    private var _lastFeedbackBody: FeedbackBody? = null
    fun setSendFeedbackResult(success: Boolean) {
        _sendSucceeds = success
    }

    fun getLastSentFeedback(): FeedbackBody? = _lastFeedbackBody

    override suspend fun sendFeedback(
        email: String,
        source: PrivacyProFeedbackSource,
        category: SubscriptionFeedbackCategory,
        subCategory: SubscriptionFeedbackSubCategory?,
        description: String?,
        appName: String?,
        appPackage: String?,
        customMetadata: String,
    ): Boolean {
        _lastFeedbackBody = FeedbackBody(
            userEmail = email,
            feedbackSource = source.asParams(),
            problemCategory = category.asParams(),
            customMetadata = customMetadata,
            feedbackText = description,
            appName = appName,
            appPackage = appPackage,
            problemSubCategory = subCategory?.asParams(),
        )
        return _sendSucceeds
    }
}
