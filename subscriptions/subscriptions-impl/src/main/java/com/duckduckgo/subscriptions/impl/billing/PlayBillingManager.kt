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
import com.android.billingclient.api.Purchase
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.di.IsMainProcess
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.subscriptions.impl.SubscriptionTier
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.ADVANCED_SUBSCRIPTION
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.BASIC_SUBSCRIPTION
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.LIST_OF_PRODUCTS
import com.duckduckgo.subscriptions.impl.SubscriptionsFeature
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
import com.duckduckgo.subscriptions.impl.wideevents.BillingFlowInitFailureContext
import com.duckduckgo.subscriptions.impl.wideevents.LastLoadProductsOutcome
import com.duckduckgo.subscriptions.impl.wideevents.SubscriptionPurchaseWideEvent
import com.duckduckgo.subscriptions.impl.wideevents.SubscriptionSwitchWideEvent
import com.duckduckgo.subscriptions.impl.wideevents.SubscriptionUndoCancellationWideEvent
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import logcat.logcat
import java.util.EnumSet
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

interface PlayBillingManager {
    val products: List<ProductDetails>
    val productsFlow: Flow<List<ProductDetails>>
    val purchases: List<Purchase>
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
     * Replaces the current canceled (not expired) plan with the same plan, without proration
     */
    suspend fun launchCancellationUndoFlow(
        activity: Activity,
        planId: String,
        externalId: String,
        oldPurchaseToken: String,
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
        oldPurchaseToken: String,
        replacementMode: SubscriptionReplacementMode,
    )

    /**
     * Gets the current purchase token from active purchases (queryPurchasesAsync)
     * This is the preferred method over purchase history for getting current tokens
     */
    fun getLatestPurchaseToken(): String?

