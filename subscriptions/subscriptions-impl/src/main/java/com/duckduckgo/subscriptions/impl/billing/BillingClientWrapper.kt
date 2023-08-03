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
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.ProductDetailsResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.Purchase.PurchaseState
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryProductDetailsParams.Product
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.logcat

interface BillingClientWrapper {
    val products: StateFlow<Map<String, ProductDetails>>
    val purchases: Flow<List<Purchase>>
    fun launchBillingFlow(activity: Activity, params: BillingFlowParams)
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = BillingClientWrapper::class)
@ContributesMultibinding(scope = AppScope::class, boundType = MainProcessLifecycleObserver::class)
class RealBillingClientWrapper @Inject constructor(
    private val context: Context,
    val dispatcherProvider: DispatcherProvider,
    @AppCoroutineScope val coroutineScope: CoroutineScope,
) : BillingClientWrapper, MainProcessLifecycleObserver {

    private var billingFlowInProcess = false

    // New Subscription ProductDetails
    private val _products = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    override val products = _products.asStateFlow()

    // Current Purchases
    private val _purchases = MutableStateFlow<List<Purchase>>(listOf())
    override val purchases = _purchases.asStateFlow()

    private val purchasesUpdatedListener =
        PurchasesUpdatedListener { billingResult, purchases ->
            if (billingResult.responseCode == BillingResponseCode.OK && !purchases.isNullOrEmpty()) {
                processPurchases(purchases)
            } else if (billingResult.responseCode == BillingResponseCode.USER_CANCELED) {
                // Handle an error caused by a user cancelling the purchase flow.
            } else {
                // Handle any other error codes.
            }
            billingFlowInProcess = false
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
        if (!billingFlowInProcess) {
            if (billingClient.isReady) {
                owner.lifecycleScope.launch(dispatcherProvider.io()) {
                    getSubscriptions()
                    queryPurchases()
                }
            }
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        if (billingClient.isReady) {
            billingClient.endConnection()
        }
    }

    override fun launchBillingFlow(activity: Activity, params: BillingFlowParams) {
        if (!billingClient.isReady) {
            logcat { "Service not ready" }
        }
        val billingFlow = billingClient.launchBillingFlow(activity, params)
        if (billingFlow.responseCode == BillingResponseCode.OK) {
            billingFlowInProcess = true
        } else {
            // Handle billing failure
        }
    }

    private fun processPurchases(purchases: List<Purchase>) {
        // Post new purchase List to _purchases
        _purchases.value = purchases

        // Then, handle the purchases
        for (purchase in purchases) {
            if (purchase.purchaseState == PurchaseState.PURCHASED) {
                // ToDo check with BE
                acknowledgePurchase(purchase)
            }
        }
    }
    private fun acknowledgePurchase(purchase: Purchase?) {
        purchase?.let {
            if (!it.isAcknowledged) {
                val params = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(it.purchaseToken)
                    .build()

                billingClient.acknowledgePurchase(
                    params,
                ) { billingResult ->
                    if (billingResult.responseCode == BillingResponseCode.OK && it.purchaseState == PurchaseState.PURCHASED) {
                        // Handle success
                    }
                }
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
                            queryPurchases()
                            getSubscriptions()
                        }
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
                _products.value = newMap
            }
            else -> {
                logcat { "onProductDetailsResponse: $responseCode $debugMessage" }
            }
        }
    }

    private fun queryPurchases() {
        if (!billingClient.isReady) {
            // Handle client not ready
            return
        }
        // Query for existing subscription products that have been purchased.
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(ProductType.SUBS).build(),
        ) { billingResult, purchaseList ->
            if (billingResult.responseCode == BillingResponseCode.OK) {
                if (purchaseList.isNotEmpty()) {
                    _purchases.value = purchaseList.filter { it.purchaseState == PurchaseState.PURCHASED }
                } else {
                    _purchases.value = emptyList()
                }
            } else {
                // Handle error codes
            }
        }
    }

    companion object {
        // List of subscriptions
        const val BASIC_SUBSCRIPTION = "bundle_1"
        private val LIST_OF_PRODUCTS = listOf(BASIC_SUBSCRIPTION)

        // List of plans
        const val YEARLY_PLAN = "test-bundle-1-plan"
        const val MONTHLY_PLAN = "test-bundle-2-plan"
        const val UK_PLAN = "test-bundle-uk-plan"
        const val NETHERLANDS_PLAN = "test-bundle-ne-plan"
    }
}
