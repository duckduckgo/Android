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
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.BASIC_SUBSCRIPTION
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.LIST_OF_PRODUCTS
import com.duckduckgo.subscriptions.impl.billing.BillingError.ERROR
import com.duckduckgo.subscriptions.impl.billing.BillingError.NETWORK_ERROR
import com.duckduckgo.subscriptions.impl.billing.BillingError.SERVICE_DISCONNECTED
import com.duckduckgo.subscriptions.impl.billing.BillingError.SERVICE_UNAVAILABLE
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import logcat.logcat
import java.util.EnumSet
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

interface PlayBillingManager {
    val products: List<ProductDetails>
    val productsFlow: Flow<List<ProductDetails>>
    val purchaseHistory: List<PurchaseHistoryRecord>
    val purchaseState: Flow<PurchaseState>

    /**
     * Launches the billing flow
     *
     * It is safe to call this method without specifying dispatcher as it's handled internally
     */
    suspend fun launchBillingFlow(
        activity: Activity,
        planId: String,
        externalId: String,
        offerId: String?,
    )

    /**
     * Launches the subscription update flow
     *
     * It is safe to call this method without specifying dispatcher as it's handled internally
     */
    suspend fun launchSubscriptionUpdate(
        activity: Activity,
        newPlanId: String,
        externalId: String,
        newOfferId: String?,
        replacementMode: SubscriptionReplacementMode = SubscriptionReplacementMode.DEFERRED,
    )
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = PlayBillingManager::class)
@ContributesMultibinding(scope = AppScope::class, boundType = MainProcessLifecycleObserver::class)
class RealPlayBillingManager @Inject constructor(
    @AppCoroutineScope val coroutineScope: CoroutineScope,
    private val pixelSender: SubscriptionPixelSender,
    private val billingClient: BillingClientAdapter,
    private val dispatcherProvider: DispatcherProvider,
) : PlayBillingManager, MainProcessLifecycleObserver {

    private val connectionMutex = Mutex()
    private var connectionJob: Job? = null
    private var billingFlowInProgress = false

    // PurchaseState
    private val _purchaseState = MutableSharedFlow<PurchaseState>()
    override val purchaseState = _purchaseState.asSharedFlow()

    // New Subscription ProductDetails
    private var _products = MutableStateFlow(emptyList<ProductDetails>())

    override val products: List<ProductDetails>
        get() = _products.value

    override val productsFlow: Flow<List<ProductDetails>>
        get() = _products.asStateFlow()

    // Purchase History
    override var purchaseHistory = emptyList<PurchaseHistoryRecord>()

    override fun onCreate(owner: LifecycleOwner) {
        connectAsyncWithRetry()
    }

    override fun onResume(owner: LifecycleOwner) {
        // Will call on resume coming back from a purchase flow
        if (!billingFlowInProgress) {
            if (billingClient.ready) {
                owner.lifecycleScope.launch(dispatcherProvider.io()) {
                    loadProducts()
                    loadPurchaseHistory()
                }
            }
        }
    }

    private fun connectAsyncWithRetry() {
        if (connectionJob?.isActive == true) return

        connectionJob = coroutineScope.launch(dispatcherProvider.io()) {
            connect(
                retryPolicy = RetryPolicy(
                    retryCount = 5,
                    initialDelay = 1.seconds,
                    maxDelay = 5.minutes,
                    delayIncrementFactor = 4.0,
                ),
            )
        }
    }

    private suspend fun connect(retryPolicy: RetryPolicy? = null) = retry(retryPolicy) {
        connectionMutex.withLock {
            if (billingClient.ready) return@withLock true

            val result = billingClient.connect(
                purchasesListener = { result -> onPurchasesUpdated(result) },
                disconnectionListener = { onBillingClientDisconnected() },
            )

            when (result) {
                Success -> {
                    loadProducts()
                    loadPurchaseHistory()
                    true // success, don't retry
                }

                is Failure -> {
                    logcat { "Service error" }
                    val recoverable = result.billingError in EnumSet.of(
                        ERROR,
                        SERVICE_DISCONNECTED,
                        SERVICE_UNAVAILABLE,
                        NETWORK_ERROR,
                    )
                    !recoverable // complete without retry if error is not recoverable
                }
            }
        }
    }

    override suspend fun launchBillingFlow(
        activity: Activity,
        planId: String,
        externalId: String,
        offerId: String?,
    ) = withContext(dispatcherProvider.io()) {
        if (!billingClient.ready) {
            logcat { "Service not ready" }
            connect()
        }

        val productDetails = products.find { it.productId == BASIC_SUBSCRIPTION }

        val offerToken = productDetails
            ?.subscriptionOfferDetails
            ?.find { it.basePlanId == planId && it.offerId == offerId }
            ?.offerToken

        if (productDetails == null || offerToken == null) {
            _purchaseState.emit(Canceled)
            return@withContext
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

    override suspend fun launchSubscriptionUpdate(
        activity: Activity,
        newPlanId: String,
        externalId: String,
        newOfferId: String?,
        replacementMode: SubscriptionReplacementMode,
    ) = withContext(dispatcherProvider.io()) {
        if (!billingClient.ready) {
            logcat { "Service not ready" }
            connect()
        }

        val oldPurchaseToken: String? = getCurrentPurchaseToken()

        val productDetails = products.find { it.productId == BASIC_SUBSCRIPTION }

        val offerToken = productDetails
            ?.subscriptionOfferDetails
            ?.find { it.basePlanId == newPlanId && it.offerId == newOfferId }
            ?.offerToken

        if (productDetails == null || offerToken == null || oldPurchaseToken == null) {
            _purchaseState.emit(Canceled)
            return@withContext
        }

        val launchBillingFlowResult = billingClient.launchSubscriptionUpdate(
            activity = activity,
            productDetails = productDetails,
            offerToken = offerToken,
            externalId = externalId,
            oldPurchaseToken = oldPurchaseToken,
            replacementMode = replacementMode,
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

    /**
     * Gets the current purchase token for the active subscription
     */
    private suspend fun getCurrentPurchaseToken(): String? = withContext(dispatcherProvider.io()) {
        return@withContext purchaseHistory
            .filter { it.products.contains(BASIC_SUBSCRIPTION) }
            .maxByOrNull { it.purchaseTime }
            ?.purchaseToken
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

                is PurchasesUpdateResult.Failure -> {
                    pixelSender.reportPurchaseFailureStore(result.errorType)
                    _purchaseState.emit(Canceled)
                }
            }
        }

        billingFlowInProgress = false
    }

    private fun onBillingClientDisconnected() {
        logcat { "Service disconnected" }
        connectAsyncWithRetry()
    }

    private suspend fun loadProducts() {
        when (val result = billingClient.getSubscriptions(LIST_OF_PRODUCTS)) {
            is SubscriptionsResult.Success -> {
                if (result.products.isEmpty()) {
                    logcat { "No products found" }
                }
                _products.value = result.products
            }

            is SubscriptionsResult.Failure -> {
                logcat { "onProductDetailsResponse: ${result.billingError} ${result.debugMessage}" }
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
