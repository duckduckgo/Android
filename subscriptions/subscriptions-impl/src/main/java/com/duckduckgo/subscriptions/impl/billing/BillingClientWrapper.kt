/*
 * Copyright (c) 2023 DuckDuckGo
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
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.ProductDetailsResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchaseHistoryRecord
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryProductDetailsParams.Product
import com.android.billingclient.api.QueryPurchaseHistoryParams
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchaseHistory
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.LIST_OF_PRODUCTS
import com.duckduckgo.subscriptions.impl.billing.PurchaseState.Canceled
import com.duckduckgo.subscriptions.impl.billing.PurchaseState.InProgress
import com.duckduckgo.subscriptions.impl.billing.PurchaseState.Purchased
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixelSender
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.logcat

interface BillingClientWrapper {
    val products: Map<String, ProductDetails>
    val purchaseHistory: List<PurchaseHistoryRecord>
    val purchaseState: Flow<PurchaseState>

    suspend fun launchBillingFlow(
        activity: Activity,
        productDetails: ProductDetails,
        offerToken: String,
        externalId: String,
    )
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = BillingClientWrapper::class)
@ContributesMultibinding(scope = AppScope::class, boundType = MainProcessLifecycleObserver::class)
class RealBillingClientWrapper @Inject constructor(
    private val context: Context,
    val dispatcherProvider: DispatcherProvider,
    @AppCoroutineScope val coroutineScope: CoroutineScope,
    private val pixelSender: SubscriptionPixelSender,
) : BillingClientWrapper, MainProcessLifecycleObserver {

    private var billingFlowInProgress = false

    // PurchaseState
    private val _purchaseState = MutableSharedFlow<PurchaseState>()
    override val purchaseState = _purchaseState.asSharedFlow()

    // New Subscription ProductDetails
    override val products = mutableMapOf<String, ProductDetails>()

    // Purchase History
    override val purchaseHistory = mutableListOf<PurchaseHistoryRecord>()
    private val purchasesUpdatedListener =
        PurchasesUpdatedListener { billingResult, purchases ->
            if (billingResult.responseCode == BillingResponseCode.OK && !purchases.isNullOrEmpty()) {
                coroutineScope.launch(dispatcherProvider.io()) {
                    processPurchases(purchases)
                }
            } else if (billingResult.responseCode == BillingResponseCode.USER_CANCELED) {
                coroutineScope.launch(dispatcherProvider.io()) {
                    _purchaseState.emit(Canceled)
                }
                // Handle an error caused by a user cancelling the purchase flow.
            } else {
                pixelSender.reportPurchaseFailureStore()
                coroutineScope.launch(dispatcherProvider.io()) {
                    _purchaseState.emit(Canceled)
                }
            }
            billingFlowInProgress = false
        }

    private lateinit var billingClient: BillingClient

    override fun onCreate(owner: LifecycleOwner) {
        billingClient = BillingClient.newBuilder(context)
            .enablePendingPurchases()
            .setListener(purchasesUpdatedListener)
            .build()

        if (!billingClient.isReady) {
            connect()
        }
    }

    override fun onResume(owner: LifecycleOwner) {
        // Will call on resume coming back from a purchase flow
        if (!billingFlowInProgress) {
            if (billingClient.isReady) {
                owner.lifecycleScope.launch(dispatcherProvider.io()) {
                    getSubscriptions()
                    queryPurchaseHistory()
                }
            }
        }
    }

    override suspend fun launchBillingFlow(
        activity: Activity,
        productDetails: ProductDetails,
        offerToken: String,
        externalId: String,
    ) {
        if (!billingClient.isReady) {
            logcat { "Service not ready" }
        }
        val params = buildBillingFlowParams(productDetails, offerToken, externalId)
        val billingFlow = billingClient.launchBillingFlow(activity, params)
        if (billingFlow.responseCode == BillingResponseCode.OK) {
            _purchaseState.emit(InProgress)
            billingFlowInProgress = true
        } else {
            _purchaseState.emit(Canceled)
        }
    }

    private suspend fun processPurchases(purchases: List<Purchase>) {
        for (purchase in purchases) {
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                _purchaseState.emit(
                    Purchased(
                        purchaseToken = purchase.purchaseToken,
                        packageName = purchase.packageName,
                    ),
                )
            }
        }
    }

    private fun connect() {
        billingClient.startConnection(
            object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    val responseCode = billingResult.responseCode
                    if (responseCode == BillingResponseCode.OK) {
                        coroutineScope.launch(dispatcherProvider.io()) {
                            queryPurchaseHistory()
                            getSubscriptions()
                        }
                    } else {
                        logcat { "Service error" }
                    }
                }
                override fun onBillingServiceDisconnected() {
                    // TODO: Try reconnecting again
                    logcat { "Service disconnected" }
                }
            },
        )
    }

    private suspend fun getSubscriptions() {
        val productList = mutableListOf<Product>()
        val params = QueryProductDetailsParams.newBuilder()

        for (product in LIST_OF_PRODUCTS) {
            productList.add(
                Product.newBuilder()
                    .setProductId(product)
                    .setProductType(ProductType.SUBS)
                    .build(),
            )
        }

        params.setProductList(productList).let { productDetailsParams ->
            val productDetailsResult = withContext(dispatcherProvider.io()) {
                billingClient.queryProductDetails(productDetailsParams.build())
            }
            processProducts(productDetailsResult)
        }
    }

    private fun processProducts(productDetailsResult: ProductDetailsResult) {
        val responseCode = productDetailsResult.billingResult.responseCode
        val debugMessage = productDetailsResult.billingResult.debugMessage
        val productDetailsList = productDetailsResult.productDetailsList.orEmpty()
        when (responseCode) {
            BillingResponseCode.OK -> {
                var newMap = emptyMap<String, ProductDetails>()
                if (productDetailsList.isEmpty()) {
                    logcat { "No products found" }
                } else {
                    newMap = productDetailsList.associateBy {
                        it.productId
                    }
                }
                products.clear()
                newMap.toMap(products)
            }
            else -> {
                logcat { "onProductDetailsResponse: $responseCode $debugMessage" }
            }
        }
    }

    private suspend fun queryPurchaseHistory() {
        if (!billingClient.isReady) {
            // Handle client not ready
            return
        }
        val (billingResult, purchaseList) = billingClient.queryPurchaseHistory(
            QueryPurchaseHistoryParams.newBuilder().setProductType(ProductType.SUBS).build(),
        )
        if (billingResult.responseCode == BillingResponseCode.OK) {
            if (purchaseList?.isNotEmpty() == true) {
                purchaseHistory.clear()
                purchaseHistory.addAll(purchaseList)
            } else {
                purchaseHistory.clear()
            }
        }
    }

    private fun buildBillingFlowParams(
        productDetails: ProductDetails,
        offerToken: String,
        externalId: String,
    ): BillingFlowParams {
        return BillingFlowParams.newBuilder()
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
    }
}

sealed class PurchaseState {
    object InProgress : PurchaseState()
    data class Purchased(
        val purchaseToken: String,
        val packageName: String,
    ) : PurchaseState()

    object Canceled : PurchaseState()
}
