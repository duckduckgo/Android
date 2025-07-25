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

package com.duckduckgo.subscriptions.impl.survey

import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.survey.api.SurveyParameterPlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class PproPurchasePlatformSurveyParameterPlugin @Inject constructor(
    private val subscriptionsManager: SubscriptionsManager,
) : SurveyParameterPlugin {
    override fun matches(paramKey: String): Boolean = paramKey == "ppro_platform"

    override suspend fun evaluate(paramKey: String): String = subscriptionsManager.getSubscription()?.platform?.lowercase() ?: ""
}

@ContributesMultibinding(AppScope::class)
class PproBillingParameterPlugin @Inject constructor(
    private val subscriptionsManager: SubscriptionsManager,
) : SurveyParameterPlugin {
    override fun matches(paramKey: String): Boolean = paramKey == "ppro_billing"

    override suspend fun evaluate(paramKey: String): String {
        return subscriptionsManager.getSubscription()?.billingPeriod ?: ""
    }
}

@ContributesMultibinding(AppScope::class)
class PproDaysSincePurchaseSurveyParameterPlugin @Inject constructor(
    private val subscriptionsManager: SubscriptionsManager,
    private val currentTimeProvider: CurrentTimeProvider,
) : SurveyParameterPlugin {
    override fun matches(paramKey: String): Boolean = paramKey == "ppro_days_since_purchase"

    override suspend fun evaluate(paramKey: String): String {
        val startedAt = subscriptionsManager.getSubscription()?.startedAt
        return startedAt?.let {
            "${TimeUnit.MILLISECONDS.toDays(currentTimeProvider.currentTimeMillis() - it)}"
        } ?: "0"
    }
}

@ContributesMultibinding(AppScope::class)
class PproDaysUntilExpirySurveyParameterPlugin @Inject constructor(
    private val subscriptionsManager: SubscriptionsManager,
    private val currentTimeProvider: CurrentTimeProvider,
) : SurveyParameterPlugin {
    override fun matches(paramKey: String): Boolean = paramKey == "ppro_days_until_exp"

    override suspend fun evaluate(paramKey: String): String {
        val expiry = subscriptionsManager.getSubscription()?.expiresOrRenewsAt
        return expiry?.let {
            "${TimeUnit.MILLISECONDS.toDays(expiry - currentTimeProvider.currentTimeMillis())}"
        } ?: "0"
    }
}

@ContributesMultibinding(AppScope::class)
class PproStatusParameterPlugin @Inject constructor(
    private val subscriptionsManager: SubscriptionsManager,
) : SurveyParameterPlugin {
    private val invalidCharRegex = Regex("[ -]")
    override fun matches(paramKey: String): Boolean = paramKey == "ppro_status"

    override suspend fun evaluate(paramKey: String): String =
        subscriptionsManager.getSubscription()?.status?.statusName?.lowercase()?.replace(invalidCharRegex, "_") ?: ""
}
