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

package com.duckduckgo.subscriptions.impl

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.ProductDetails
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.autofill.api.email.EmailManager
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.subscriptions.impl.SubscriptionStatus.AutoRenewable
import com.duckduckgo.subscriptions.impl.SubscriptionStatus.Expired
import com.duckduckgo.subscriptions.impl.SubscriptionStatus.GracePeriod
import com.duckduckgo.subscriptions.impl.SubscriptionStatus.Inactive
import com.duckduckgo.subscriptions.impl.SubscriptionStatus.NotAutoRenewable
import com.duckduckgo.subscriptions.impl.SubscriptionStatus.Unknown
import com.duckduckgo.subscriptions.impl.SubscriptionsData.*
import com.duckduckgo.subscriptions.impl.billing.BillingClientWrapper
import com.duckduckgo.subscriptions.impl.billing.PurchaseState
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixelSender
import com.duckduckgo.subscriptions.impl.repository.AuthRepository
import com.duckduckgo.subscriptions.impl.services.AuthService
import com.duckduckgo.subscriptions.impl.services.ConfirmationBody
import com.duckduckgo.subscriptions.impl.services.CreateAccountResponse
import com.duckduckgo.subscriptions.impl.services.Entitlement
import com.duckduckgo.subscriptions.impl.services.ResponseError
import com.duckduckgo.subscriptions.impl.services.StoreLoginBody
import com.duckduckgo.subscriptions.impl.services.SubscriptionsService
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import com.squareup.moshi.Moshi
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.logcat
import retrofit2.HttpException

interface SubscriptionsManager {

    /**
     * Launches the purchase flow for a given product details and token
     */
    suspend fun purchase(
        activity: Activity,
        productDetails: ProductDetails,
        offerToken: String,
        isReset: Boolean = false,
    )

    /**
     * Recovers a subscription from the store
     */
    suspend fun recoverSubscriptionFromStore(): SubscriptionsData

    /**
     * Gets the subscription data for an authenticated user
     */
    suspend fun getSubscriptionData(): SubscriptionsData

    /**
     * Gets the subscription for an authenticated user
     */
    suspend fun getSubscription(): Subscription

    /**
     * Authenticates the user based on the auth token
     */
    suspend fun authenticate(authToken: String): SubscriptionsData

    /**
     * Returns the auth token and if expired, tries to refresh irt
     */
    suspend fun getAuthToken(): AuthToken

    /**
     * Returns the access token
     */
    suspend fun getAccessToken(): AccessToken

    /**
     * Returns [true] if the user has an active subscription and [false] otherwise
     */
    suspend fun hasSubscription(): Boolean

    /**
     * Flow to know if a user is signed in or not
     */
    val isSignedIn: Flow<Boolean>

    /**
     * Flow to know if a user has a subscription or not
     */
    val hasSubscription: Flow<Boolean>

    /**
     * Flow to know the state of the current purchase
     */
    val currentPurchaseState: Flow<CurrentPurchase>

    /**
     * Signs the user out
     */
    suspend fun signOut()

    /**
     * Deletes the current account
     */
    suspend fun deleteAccount(): Boolean