    /**
     * Returns the latest active (PURCHASED) subscription purchase. Queries Play Billing
     * directly on every call so the result reflects current state (no cache staleness).
     * See [LatestPurchaseResult] for the meaning of each outcome, and [BillingConnect] for how the
     * billing client was connected before the query.
     */
    suspend fun getLatestPurchase(): LatestPurchaseLookup
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<PlayBillingManager>())
@ContributesIntoSet(scope = AppScope::class, binding = binding<MainProcessLifecycleObserver>())
class RealPlayBillingManager @Inject constructor(
    @AppCoroutineScope val coroutineScope: CoroutineScope,
    private val pixelSender: SubscriptionPixelSender,
    private val billingClient: BillingClientAdapter,
    private val dispatcherProvider: DispatcherProvider,
    private val subscriptionPurchaseWideEvent: SubscriptionPurchaseWideEvent,
    private val subscriptionSwitchWideEvent: SubscriptionSwitchWideEvent,
    private val subscriptionUndoCancellationWideEvent: SubscriptionUndoCancellationWideEvent,
    private val subscriptionsFeature: Lazy<SubscriptionsFeature>,
    @IsMainProcess private val isMainProcess: Boolean,
) : PlayBillingManager, MainProcessLifecycleObserver {

    private val connectionMutex = Mutex()
    private var connectionJob: Job? = null
    private var billingFlowInProgress = false
    private var currentFlowType = BillingFlowType.PURCHASE

    // PurchaseState
    private val _purchaseState = MutableSharedFlow<PurchaseState>()
    override val purchaseState = _purchaseState.asSharedFlow()

    // New Subscription ProductDetails
    private var _products = MutableStateFlow(emptyList<ProductDetails>())

    private var lastLoadProductsOutcome: LastLoadProductsOutcome = LastLoadProductsOutcome.NeverAttempted

    override val products: List<ProductDetails>
        get() = _products.value

    override val productsFlow: Flow<List<ProductDetails>>
        get() = _products.asStateFlow()

    // Active Purchases
    override var purchases = emptyList<Purchase>()

    override fun onCreate(owner: LifecycleOwner) {
        connectAsyncWithRetry()
    }

    override fun onResume(owner: LifecycleOwner) {
        // Will call on resume coming back from a purchase flow
        if (!billingFlowInProgress) {
            if (billingClient.ready) {
                owner.lifecycleScope.launch(dispatcherProvider.io()) {
                    loadProducts()
                    loadPurchases()
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
                    loadPurchases()
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
    ) = launchPurchaseFlow(activity, planId, externalId, offerId, BillingFlowType.PURCHASE)

    override suspend fun launchCancellationUndoFlow(
        activity: Activity,
        planId: String,
        externalId: String,
        oldPurchaseToken: String,
    ) = launchReplacementFlow(
        activity = activity,
        newPlanId = planId,
        externalId = externalId,
        newOfferId = null,
        oldPurchaseToken = oldPurchaseToken,
        replacementMode = SubscriptionReplacementMode.WITHOUT_PRORATION,
        flowType = BillingFlowType.CANCELLATION_UNDO,
    )

    private suspend fun launchPurchaseFlow(
        activity: Activity,
        planId: String,
        externalId: String,
        offerId: String?,
        flowType: BillingFlowType,
    ) = withContext(dispatcherProvider.io()) {
        currentFlowType = flowType
        if (!billingClient.ready) {
            logcat { "Service not ready" }
            connect()
        }

        val subscriptionProduct = SubscriptionTier.fromPlanId(planId).productId
        val productDetails = products.find { it.productId == subscriptionProduct }

        val offerToken = productDetails
            ?.subscriptionOfferDetails
            ?.find { it.basePlanId == planId && it.offerId == offerId }
            ?.offerToken

        if (productDetails == null || offerToken == null) {
            val error = "Missing product details"
            val reason = when {
                products.isEmpty() -> BillingFlowInitFailureContext.Reason.NO_PRODUCTS_LOADED
                productDetails == null -> BillingFlowInitFailureContext.Reason.PRODUCT_ID_NOT_FOUND
                else -> BillingFlowInitFailureContext.Reason.OFFER_NOT_FOUND
            }
            if (flowType == BillingFlowType.PURCHASE) {
                subscriptionPurchaseWideEvent.onBillingFlowInitFailure(
                    error = error,
                    failureContext = BillingFlowInitFailureContext(
                        reason = reason,
                        requestedProductId = subscriptionProduct,
                        requestedPlanId = planId,
                        requestedOfferId = offerId,
                        loadedProductIds = products.map { it.productId },
                        billingClientReady = billingClient.ready,
                        lastLoadProductsOutcome = lastLoadProductsOutcome,
                    ),
                )
            }
            emitLaunchFailure(error)
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
                if (flowType == BillingFlowType.PURCHASE) {
                    subscriptionPurchaseWideEvent.onBillingFlowInitSuccess()
                }
                _purchaseState.emit(InProgress)
                billingFlowInProgress = true
            }

            is LaunchBillingFlowResult.Failure -> {
                val error = "Billing error: ${launchBillingFlowResult.error.name}"
                if (flowType == BillingFlowType.PURCHASE) {
                    subscriptionPurchaseWideEvent.onBillingFlowInitFailure(error)
                }
                emitLaunchFailure(error)
            }
        }
    }

    override suspend fun launchSubscriptionUpdate(
        activity: Activity,
        newPlanId: String,
        externalId: String,
        newOfferId: String?,
        oldPurchaseToken: String,
        replacementMode: SubscriptionReplacementMode,
    ) = launchReplacementFlow(activity, newPlanId, externalId, newOfferId, oldPurchaseToken, replacementMode, BillingFlowType.PLAN_SWITCH)

    private suspend fun launchReplacementFlow(
        activity: Activity,
        newPlanId: String,
        externalId: String,
        newOfferId: String?,
        oldPurchaseToken: String,
        replacementMode: SubscriptionReplacementMode,
        flowType: BillingFlowType,
    ) = withContext(dispatcherProvider.io()) {
        currentFlowType = flowType
        if (!billingClient.ready) {
            logcat { "Service not ready" }
            connect()
        }

        logcat { "Billing: Using provided old purchase token: ${oldPurchaseToken.take(10)}..." }

        if (oldPurchaseToken.isEmpty()) {
            val errorMessage = "empty old purchase token"
            logcat { "Billing: $errorMessage" }
            reportReplacementFlowInitFailure(flowType, errorMessage)
            emitLaunchFailure(errorMessage)
            return@withContext
        }

        val subscriptionProduct = SubscriptionTier.fromPlanId(newPlanId).productId

        val productDetails = products.find { it.productId == subscriptionProduct }

        val offerToken = productDetails
            ?.subscriptionOfferDetails
            ?.find { it.basePlanId == newPlanId && it.offerId == newOfferId }
            ?.offerToken

        if (productDetails == null || offerToken == null) {
            val errorMessage = "Missing product details"
            logcat { "Billing: $errorMessage" }
            reportReplacementFlowInitFailure(flowType, errorMessage)
            emitLaunchFailure(errorMessage)
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
                reportReplacementFlowInitSuccess(flowType)
                _purchaseState.emit(InProgress)
                billingFlowInProgress = true
            }

            is LaunchBillingFlowResult.Failure -> {
                reportReplacementFlowInitFailure(flowType, launchBillingFlowResult.error.name)
                emitLaunchFailure(launchBillingFlowResult.error.name)
            }
        }
    }

    private suspend fun emitLaunchFailure(error: String) {
        currentFlowType = BillingFlowType.PURCHASE
        _purchaseState.emit(PurchaseState.Failure(error))
    }

    private suspend fun reportReplacementFlowInitSuccess(flowType: BillingFlowType) {
        when (flowType) {
            BillingFlowType.PLAN_SWITCH -> subscriptionSwitchWideEvent.onBillingFlowInitSuccess()
            BillingFlowType.CANCELLATION_UNDO -> subscriptionUndoCancellationWideEvent.onBillingFlowInitSuccess()
            BillingFlowType.PURCHASE -> Unit
        }
    }

    private suspend fun reportReplacementFlowInitFailure(
        flowType: BillingFlowType,
        error: String,
    ) {
        when (flowType) {
            BillingFlowType.PLAN_SWITCH -> subscriptionSwitchWideEvent.onBillingFlowInitFailure(error)
            BillingFlowType.CANCELLATION_UNDO -> subscriptionUndoCancellationWideEvent.onBillingFlowInitFailure(error)
            BillingFlowType.PURCHASE -> Unit
        }
    }

    private fun onPurchasesUpdated(result: PurchasesUpdateResult) {
        val flowType = currentFlowType
        currentFlowType = BillingFlowType.PURCHASE
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
                    if (flowType == BillingFlowType.PURCHASE) {
                        pixelSender.reportPurchaseFailureStore(result.errorType)
                    }
                    _purchaseState.emit(PurchaseState.Failure(result.errorType))
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
                lastLoadProductsOutcome = LastLoadProductsOutcome.Success(result.products.size)
                if (result.products.isEmpty()) {
                    logcat { "No products found" }
                }
                _products.value = result.products
            }

            is SubscriptionsResult.Failure -> {
                lastLoadProductsOutcome = LastLoadProductsOutcome.Failure(result.billingError.toString())
                logcat { "onProductDetailsResponse: ${result.billingError} ${result.debugMessage}" }
            }
        }
    }

    private suspend fun loadPurchases() {
        when (val result = billingClient.queryPurchases()) {
            is QueryPurchasesResult.Success -> {
                purchases = result.purchases
                logcat { "Billing: Loaded ${result.purchases.size} active purchases" }
            }
            is QueryPurchasesResult.Failure -> {
                logcat { "Billing: Failed to load purchases: ${result.billingError} - ${result.debugMessage}" }
            }
        }
    }

    override fun getLatestPurchaseToken(): String? {
        val activePurchases = purchases.filter { purchase: Purchase ->
            (purchase.products.contains(BASIC_SUBSCRIPTION) || purchase.products.contains(ADVANCED_SUBSCRIPTION)) &&
                purchase.purchaseState == Purchase.PurchaseState.PURCHASED
        }

        val latestPurchase = activePurchases.maxByOrNull { it.purchaseTime }

        return if (latestPurchase != null) {
            val tokenPreview = latestPurchase.purchaseToken.take(10) + "..."
            logcat { "Billing: Latest active purchase token preview: $tokenPreview" }
            latestPurchase.purchaseToken
        } else {
            logcat { "Billing: No active purchase token found" }
            null
        }
    }

    override suspend fun getLatestPurchase(): LatestPurchaseLookup = withContext(dispatcherProvider.io()) {
        val flagIsOn = subscriptionsFeature.value.connectBillingBeforeQueryingPurchases().isEnabled()
        if (billingClient.ready) {
            val billingConnect = if (flagIsOn) BillingConnect.ALREADY_CONNECTED else BillingConnect.FLAG_OFF
            return@withContext LatestPurchaseLookup(result = queryLatestPurchase(), billingConnect = billingConnect)
        }
        if (!flagIsOn) {
            return@withContext LatestPurchaseLookup(result = BILLING_CLIENT_NOT_READY, billingConnect = BillingConnect.FLAG_OFF)
        }
        if (!isMainProcess) {
            return@withContext LatestPurchaseLookup(result = BILLING_CLIENT_NOT_READY, billingConnect = null)
        }
        val connectFailureCause = connectWithTimeout()
        if (connectFailureCause != null) {
            return@withContext LatestPurchaseLookup(result = LatestPurchaseResult.Unknown(cause = connectFailureCause), billingConnect = null)
        }
        LatestPurchaseLookup(result = queryLatestPurchase(), billingConnect = BillingConnect.CONNECTED)
    }

    private suspend fun queryLatestPurchase(): LatestPurchaseResult {
        return when (val result = billingClient.queryPurchases()) {
            is QueryPurchasesResult.Success -> {
                val latest = result.purchases
                    .filter { purchase ->
                        (purchase.products.contains(BASIC_SUBSCRIPTION) || purchase.products.contains(ADVANCED_SUBSCRIPTION)) &&
                            purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                    }
                    .maxByOrNull { it.purchaseTime }
                if (latest != null) LatestPurchaseResult.Present(latest) else LatestPurchaseResult.Absent
            }
            is QueryPurchasesResult.Failure -> {
                logcat { "Billing: getLatestPurchase query failed: ${result.billingError} - ${result.debugMessage}" }
                LatestPurchaseResult.Unknown(cause = "query_purchases_failed:${result.billingError?.name ?: "unknown"}")
            }
        }
    }

    private suspend fun connectWithTimeout(): String? {
        val connecting = coroutineScope.async(dispatcherProvider.io()) { connect() }
        val failureCause = try {
            withTimeoutOrNull(timeout = CONNECT_BEFORE_QUERY_TIMEOUT) {
                connecting.await()
                "billing_connect_failed"
            } ?: "billing_connect_timeout"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat { "Billing: connect before query failed: ${e.javaClass.simpleName}" }
            "billing_connect_error"
        }
        return if (billingClient.ready) null else failureCause
    }

    private companion object {
        val CONNECT_BEFORE_QUERY_TIMEOUT = 5.seconds
        val BILLING_CLIENT_NOT_READY = LatestPurchaseResult.Unknown(cause = "billing_client_not_ready")
    }
}

