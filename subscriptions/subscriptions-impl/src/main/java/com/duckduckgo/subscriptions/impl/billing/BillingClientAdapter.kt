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

package com.duckduckgo.subscriptions.impl.billing

import android.app.Activity
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.PurchaseHistoryRecord

interface BillingClientAdapter {
    val ready: Boolean

    suspend fun connect(
        purchasesListener: (PurchasesUpdateResult) -> Unit,
        disconnectionListener: () -> Unit,
    ): BillingInitResult

    suspend fun getSubscriptions(productIds: List<String>): SubscriptionsResult

    suspend fun getSubscriptionsPurchaseHistory(): SubscriptionsPurchaseHistoryResult

    suspend fun launchBillingFlow(
        activity: Activity,
        productDetails: ProductDetails,
        offerToken: String,
        externalId: String,
    ): LaunchBillingFlowResult

    suspend fun launchSubscriptionUpdate(
        activity: Activity,
        productDetails: ProductDetails,
        offerToken: String,
        externalId: String,
        oldPurchaseToken: String,
        replacementMode: SubscriptionReplacementMode = SubscriptionReplacementMode.DEFERRED,
    ): LaunchBillingFlowResult
}

sealed class BillingInitResult {
    data object Success : BillingInitResult()
    data class Failure(val billingError: BillingError) : BillingInitResult()
}

sealed class SubscriptionsResult {
    data class Success(val products: List<ProductDetails>) : SubscriptionsResult()

    data class Failure(
        val billingError: BillingError? = null,
        val debugMessage: String? = null,
    ) : SubscriptionsResult()
}

sealed class SubscriptionsPurchaseHistoryResult {
    data class Success(val history: List<PurchaseHistoryRecord>) : SubscriptionsPurchaseHistoryResult()
    data object Failure : SubscriptionsPurchaseHistoryResult()
}

sealed class LaunchBillingFlowResult {
    data object Success : LaunchBillingFlowResult()
    data class Failure(val error: BillingError) : LaunchBillingFlowResult()
}

sealed class PurchasesUpdateResult {
    data class PurchasePresent(
        val purchaseToken: String,
        val packageName: String,
    ) : PurchasesUpdateResult()

    data object PurchaseAbsent : PurchasesUpdateResult()
    data object UserCancelled : PurchasesUpdateResult()
    data class Failure(val errorType: String) : PurchasesUpdateResult()
}

enum class BillingError {
    SERVICE_TIMEOUT,
    FEATURE_NOT_SUPPORTED,
    SERVICE_DISCONNECTED,
    USER_CANCELED,
    SERVICE_UNAVAILABLE,
    BILLING_UNAVAILABLE,
    ITEM_UNAVAILABLE,
    DEVELOPER_ERROR,
    ERROR,
    ITEM_ALREADY_OWNED,
    ITEM_NOT_OWNED,
    NETWORK_ERROR,
    UNKNOWN_ERROR, // for when billing returns something we don't understand
    BILLING_CRASH_ERROR, // This is our own error
}

/**
 * Defines supported replacement modes for Google Play Billing subscription updates.
 *
 * Currently, we only use the [DEFERRED] mode in our implementation.
 *
 * For a complete list of available values, refer to the official documentation:
 * https://developer.android.com/reference/com/android/billingclient/api/BillingFlowParams.SubscriptionUpdateParams.ReplacementMode
 */
enum class SubscriptionReplacementMode(val value: Int) {
    /**
     * New subscription starts after current subscription expires.
     * Best for: When you want to avoid billing complications or user requested delayed switch.
     */
    DEFERRED(6),
}
