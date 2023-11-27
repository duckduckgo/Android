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
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.subscriptions.impl.SubscriptionsData.*
import com.duckduckgo.subscriptions.impl.auth.AuthService
import com.duckduckgo.subscriptions.impl.auth.CreateAccountResponse
import com.duckduckgo.subscriptions.impl.auth.Entitlement
import com.duckduckgo.subscriptions.impl.auth.ResponseError
import com.duckduckgo.subscriptions.impl.auth.StoreLoginBody
import com.duckduckgo.subscriptions.impl.billing.BillingClientWrapper
import com.duckduckgo.subscriptions.impl.billing.PurchaseState
import com.duckduckgo.subscriptions.store.AuthDataStore
import com.squareup.anvil.annotations.ContributesBinding
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
    suspend fun purchase(activity: Activity, productDetails: ProductDetails, offerToken: String, isReset: Boolean = false)

    /**
     * Recovers a subscription from the store
     */
    suspend fun recoverSubscriptionFromStore(): SubscriptionsData

    /**
     * Gets the subscription data for an authenticated user
     */
    suspend fun getSubscriptionData(): SubscriptionsData

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
     * Flow to know if a user is signed in or not
     */
    val isSignedIn: Flow<Boolean>

    /**
     * Flow to know if a user has a subscription or not
     */
    val hasSubscription: Flow<Boolean>

    /**
     * Flow to know if a user is signed in or not
     */
    val currentPurchaseState: Flow<CurrentPurchase>

    /**
     * Signs the user out
     */
    suspend fun signOut()
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealSubscriptionsManager @Inject constructor(
    private val authService: AuthService,
    private val authDataStore: AuthDataStore,
    private val billingClientWrapper: BillingClientWrapper,
    private val context: Context,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : SubscriptionsManager {

    private val adapter = Moshi.Builder().build().adapter(ResponseError::class.java)

    private val _currentPurchaseState = MutableSharedFlow<CurrentPurchase>()
    override val currentPurchaseState = _currentPurchaseState.asSharedFlow().onSubscription { emitCurrentPurchaseValues() }

    private val _isSignedIn = MutableStateFlow(isUserAuthenticated())
    override val isSignedIn = _isSignedIn.asStateFlow()

    private val _hasSubscription = MutableStateFlow(false)
    override val hasSubscription = _hasSubscription.asStateFlow().onSubscription { emitHasSubscriptionsValues() }

    private var purchaseStateJob: Job? = null
    private fun isUserAuthenticated(): Boolean = !authDataStore.accessToken.isNullOrBlank() && !authDataStore.authToken.isNullOrBlank()

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
                    is PurchaseState.Purchased -> checkPurchase()
                    else -> {
                        // NOOP
                    }
                }
            }
        }
    }

    override suspend fun signOut() {
        authDataStore.authToken = ""
        authDataStore.accessToken = ""
        _isSignedIn.emit(false)
        _hasSubscription.emit(false)
    }

    private suspend fun checkPurchase() {
        _currentPurchaseState.emit(CurrentPurchase.InProgress)
        delay(500)
        var retries = 1
        var hasSubscription = hasSubscription()
        while (!hasSubscription && retries <= 3) {
            delay(500L * retries)
            hasSubscription = hasSubscription()
            retries++
        }
        if (hasSubscription) {
            _currentPurchaseState.emit(CurrentPurchase.Success)
        } else {
            _currentPurchaseState.emit(CurrentPurchase.Failure("An error happened, try again"))
        }
        _hasSubscription.emit(hasSubscription)
    }

    private suspend fun hasSubscription(): Boolean {
        return when (val result = getSubscriptionData()) {
            is Success -> {
                val isSubscribed = result.entitlements.isNotEmpty()
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
            authDataStore.accessToken = response.accessToken
            authDataStore.authToken = authToken
            val subscriptionData = getSubscriptionDataFromToken(response.accessToken)
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
                authenticate(response.authToken)
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
                getSubscriptionDataFromToken(authDataStore.accessToken!!)
            } else {
                Failure("Subscription data not found")
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
                    val billingParams = billingClientWrapper.billingFlowParamsBuilder(
                        productDetails = productDetails,
                        offerToken = offerToken,
                        externalId = response.externalId,
                        isReset = isReset,
                    ).build()
                    logcat(LogPriority.DEBUG) { "Subs: external id is ${response.externalId}" }
                    _currentPurchaseState.emit(CurrentPurchase.PreFlowFinished)
                    withContext(dispatcherProvider.main()) {
                        billingClientWrapper.launchBillingFlow(activity, billingParams)
                    }
                } else {
                    _currentPurchaseState.emit(CurrentPurchase.Recovered)
                }
            }
            is Failure -> {
                logcat(LogPriority.ERROR) { "Subs: ${response.message}" }
                _currentPurchaseState.emit(CurrentPurchase.Failure(response.message))
            }
        }
    }

    private suspend fun prePurchaseFlow(): SubscriptionsData {
        return try {
            val subscriptionData = if (isUserAuthenticated()) {
                getSubscriptionDataFromToken(authDataStore.accessToken!!)
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
            when (val response = getSubscriptionDataFromToken(authDataStore.authToken!!)) {
                is Success -> {
                    return if (response.entitlements.isEmpty()) {
                        AuthToken.Failure("")
                    } else {
                        AuthToken.Success(authDataStore.authToken!!)
                    }
                }
                is Failure -> {
                    when (response.message) {
                        "expired_token" -> {
                            logcat(LogPriority.DEBUG) { "Subs: auth token expired" }
                            val subscriptionsData = recoverSubscriptionFromStore()
                            if (subscriptionsData is Success) {
                                AuthToken.Success(authDataStore.authToken!!)
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
                AccessToken.Success(authDataStore.accessToken!!)
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
        return authService.createAccount()
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
    data class Success(val email: String?, val externalId: String, val entitlements: List<Entitlement>) : SubscriptionsData()
    data class Failure(val message: String) : SubscriptionsData()
}

sealed class CurrentPurchase {
    object PreFlowInProgress : CurrentPurchase()
    object PreFlowFinished : CurrentPurchase()
    object InProgress : CurrentPurchase()
    object Success : CurrentPurchase()
    object Recovered : CurrentPurchase()
    data class Failure(val message: String) : CurrentPurchase()
}
