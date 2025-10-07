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
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchaseHistoryRecord

interface BillingClientAdapter {
    val ready: Boolean

    suspend fun connect(
        purchasesListener: (PurchasesUpdateResult) -> Unit,
        disconnectionListener: () -> Unit,
    ): BillingInitResult

    suspend fun getSubscriptions(productIds: List<String>): SubscriptionsResult

    @Deprecated(
        message = "purchaseHistory API is deprecated and removed in the Billing Library v8",
        replaceWith = ReplaceWith("queryPurchases"),
    )
    suspend fun getSubscriptionsPurchaseHistory(): SubscriptionsPurchaseHistoryResult

    suspend fun queryPurchases(): QueryPurchasesResult

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
        replacementMode: SubscriptionReplacementMode,
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

sealed class QueryPurchasesResult {
    data class Success(val purchases: List<Purchase>) : QueryPurchasesResult()
    data class Failure(
        val billingError: BillingError? = null,
        val debugMessage: String? = null
    ) : QueryPurchasesResult()
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
 * Currently, we only use the [WITHOUT_PRORATION] mode in our implementation.
 */
enum class SubscriptionReplacementMode(val value: Int) {
    /**
     * The new plan takes effect immediately.
     * The billing cycle remains the same, and the user is charged a prorated amount for the remaining period.
     */
    CHARGE_PRORATED_PRICE(2),

    /**
     * The new plan takes effect immediately.
     * The new price will be charged on the next recurrence time, and the billing cycle stays the same.
     */
    WITHOUT_PRORATION(3),

    /**
     * The new plan takes effect immediately.
     * The user is charged the full price of the new plan and is given a full billing cycle of subscription,
     * plus remaining prorated time from the old plan.
     */
    CHARGE_FULL_PRICE(5),

    /**
     * New subscription starts after current subscription expires.
     * Best for: When you want to avoid billing complications or user requested delayed switch.
     */
    DEFERRED(6),
}
