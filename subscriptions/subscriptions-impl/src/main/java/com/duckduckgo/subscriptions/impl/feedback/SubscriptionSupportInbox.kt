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

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.subscriptions.api.PrivacyProUnifiedFeedback.PrivacyProFeedbackSource
import com.duckduckgo.subscriptions.impl.services.FeedbackBody
import com.duckduckgo.subscriptions.impl.services.SubscriptionsService
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import logcat.logcat
import javax.inject.Inject

interface SubscriptionSupportInbox {
    /**
     * This methods send the feedback to the subscription BE feedback API whenever an email along a feedback.
     *
     * This is a suspend function because the operation can take time.
     * You DO NOT need to set any dispatcher to call this suspend function.
     */
    suspend fun sendFeedback(
        email: String,
        source: PrivacyProFeedbackSource,
        category: SubscriptionFeedbackCategory,
        subCategory: SubscriptionFeedbackSubCategory?,
        description: String?,
        appName: String?,
        appPackage: String?,
        customMetadata: String,
    ): Boolean
}

@ContributesBinding(ActivityScope::class)
class RealSubscriptionSupportInbox @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val subscriptionsService: SubscriptionsService,
) : SubscriptionSupportInbox {
    override suspend fun sendFeedback(
        email: String,
        source: PrivacyProFeedbackSource,
        category: SubscriptionFeedbackCategory,
        subCategory: SubscriptionFeedbackSubCategory?,
        description: String?,
        appName: String?,
        appPackage: String?,
        customMetadata: String,
    ): Boolean = withContext(dispatcherProvider.io()) {
        runCatching {
            logcat { "Support inbox: attempt to send feedback" }
            subscriptionsService.feedback(
                FeedbackBody(
                    userEmail = email,
                    feedbackSource = source.asParams().lowercase(),
                    problemCategory = category.asParams(),
                    problemSubCategory = subCategory?.asParams(),
                    customMetadata = customMetadata,
                    feedbackText = description?.trim(),
                    appName = appName,
                    appPackage = appPackage,
                ),
            )
            logcat { "Support inbox: feedback sent!" }
            true
        }.getOrElse {
            logcat { "Support inbox: failed to send feedback. Reason ${it.message}" }
            false
        }
    }
}
