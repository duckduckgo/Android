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

class FakeCustomMetadataProvider : FeedbackCustomMetadataProvider {
    override suspend fun getCustomMetadata(
        category: SubscriptionFeedbackCategory,
        appPackageId: String?,
    ): String = when (category) {
        SubscriptionFeedbackCategory.VPN -> "VPN raw metadata"
        SubscriptionFeedbackCategory.ITR -> "ITR raw metadata"
        SubscriptionFeedbackCategory.SUBS_AND_PAYMENTS -> "SUBS_AND_PAYMENTS raw metadata"
        SubscriptionFeedbackCategory.PIR -> "PIR raw metadata"
        else -> ""
    }

    override suspend fun getCustomMetadataEncoded(
        category: SubscriptionFeedbackCategory,
        appPackageId: String?,
    ): String = when (category) {
        SubscriptionFeedbackCategory.VPN -> "VPN encoded metadata"
        SubscriptionFeedbackCategory.ITR -> "ITR encoded metadata"
        SubscriptionFeedbackCategory.SUBS_AND_PAYMENTS -> "SUBS_AND_PAYMENTS encoded metadata"
        SubscriptionFeedbackCategory.PIR -> "PIR encoded metadata"
        else -> ""
    }
}