    /**
     * Returns a [String] with the URL of the portal or null otherwise
     */
    suspend fun getPortalUrl(): String?
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealSubscriptionsManager @Inject constructor(
    private val authService: AuthService,
    private val subscriptionsService: SubscriptionsService,
    private val authRepository: AuthRepository,
    private val billingClientWrapper: BillingClientWrapper,
    private val emailManager: EmailManager,
    private val context: Context,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val pixelSender: SubscriptionPixelSender,
) : SubscriptionsManager {

    private val adapter = Moshi.Builder().build().adapter(ResponseError::class.java)

    private val _currentPurchaseState = MutableSharedFlow<CurrentPurchase>()
    override val currentPurchaseState = _currentPurchaseState.asSharedFlow().onSubscription { emitCurrentPurchaseValues() }

    private val _isSignedIn = MutableStateFlow(isUserAuthenticated())
    override val isSignedIn = _isSignedIn.asStateFlow()

    private val _hasSubscription = MutableStateFlow(false)
    override val hasSubscription = _hasSubscription.asStateFlow().onSubscription { emitHasSubscriptionsValues() }

    private var purchaseStateJob: Job? = null
    private fun isUserAuthenticated(): Boolean = authRepository.isUserAuthenticated()

    private suspend fun emitHasSubscriptionsValues() {
        coroutineScope.launch(dispatcherProvider.io()) {
            _hasSubscription.emit(hasSubscription())
        }
    }

    private suspend fun emitCurrentPurchaseValues() {
        purchaseStateJob?.cancel()
        purchaseStateJob = coroutineScope.launch(dispatcherProvider.io()) {
            billingClientWrapper.purchaseState.collect {
                when (it) {
                    is PurchaseState.Purchased -> checkPurchase(it.packageName, it.purchaseToken)
                    else -> {
                        // NOOP
                    }
                }
            }
        }
    }

    override suspend fun deleteAccount(): Boolean {
        return try {
            val state = authService.delete("Bearer ${authRepository.tokens().authToken}")
            (state.status == "deleted")
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun getPortalUrl(): String? {
        return try {
            if (isUserAuthenticated()) {
                return subscriptionsService.portal("Bearer ${authRepository.tokens().accessToken}").customerPortalUrl
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getSubscription(): Subscription {
        return try {
            if (isUserAuthenticated()) {
                val response = subscriptionsService.subscription("Bearer ${authRepository.tokens().accessToken}")
                val state = when (response.status) {
                    "Auto-Renewable" -> AutoRenewable
                    "Not Auto-Renewable" -> NotAutoRenewable
                    "Grace Period" -> GracePeriod
                    "Inactive" -> Inactive
                    "Expired" -> Expired
                    else -> Unknown
                }
                authRepository.saveSubscriptionData(response.platform, response.expiresOrRenewsAt)
                return Subscription.Success(
                    productId = response.productId,
                    startedAt = response.startedAt,
                    expiresOrRenewsAt = response.expiresOrRenewsAt,
                    status = state,
                    platform = response.platform,
                )
            } else {
                Subscription.Failure("Subscription not found")
            }
        } catch (e: HttpException) {
            val error = parseError(e)?.error ?: "An error happened"
            Subscription.Failure(error)
        } catch (e: Exception) {
            Subscription.Failure(e.message ?: "An error happened")
        }
    }

    override suspend fun signOut() {
        authRepository.signOut()
        _isSignedIn.emit(false)
        _hasSubscription.emit(false)
    }

    private suspend fun checkPurchase(
        packageName: String,
        purchaseToken: String,
    ) {
        var retryCompleted = false

        suspend fun retry(
            times: Int = 3,
            initialDelay: Long = 500, // .5 seconds
            maxDelay: Long = 1_500, // 1.5 seconds
            factor: Double = 2.0,
            block: suspend () -> Unit,
        ) {
            var currentDelay = initialDelay
            repeat(times) {
                try {
                    if (!retryCompleted) {
                        block()
                    } else {
                        return@retry
                    }
                } catch (t: Throwable) {
                    logcat { "Subs: error in confirmation retry" }
                }
                delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
            }
        }

        _currentPurchaseState.emit(CurrentPurchase.InProgress)

        retry {
            retryCompleted = attemptConfirmPurchase(packageName, purchaseToken)
            logcat { "Subs: retry success: $retryCompleted" }
        }

        if (!retryCompleted) {
            handlePurchaseFailed()
        }
    }

    private suspend fun attemptConfirmPurchase(
        packageName: String,
        purchaseToken: String,
    ): Boolean {
        return try {
            subscriptionsService.confirm(
                "Bearer ${authRepository.tokens().accessToken}",
                ConfirmationBody(
                    packageName = packageName,
                    purchaseToken = purchaseToken,
                ),
            ).also {
                val status = it.subscription.status
                if (status == "Auto-Renewable" || status == "Not Auto-Renewable" || status == "Grace Period") {
                    pixelSender.reportPurchaseSuccess()
                    pixelSender.reportSubscriptionActivated()
                    _currentPurchaseState.emit(CurrentPurchase.Success)
                } else {
                    handlePurchaseFailed()
                }
            }

            _hasSubscription.emit(true)
            true
        } catch (e: Exception) {
            logcat { "Subs: failed to confirm purchase $e" }
            false
        }
    }

    private suspend fun handlePurchaseFailed() {
        pixelSender.reportPurchaseFailureBackend()
        _currentPurchaseState.emit(CurrentPurchase.Failure("An error happened, try again"))
        _hasSubscription.emit(false)
    }

    override suspend fun hasSubscription(): Boolean {
        return when (val result = getSubscriptionData()) {
            is Success -> {
                val isSubscribed = result.entitlements.isNotEmpty()
                _hasSubscription.emit(isSubscribed)
                isSubscribed
            }

            is Failure -> {
                false
            }
        }
    }

    override suspend fun authenticate(authToken: String): SubscriptionsData {
        return try {
            val response = authService.accessToken("Bearer $authToken")
            val subscriptionData = getSubscriptionDataFromToken(response.accessToken)
            if (subscriptionData is Success) {
                authRepository.authenticate(authToken, response.accessToken, subscriptionData.externalId, subscriptionData.email)
            }
            _isSignedIn.emit(isUserAuthenticated())
            _hasSubscription.emit(hasSubscription())
            return subscriptionData
        } catch (e: HttpException) {
            val error = parseError(e)?.error ?: "An error happened"
            Failure(error)
        } catch (e: Exception) {
            Failure(e.message ?: "An error happened")
        }
    }

    override suspend fun recoverSubscriptionFromStore(): SubscriptionsData {
        return try {
            val purchase = billingClientWrapper.purchaseHistory.lastOrNull()
            return if (purchase != null) {
                val signature = purchase.signature
                val body = purchase.originalJson
                val storeLoginBody = StoreLoginBody(signature = signature, signedData = body, packageName = context.packageName)
                val response = authService.storeLogin(storeLoginBody)
                logcat(LogPriority.DEBUG) { "Subs: store login succeeded" }
                val subscriptionsData = authenticate(response.authToken)
                if (subscriptionsData is Success && subscriptionsData.entitlements.isNotEmpty()) {
                    pixelSender.reportSubscriptionActivated()
                }
                subscriptionsData
            } else {
                Failure(SUBSCRIPTION_NOT_FOUND_ERROR)
            }
        } catch (e: HttpException) {
            val error = parseError(e)?.error ?: "An error happened"
            Failure(error)
        } catch (e: Exception) {
            Failure(e.message ?: "An error happened")
        }
    }

    override suspend fun getSubscriptionData(): SubscriptionsData {
        return try {
            if (isUserAuthenticated()) {
                getSubscriptionDataFromToken(authRepository.tokens().accessToken!!)
            } else {
                Success(email = null, externalId = "", entitlements = emptyList())
            }
        } catch (e: HttpException) {
            val error = parseError(e)?.error ?: "An error happened"
            Failure(error)
        } catch (e: Exception) {
            Failure(e.message ?: "An error happened")
        }
    }

    override suspend fun purchase(
        activity: Activity,
        productDetails: ProductDetails,
        offerToken: String,
        isReset: Boolean,
    ) {
        _currentPurchaseState.emit(CurrentPurchase.PreFlowInProgress)
        when (val response = prePurchaseFlow()) {
            is Success -> {
                if (response.entitlements.isEmpty()) {
                    logcat(LogPriority.DEBUG) { "Subs: external id is ${response.externalId}" }
                    _currentPurchaseState.emit(CurrentPurchase.PreFlowFinished)
                    withContext(dispatcherProvider.main()) {
                        billingClientWrapper.launchBillingFlow(
                            activity = activity,
                            productDetails = productDetails,
                            offerToken = offerToken,
                            externalId = response.externalId,
                        )
                    }
                } else {
                    pixelSender.reportRestoreAfterPurchaseAttemptSuccess()
                    _currentPurchaseState.emit(CurrentPurchase.Recovered)
                }
            }

            is Failure -> {
                logcat(LogPriority.ERROR) { "Subs: ${response.message}" }
                pixelSender.reportPurchaseFailureOther()
                _currentPurchaseState.emit(CurrentPurchase.Failure(response.message))
            }
        }
    }

    private suspend fun prePurchaseFlow(): SubscriptionsData {
        return try {
            val subscriptionData = if (isUserAuthenticated()) {
                getSubscriptionDataFromToken(authRepository.tokens().accessToken!!)
                    .also { subscriptionsData ->
                        if (subscriptionsData is Success && subscriptionsData.entitlements.isNotEmpty()) {
                            pixelSender.reportSubscriptionActivated()
                        }
                    }
            } else {
                recoverSubscriptionFromStore()
            }
            return if (subscriptionData is Success) {
                subscriptionData
            } else {
                val newAccount = createAccount()
                logcat(LogPriority.DEBUG) { "Subs: account created ${newAccount.externalId}" }
                authenticate(newAccount.authToken)
            }
        } catch (e: HttpException) {
            val error = parseError(e)?.error ?: "An error happened"
            Failure(error)
        } catch (e: Exception) {
            Failure(e.message ?: "An error happened")
        }
    }

    override suspend fun getAuthToken(): AuthToken {
        return if (isUserAuthenticated()) {
            logcat { "Subs auth token is ${authRepository.tokens().authToken}" }
            when (val response = getSubscriptionDataFromToken(authRepository.tokens().authToken!!)) {
                is Success -> {
                    return if (response.entitlements.isEmpty()) {
                        AuthToken.Failure("")
                    } else {
                        AuthToken.Success(authRepository.tokens().authToken!!)
                    }
                }

                is Failure -> {
                    when (response.message) {
                        "expired_token" -> {
                            logcat(LogPriority.DEBUG) { "Subs: auth token expired" }
                            val subscriptionsData = recoverSubscriptionFromStore()
                            if (subscriptionsData is Success) {
                                return if (subscriptionsData.entitlements.isEmpty()) {
                                    AuthToken.Failure("")
                                } else {
                                    AuthToken.Success(authRepository.tokens().authToken!!)
                                }
                            } else {
                                AuthToken.Failure(response.message)
                            }
                        }

                        else -> {
                            AuthToken.Failure(response.message)
                        }
                    }
                }
            }
        } else {
            AuthToken.Failure("")
        }
    }

    override suspend fun getAccessToken(): AccessToken {
        return withContext(dispatcherProvider.io()) {
            if (isUserAuthenticated()) {
                AccessToken.Success(authRepository.tokens().accessToken!!)
            } else {
                AccessToken.Failure("Token not found")
            }
        }
    }

    private suspend fun getSubscriptionDataFromToken(token: String): SubscriptionsData {
        return try {
            val response = authService.validateToken("Bearer $token")
            response.account.let {
                Success(email = it.email, externalId = it.externalId, entitlements = it.entitlements)
            }
        } catch (e: HttpException) {
            val error = parseError(e)
            Failure(error?.error ?: "An error happened")
        } catch (e: Exception) {
            Failure(e.message ?: "An error happened")
        }
    }

    private suspend fun createAccount(): CreateAccountResponse {
        try {
            val account = authService.createAccount("Bearer ${emailManager.getToken()}")
            if (account.authToken.isEmpty()) {
                pixelSender.reportPurchaseFailureAccountCreation()
            }
            return account
        } catch (e: Exception) {
            when (e) {
                is JsonDataException, is JsonEncodingException, is HttpException -> {
                    pixelSender.reportPurchaseFailureAccountCreation()
                }
            }
            throw e
        }
    }

    private fun parseError(e: HttpException): ResponseError? {
        return try {
            val error = adapter.fromJson(e.response()?.errorBody()?.string().orEmpty())
            error
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        const val SUBSCRIPTION_NOT_FOUND_ERROR = "SubscriptionNotFound"
    }
}

sealed class AccessToken {
    data class Success(val accessToken: String) : AccessToken()
    data class Failure(val message: String) : AccessToken()
}

sealed class AuthToken {
    data class Success(val authToken: String) : AuthToken()
    data class Failure(val message: String) : AuthToken()
}

sealed class SubscriptionsData {
    data class Success(
        val email: String?,
        val externalId: String,
        val entitlements: List<Entitlement>,
    ) : SubscriptionsData()

    data class Failure(val message: String) : SubscriptionsData()
}

sealed class Subscription {
    data class Success(
        val productId: String,
        val startedAt: Long,
        val expiresOrRenewsAt: Long,
        val status: SubscriptionStatus,
        val platform: String,
    ) : Subscription()

    data class Failure(val message: String) : Subscription()
}

sealed class SubscriptionStatus {
    data object AutoRenewable : SubscriptionStatus()
    data object NotAutoRenewable : SubscriptionStatus()
    data object GracePeriod : SubscriptionStatus()
    data object Inactive : SubscriptionStatus()
    data object Expired : SubscriptionStatus()
    data object Unknown : SubscriptionStatus()
}

sealed class CurrentPurchase {
    data object PreFlowInProgress : CurrentPurchase()
    data object PreFlowFinished : CurrentPurchase()
    data object InProgress : CurrentPurchase()
    data object Success : CurrentPurchase()
    data object Recovered : CurrentPurchase()
    data class Failure(val message: String) : CurrentPurchase()
}
