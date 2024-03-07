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
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.PurchaseHistoryRecord
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.LIST_OF_PRODUCTS
import com.duckduckgo.subscriptions.impl.billing.BillingInitResult.Failure
import com.duckduckgo.subscriptions.impl.billing.BillingInitResult.Success
import com.duckduckgo.subscriptions.impl.billing.PurchaseState.Canceled
import com.duckduckgo.subscriptions.impl.billing.PurchaseState.InProgress
import com.duckduckgo.subscriptions.impl.billing.PurchaseState.Purchased
import com.duckduckgo.subscriptions.impl.billing.PurchasesUpdateResult.PurchaseAbsent
import com.duckduckgo.subscriptions.impl.billing.PurchasesUpdateResult.PurchasePresent
import com.duckduckgo.subscriptions.impl.billing.PurchasesUpdateResult.UserCancelled
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logcat.logcat

interface PlayBillingManager {
    val products: List<ProductDetails>
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
@ContributesBinding(AppScope::class, boundType = PlayBillingManager::class)
@ContributesMultibinding(scope = AppScope::class, boundType = MainProcessLifecycleObserver::class)
class RealPlayBillingManager @Inject constructor(
    @AppCoroutineScope val coroutineScope: CoroutineScope,
    private val pixelSender: SubscriptionPixelSender,
    private val billingClient: BillingClientAdapter,
) : PlayBillingManager, MainProcessLifecycleObserver {

    private val connectionMutex = Mutex()
    private var billingFlowInProgress = false

    // PurchaseState
    private val _purchaseState = MutableSharedFlow<PurchaseState>()
    override val purchaseState = _purchaseState.asSharedFlow()

    // New Subscription ProductDetails
    override var products = emptyList<ProductDetails>()

    // Purchase History
    override var purchaseHistory = emptyList<PurchaseHistoryRecord>()

    override fun onCreate(owner: LifecycleOwner) {
        coroutineScope.launch { connect() }
    }

    override fun onResume(owner: LifecycleOwner) {
        // Will call on resume coming back from a purchase flow
        if (!billingFlowInProgress) {
            if (billingClient.ready) {
                owner.lifecycleScope.launch {
                    loadProducts()
                    loadPurchaseHistory()
                }
            }
        }
    }

    private suspend fun connect() = connectionMutex.withLock {
        if (billingClient.ready) return@withLock

        val result = billingClient.connect(
            purchasesListener = { result -> onPurchasesUpdated(result) },
            disconnectionListener = { onBillingClientDisconnected() },
        )

        when (result) {
            Success -> {
                loadProducts()
                loadPurchaseHistory()
            }

            Failure -> {
                logcat { "Service error" }
            }
        }
    }

    override suspend fun launchBillingFlow(
        activity: Activity,
        productDetails: ProductDetails,
        offerToken: String,
        externalId: String,
    ) {
        if (!billingClient.ready) {
            logcat { "Service not ready" }
            connect()
        }

        val launchBillingFlowResult = billingClient.launchBillingFlow(
            activity = activity,
            productDetails = productDetails,
            offerToken = offerToken,
            externalId = externalId,
        )

        when (launchBillingFlowResult) {
            LaunchBillingFlowResult.Success -> {
                _purchaseState.emit(InProgress)
                billingFlowInProgress = true
            }

            LaunchBillingFlowResult.Failure -> {
                _purchaseState.emit(Canceled)
            }
        }
    }

    private fun onPurchasesUpdated(result: PurchasesUpdateResult) {
        coroutineScope.launch {
            when (result) {
                is PurchasePresent -> {
                    _purchaseState.emit(
                        Purchased(
                            purchaseToken = result.purchaseToken,
                            packageName = result.packageName,
                        ),
                    )
                }

                PurchaseAbsent -> {}
                UserCancelled -> {
                    _purchaseState.emit(Canceled)
                    // Handle an error caused by a user cancelling the purchase flow.
                }

                PurchasesUpdateResult.Failure -> {
                    pixelSender.reportPurchaseFailureStore()
                    _purchaseState.emit(Canceled)
                }
            }
        }

        billingFlowInProgress = false
    }

    private fun onBillingClientDisconnected() {
        logcat { "Service disconnected" }
        coroutineScope.launch { connect() }
    }

    private suspend fun loadProducts() {
        when (val result = billingClient.getSubscriptions(LIST_OF_PRODUCTS)) {
            is SubscriptionsResult.Success -> {
                if (result.products.isEmpty()) {
                    logcat { "No products found" }
                }
                this.products = result.products
            }

            is SubscriptionsResult.Failure -> {
                logcat { "onProductDetailsResponse: ${result.billingResponseCode} ${result.debugMessage}" }
            }
        }
    }

    private suspend fun loadPurchaseHistory() {
        when (val result = billingClient.getSubscriptionsPurchaseHistory()) {
            is SubscriptionsPurchaseHistoryResult.Success -> {
                purchaseHistory = result.history
            }
            SubscriptionsPurchaseHistoryResult.Failure -> {
            }
        }
    }
}

sealed class PurchaseState {
    data object InProgress : PurchaseState()
    data class Purchased(
        val purchaseToken: String,
        val packageName: String,
    ) : PurchaseState()

    data object Canceled : PurchaseState()
}