data class LatestPurchaseLookup(
    val result: LatestPurchaseResult,
    /** Null when the client was not connected and the app did not connect it: outside the main process, or the connection failed. */
    val billingConnect: BillingConnect?,
)

/** How the billing client came to be connected for a purchase query, as reported in the auth-token-refresh wide event. */
enum class BillingConnect(val paramValue: String) {
    FLAG_OFF("flag_off"),
    ALREADY_CONNECTED("already_connected"),
    CONNECTED("connected"),
}

sealed class LatestPurchaseResult {
    /** An active purchase exists. */
    data class Present(val purchase: Purchase) : LatestPurchaseResult()

    /** Play Billing has been successfully queried and confirmed no active purchase exists. */
    data object Absent : LatestPurchaseResult()

    /**
     * No confirmed answer yet — either we have not queried Play Billing yet,
     * or the last query failed (connection error, service unavailable, etc.).
     * [cause] is a low-cardinality tag identifying which path produced the Unknown result.
     */
    data class Unknown(val cause: String) : LatestPurchaseResult()
}

sealed class PurchaseState {
    data object InProgress : PurchaseState()
    data class Purchased(
        val purchaseToken: String,
        val packageName: String,
    ) : PurchaseState()

    data object Canceled : PurchaseState()
    data class Failure(val errorType: String) : PurchaseState()
}

enum class BillingFlowType {
    PURCHASE,
    PLAN_SWITCH,
    CANCELLATION_UNDO,
}
