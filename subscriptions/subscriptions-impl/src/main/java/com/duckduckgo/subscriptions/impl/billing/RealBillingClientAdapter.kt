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
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.Purchase.PurchaseState
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryProductDetailsParams.Product
import com.android.billingclient.api.QueryPurchaseHistoryParams
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchaseHistory
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.subscriptions.impl.billing.BillingInitResult.Failure
import com.duckduckgo.subscriptions.impl.billing.BillingInitResult.Success
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealBillingClientAdapter @Inject constructor(
    private val context: Context,
    private val coroutineDispatchers: DispatcherProvider,
) : BillingClientAdapter {

    private var billingClient: BillingClient? = null

    override val ready: Boolean
        get() = billingClient?.isReady == true

    override suspend fun connect(
        purchasesListener: (PurchasesUpdateResult) -> Unit,
        disconnectionListener: () -> Unit,
    ): BillingInitResult {
        reset()

        billingClient = BillingClient.newBuilder(context)
            .enablePendingPurchases()
            .setListener { billingResult, purchases ->
                val purchasesUpdateResult = mapToPurchasesUpdateResult(billingResult, purchases)
                purchasesListener.invoke(purchasesUpdateResult)
            }
            .build()

        return suspendCancellableCoroutine { continuation ->
            billingClient?.startConnection(
                object : BillingClientStateListener {
                    override fun onBillingServiceDisconnected() {
                        disconnectionListener.invoke()
                    }

                    override fun onBillingSetupFinished(p0: BillingResult) {
                        val result = when (p0.responseCode) {
                            BillingResponseCode.OK -> Success
                            else -> Failure
                        }

                        continuation.resume(result, onCancellation = null)
                    }
                },
            )
        }
    }

    override suspend fun getSubscriptions(productIds: List<String>): SubscriptionsResult {
        val client = billingClient
        if (client == null || !client.isReady) return SubscriptionsResult.Failure()

        val queryParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                productIds.map { productId ->
                    Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(ProductType.SUBS)
                        .build()
                },
            )
            .build()

        val (billingResult, productDetails) = client.queryProductDetails(queryParams)

        return when (billingResult.responseCode) {
            BillingResponseCode.OK -> SubscriptionsResult.Success(productDetails.orEmpty())
            else -> SubscriptionsResult.Failure(billingResult.responseCode, billingResult.debugMessage)
        }
    }

    override suspend fun getSubscriptionsPurchaseHistory(): SubscriptionsPurchaseHistoryResult {
        val client = billingClient
        if (client == null || !client.isReady) return SubscriptionsPurchaseHistoryResult.Failure

        val queryParams = QueryPurchaseHistoryParams.newBuilder()
            .setProductType(ProductType.SUBS)
            .build()

        val (billingResult, purchaseHistory) = client.queryPurchaseHistory(queryParams)

        return when (billingResult.responseCode) {
            BillingResponseCode.OK -> SubscriptionsPurchaseHistoryResult.Success(history = purchaseHistory.orEmpty())
            else -> SubscriptionsPurchaseHistoryResult.Failure
        }
    }

    override suspend fun launchBillingFlow(
        activity: Activity,
        productDetails: ProductDetails,
        offerToken: String,
        externalId: String,
    ): LaunchBillingFlowResult {
        val client = billingClient
        if (client == null || !client.isReady) return LaunchBillingFlowResult.Failure

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .setOfferToken(offerToken)
                        .build(),
                ),
            )
            .setObfuscatedAccountId(externalId)
            .setObfuscatedProfileId(externalId)
            .build()

        val result = withContext(coroutineDispatchers.main()) {
            client.launchBillingFlow(activity, billingFlowParams)
        }

        return when (result.responseCode) {
            BillingResponseCode.OK -> LaunchBillingFlowResult.Success
            else -> LaunchBillingFlowResult.Failure
        }
    }

    private fun reset() {
        billingClient?.endConnection()
        billingClient = null
    }

    private fun mapToPurchasesUpdateResult(
        billingResult: BillingResult,
        purchases: List<Purchase>?,
    ): PurchasesUpdateResult =
        when (billingResult.responseCode) {
            BillingResponseCode.OK -> {
                val purchase = purchases?.lastOrNull { it.purchaseState == PurchaseState.PURCHASED }
                if (purchase != null) {
                    PurchasesUpdateResult.PurchasePresent(
                        purchaseToken = purchase.purchaseToken,
                        packageName = purchase.packageName,
                    )
                } else {
                    PurchasesUpdateResult.PurchaseAbsent
                }
            }

            BillingResponseCode.USER_CANCELED -> PurchasesUpdateResult.UserCancelled
            else -> PurchasesUpdateResult.Failure
        }
}
