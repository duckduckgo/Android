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
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.authjwt.api.AccessTokenClaims
import com.duckduckgo.authjwt.api.AuthJwtValidator
import com.duckduckgo.authjwt.api.RefreshTokenClaims
import com.duckduckgo.autofill.api.email.EmailManager
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.subscriptions.api.Product
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.api.SubscriptionStatus.AUTO_RENEWABLE
import com.duckduckgo.subscriptions.api.SubscriptionStatus.EXPIRED
import com.duckduckgo.subscriptions.api.SubscriptionStatus.GRACE_PERIOD
import com.duckduckgo.subscriptions.api.SubscriptionStatus.INACTIVE
import com.duckduckgo.subscriptions.api.SubscriptionStatus.NOT_AUTO_RENEWABLE
import com.duckduckgo.subscriptions.api.SubscriptionStatus.UNKNOWN
import com.duckduckgo.subscriptions.api.SubscriptionStatus.WAITING
import com.duckduckgo.subscriptions.impl.RealSubscriptionsManager.RecoverSubscriptionResult
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.BASIC_SUBSCRIPTION
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.MONTHLY_PLAN
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.YEARLY_PLAN
import com.duckduckgo.subscriptions.impl.auth2.AuthClient
import com.duckduckgo.subscriptions.impl.auth2.BackgroundTokenRefresh
import com.duckduckgo.subscriptions.impl.auth2.PkceGenerator
import com.duckduckgo.subscriptions.impl.auth2.TokenPair
import com.duckduckgo.subscriptions.impl.billing.PlayBillingManager
import com.duckduckgo.subscriptions.impl.billing.PurchaseState
import com.duckduckgo.subscriptions.impl.billing.RetryPolicy
import com.duckduckgo.subscriptions.impl.billing.retry
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixelSender
import com.duckduckgo.subscriptions.impl.repository.AccessToken
import com.duckduckgo.subscriptions.impl.repository.Account
import com.duckduckgo.subscriptions.impl.repository.AuthRepository
import com.duckduckgo.subscriptions.impl.repository.Entitlement
import com.duckduckgo.subscriptions.impl.repository.RefreshToken
import com.duckduckgo.subscriptions.impl.repository.Subscription
import com.duckduckgo.subscriptions.impl.repository.isActiveOrWaiting
import com.duckduckgo.subscriptions.impl.repository.isExpired
import com.duckduckgo.subscriptions.impl.repository.toProductList
import com.duckduckgo.subscriptions.impl.services.AuthService
import com.duckduckgo.subscriptions.impl.services.ConfirmationBody
import com.duckduckgo.subscriptions.impl.services.ResponseError
import com.duckduckgo.subscriptions.impl.services.StoreLoginBody
import com.duckduckgo.subscriptions.impl.services.SubscriptionsService
import com.duckduckgo.subscriptions.impl.services.ValidateTokenResponse
import com.duckduckgo.subscriptions.impl.services.toEntitlements
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import com.squareup.moshi.Moshi
import dagger.Lazy
import dagger.SingleInstanceIn
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import logcat.LogPriority
import logcat.logcat
import retrofit2.HttpException

interface SubscriptionsManager {

    /**
     * Returns available purchase options retrieved from Play Store
     */
    suspend fun getSubscriptionOffer(): SubscriptionOffer?

    /**
     * Launches the purchase flow for a given plan id
     */
    suspend fun purchase(
        activity: Activity,
        planId: String,
    )

    /**
     * Recovers a subscription from the store
     */
    suspend fun recoverSubscriptionFromStore(externalId: String? = null): RecoverSubscriptionResult

    /**
     * Fetches subscription and account data from the BE and stores it
     *
     * @return [true] if successful, [false] otherwise
     */
    @Deprecated("This method will be removed after migrating to auth v2")
    suspend fun fetchAndStoreAllData(): Boolean

    /**
     * Gets the subscription details from internal storage
     */
    suspend fun getSubscription(): Subscription?

    /**
     * Gets the account details from internal storage
     */
    suspend fun getAccount(): Account?

    /**
     * Exchanges the auth token for an access token and stores both tokens
     */
    @Deprecated("This method will be removed after migrating to auth v2")
    suspend fun exchangeAuthToken(authToken: String): String

    /**
     * Returns the auth token and if expired, tries to refresh irt
     */
    @Deprecated("This method will be removed after migrating to auth v2")
    suspend fun getAuthToken(): AuthTokenResult

    /**
     * Returns the access token from store
     */
    suspend fun getAccessToken(): AccessTokenResult

    /**
     * Returns current subscription status
     */
    suspend fun subscriptionStatus(): SubscriptionStatus

    /**
     * Checks if user is signed in or not (using either auth API v1 or v2)
     */
    suspend fun isSignedIn(): Boolean

    /**
     * Checks if user is signed in or not using auth API v2
     */
    suspend fun isSignedInV2(): Boolean

    /**
     * Flow to know if a user is signed in or not
     */
    val isSignedIn: Flow<Boolean>

    /**
     * Flow to know current subscription status
     */
    val subscriptionStatus: Flow<SubscriptionStatus>

    /**
     * Flow to return products user is entitled to
     */
    val entitlements: Flow<List<Product>>

    /**
     * Flow to know the state of the current purchase
     */
    val currentPurchaseState: Flow<CurrentPurchase>

    /**
     * Signs the user out and deletes all the data from the device
     */
    suspend fun signOut()

    /**
     * Returns a [String] with the URL of the portal or null otherwise
     */
    suspend fun getPortalUrl(): String?

    suspend fun canSupportEncryption(): Boolean
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealSubscriptionsManager @Inject constructor(
    private val authService: AuthService,
    private val subscriptionsService: SubscriptionsService,
    private val authRepository: AuthRepository,
    private val playBillingManager: PlayBillingManager,
    private val emailManager: EmailManager,
    private val context: Context,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val pixelSender: SubscriptionPixelSender,
    private val privacyProFeature: Lazy<PrivacyProFeature>,
    private val authClient: AuthClient,
    private val authJwtValidator: AuthJwtValidator,
    private val pkceGenerator: PkceGenerator,
    private val timeProvider: CurrentTimeProvider,
    private val backgroundTokenRefresh: BackgroundTokenRefresh,
) : SubscriptionsManager {

    private val adapter = Moshi.Builder().build().adapter(ResponseError::class.java)

    private val _currentPurchaseState = MutableSharedFlow<CurrentPurchase>()
    override val currentPurchaseState = _currentPurchaseState.asSharedFlow().onSubscription { emitCurrentPurchaseValues() }

    private val _isSignedIn = MutableStateFlow(false)
    override val isSignedIn = _isSignedIn.asStateFlow().onSubscription { emitIsSignedInValues() }

    private val _subscriptionStatus: MutableSharedFlow<SubscriptionStatus> = MutableSharedFlow(replay = 1, onBufferOverflow = DROP_OLDEST)
    override val subscriptionStatus = _subscriptionStatus.onSubscription { emitHasSubscriptionsValues() }

    // A state flow behaves identically to a shared flow when it is created with the following parameters
    // See https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/-state-flow/
    // See also https://github.com/Kotlin/kotlinx.coroutines/issues/2515
    //
    // WARNING: only use _state to emit values, for anything else use getState()
    private val _entitlements: MutableSharedFlow<List<Product>> = MutableSharedFlow(
        replay = 1,
        onBufferOverflow = DROP_OLDEST,
    )
    override val entitlements = _entitlements.onSubscription { emitEntitlementsValues() }

    private var purchaseStateJob: Job? = null

    private var removeExpiredSubscriptionOnCancelledPurchase: Boolean = false

    override suspend fun isSignedIn(): Boolean {
        return isSignedInV1() || isSignedInV2()
    }

    private suspend fun isSignedInV1(): Boolean {
        return !authRepository.getAuthToken().isNullOrBlank() && !authRepository.getAccessToken().isNullOrBlank()
    }

    override suspend fun isSignedInV2(): Boolean {
        return authRepository.getRefreshTokenV2() != null
    }

    private suspend fun shouldUseAuthV2(): Boolean {
        return privacyProFeature.get().authApiV2().isEnabled() || isSignedInV2()
    }

    private fun emitEntitlementsValues() {
        coroutineScope.launch(dispatcherProvider.io()) {
            val entitlements = if (authRepository.getSubscription()?.status?.isActiveOrWaiting() == true) {
                authRepository.getEntitlements().toProductList()
            } else {
                emptyList()
            }
            _entitlements.emit(entitlements)
        }
    }

    private fun emitIsSignedInValues() {
        coroutineScope.launch(dispatcherProvider.io()) {
            _isSignedIn.emit(isSignedIn())
        }
    }

    private fun emitHasSubscriptionsValues() {
        coroutineScope.launch(dispatcherProvider.io()) {
            _subscriptionStatus.emit(subscriptionStatus())
        }
    }

    private fun emitCurrentPurchaseValues() {
        purchaseStateJob?.cancel()
        purchaseStateJob = coroutineScope.launch(dispatcherProvider.io()) {
            playBillingManager.purchaseState.collect {
                when (it) {
                    is PurchaseState.Purchased -> checkPurchase(it.packageName, it.purchaseToken)
                    is PurchaseState.Canceled -> {
                        _currentPurchaseState.emit(CurrentPurchase.Canceled)
                        if (removeExpiredSubscriptionOnCancelledPurchase) {
                            if (subscriptionStatus().isExpired()) {
                                signOut()
                            }
                            removeExpiredSubscriptionOnCancelledPurchase = false
                        }
                    }
                    else -> {
                        // NOOP
                    }
                }
            }
        }
    }

    override suspend fun canSupportEncryption(): Boolean = authRepository.canSupportEncryption()

    override suspend fun getAccount(): Account? = authRepository.getAccount()

    override suspend fun getPortalUrl(): String? {
        return try {
            return subscriptionsService.portal().customerPortalUrl
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getSubscription(): Subscription? {
        return authRepository.getSubscription()
    }

    override suspend fun signOut() {
        authRepository.setAccessTokenV2(null)
        authRepository.setRefreshTokenV2(null)
        authRepository.setAuthToken(null)
        authRepository.setAccessToken(null)
        authRepository.setAccount(null)
        authRepository.setSubscription(null)
        _isSignedIn.emit(false)
        _subscriptionStatus.emit(UNKNOWN)
        _entitlements.emit(emptyList())
    }

    private suspend fun checkPurchase(
        packageName: String,
        purchaseToken: String,
    ) {
        _currentPurchaseState.emit(CurrentPurchase.InProgress)

        var retryCompleted = false

        retry(
            retryPolicy = RetryPolicy(
                retryCount = 2,
                initialDelay = 500.milliseconds,
                maxDelay = 1500.milliseconds,
                delayIncrementFactor = 2.0,
            ),
        ) {
            try {
                retryCompleted = attemptConfirmPurchase(packageName, purchaseToken)
                logcat { "Subs: retry success: $retryCompleted" }
                retryCompleted
            } catch (e: Throwable) {
                logcat { "Subs: error in confirmation retry" }
                false
            }
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
            val confirmationResponse = subscriptionsService.confirm(
                ConfirmationBody(
                    packageName = packageName,
                    purchaseToken = purchaseToken,
                ),
            )

            val subscription = Subscription(
                productId = confirmationResponse.subscription.productId,
                startedAt = confirmationResponse.subscription.startedAt,
                expiresOrRenewsAt = confirmationResponse.subscription.expiresOrRenewsAt,
                status = confirmationResponse.subscription.status.toStatus(),
                platform = confirmationResponse.subscription.platform,
            )

            authRepository.setSubscription(subscription)

            if (shouldUseAuthV2()) {
                // existing access token has to be invalidated after the purchase, because it doesn't have up-to-date entitlements
                authRepository.setAccessTokenV2(null)
                refreshAccessToken()
            } else {
                authRepository.getAccount()
                    ?.copy(email = confirmationResponse.email)
                    ?.let { authRepository.setAccount(it) }

                authRepository.setEntitlements(confirmationResponse.entitlements.toEntitlements())
            }

            if (subscription.isActive()) {
                pixelSender.reportPurchaseSuccess()
                pixelSender.reportSubscriptionActivated()
                emitEntitlementsValues()
                _currentPurchaseState.emit(CurrentPurchase.Success)
            } else {
                handlePurchaseFailed()
            }

            _subscriptionStatus.emit(authRepository.getStatus())
            true
        } catch (e: Exception) {
            logcat { "Subs: failed to confirm purchase $e" }
            false
        }
    }

    private suspend fun handlePurchaseFailed() {
        authRepository.purchaseToWaitingStatus()
        pixelSender.reportPurchaseFailureBackend()
        _currentPurchaseState.emit(CurrentPurchase.Waiting)
        _subscriptionStatus.emit(authRepository.getStatus())
    }

    override suspend fun subscriptionStatus(): SubscriptionStatus {
        return if (isSignedIn()) {
            authRepository.getStatus()
        } else {
            UNKNOWN
        }
    }

    @Deprecated("This method will be removed after migrating to auth v2")
    override suspend fun exchangeAuthToken(authToken: String): String {
        val accessToken = authService.accessToken("Bearer $authToken").accessToken
        authRepository.setAccessToken(accessToken)
        authRepository.setAuthToken(authToken)
        return accessToken
    }

    @Deprecated("This method will be removed after migrating to auth v2")
    override suspend fun fetchAndStoreAllData(): Boolean {
        try {
            if (!isSignedIn()) return false

            val subscription = try {
                subscriptionsService.subscription()
            } catch (e: HttpException) {
                if (e.code() == 401) {
                    logcat { "Token invalid, signing out" }
                    signOut()
                    return false
                }
                throw e
            }
            val token = checkNotNull(authRepository.getAccessToken()) { "Access token should not be null when user is authenticated." }
            val accountData = validateToken(token).account
            authRepository.setAccount(
                Account(
                    email = accountData.email,
                    externalId = accountData.externalId,
                ),
            )
            authRepository.setSubscription(
                Subscription(
                    productId = subscription.productId,
                    startedAt = subscription.startedAt,
                    expiresOrRenewsAt = subscription.expiresOrRenewsAt,
                    status = subscription.status.toStatus(),
                    platform = subscription.platform,
                ),
            )
            authRepository.setEntitlements(accountData.entitlements.toEntitlements())
            emitEntitlementsValues()
            _subscriptionStatus.emit(authRepository.getStatus())
            _isSignedIn.emit(isSignedIn())
            return true
        } catch (e: Exception) {
            logcat { "Failed to fetch subscriptions data: ${e.stackTraceToString()}" }
            return false
        }
    }

    private suspend fun refreshAccessToken() {
        val refreshToken = checkNotNull(authRepository.getRefreshTokenV2())

        val newTokens = try {
            val tokens = authClient.getTokens(refreshToken.jwt)
            validateTokens(tokens)
        } catch (e: HttpException) {
            if (e.code() == 401) {
                // refresh token is invalid / expired -> try to get a new pair of tokens using store login
                val account = checkNotNull(authRepository.getAccount()) { "Missing account info when refreshing access token" }

                when (val storeLoginResult = storeLogin(account.externalId)) {
                    is StoreLoginResult.Success -> storeLoginResult.tokens
                    StoreLoginResult.Failure.AccountExternalIdMismatch,
                    StoreLoginResult.Failure.PurchaseHistoryNotAvailable,
                    StoreLoginResult.Failure.AuthenticationError,
                    -> {
                        signOut()
                        throw e
                    }

                    StoreLoginResult.Failure.Other -> throw e
                }
            } else {
                throw e
            }
        }

        saveTokens(newTokens)
    }

    private suspend fun refreshSubscriptionData() {
        val subscription = subscriptionsService.subscription()

        authRepository.setSubscription(
            Subscription(
                productId = subscription.productId,
                startedAt = subscription.startedAt,
                expiresOrRenewsAt = subscription.expiresOrRenewsAt,
                status = subscription.status.toStatus(),
                platform = subscription.platform,
            ),
        )

        _subscriptionStatus.emit(subscription.status.toStatus())
    }

    private suspend fun validateTokens(tokens: TokenPair): ValidatedTokenPair {
        val jwks = authClient.getJwks()

        return ValidatedTokenPair(
            accessToken = tokens.accessToken,
            accessTokenClaims = authJwtValidator.validateAccessToken(tokens.accessToken, jwks),
            refreshToken = tokens.refreshToken,
            refreshTokenClaims = authJwtValidator.validateRefreshToken(tokens.refreshToken, jwks),
        )
    }

    private suspend fun saveTokens(tokens: ValidatedTokenPair) = with(tokens) {
        authRepository.setAccessTokenV2(AccessToken(accessToken, accessTokenClaims.expiresAt))
        authRepository.setRefreshTokenV2(RefreshToken(refreshToken, refreshTokenClaims.expiresAt))
        authRepository.setEntitlements(accessTokenClaims.entitlements.toEntitlements())
        authRepository.setAccount(Account(email = accessTokenClaims.email, externalId = accessTokenClaims.accountExternalId))
        backgroundTokenRefresh.schedule()
    }

    private fun extractError(e: Exception): String {
        return if (e is HttpException) {
            parseError(e)?.error ?: "An error happened"
        } else {
            e.message ?: "An error happened"
        }
    }

    private suspend fun storeLogin(accountExternalId: String? = null): StoreLoginResult {
        return try {
            val purchase = playBillingManager.purchaseHistory.lastOrNull()
                ?: return StoreLoginResult.Failure.PurchaseHistoryNotAvailable

            val codeVerifier = pkceGenerator.generateCodeVerifier()
            val codeChallenge = pkceGenerator.generateCodeChallenge(codeVerifier)
            val sessionId = authClient.authorize(codeChallenge)
            val authorizationCode = authClient.storeLogin(sessionId, purchase.signature, purchase.originalJson)
            val tokens = authClient.getTokens(sessionId, authorizationCode, codeVerifier)
            val validatedTokens = validateTokens(tokens)

            if (accountExternalId != null && accountExternalId != validatedTokens.accessTokenClaims.accountExternalId) {
                return StoreLoginResult.Failure.AccountExternalIdMismatch
            }

            StoreLoginResult.Success(validatedTokens)
        } catch (e: Exception) {
            if (e is HttpException && e.code() == 400) {
                StoreLoginResult.Failure.AuthenticationError
            } else {
                StoreLoginResult.Failure.Other
            }
        }
    }

    override suspend fun recoverSubscriptionFromStore(externalId: String?): RecoverSubscriptionResult {
        return try {
            if (shouldUseAuthV2()) {
                require(externalId == null) { "Use storeLogin() directly to re-authenticate using existing externalId" }
                when (val storeLoginResult = storeLogin()) {
                    is StoreLoginResult.Success -> {
                        saveTokens(storeLoginResult.tokens)
                        refreshSubscriptionData()
                        val subscription = getSubscription()
                        if (subscription?.isActive() == true) {
                            RecoverSubscriptionResult.Success(subscription)
                        } else {
                            RecoverSubscriptionResult.Failure(SUBSCRIPTION_NOT_FOUND_ERROR)
                        }
                    }
                    is StoreLoginResult.Failure -> {
                        RecoverSubscriptionResult.Failure("")
                    }
                }
            } else {
                val purchase = playBillingManager.purchaseHistory.lastOrNull()
                if (purchase != null) {
                    val signature = purchase.signature
                    val body = purchase.originalJson
                    val storeLoginBody = StoreLoginBody(signature = signature, signedData = body, packageName = context.packageName)
                    val response = authService.storeLogin(storeLoginBody)
                    if (externalId != null && externalId != response.externalId) return RecoverSubscriptionResult.Failure("")
                    authRepository.setAccount(Account(externalId = response.externalId, email = null))
                    authRepository.setAuthToken(response.authToken)
                    exchangeAuthToken(response.authToken)
                    if (fetchAndStoreAllData()) {
                        logcat(LogPriority.DEBUG) { "Subs: store login succeeded" }
                        val subscription = authRepository.getSubscription()
                        if (subscription?.isActive() == true) {
                            RecoverSubscriptionResult.Success(subscription)
                        } else {
                            RecoverSubscriptionResult.Failure(SUBSCRIPTION_NOT_FOUND_ERROR)
                        }
                    } else {
                        RecoverSubscriptionResult.Failure("")
                    }
                } else {
                    RecoverSubscriptionResult.Failure(SUBSCRIPTION_NOT_FOUND_ERROR)
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.DEBUG) { "Subs: Exception!" }
            RecoverSubscriptionResult.Failure(extractError(e))
        }
    }

    sealed class RecoverSubscriptionResult {
        data class Success(val subscription: Subscription) : RecoverSubscriptionResult()
        data class Failure(val message: String) : RecoverSubscriptionResult()
    }

    override suspend fun getSubscriptionOffer(): SubscriptionOffer? =
        playBillingManager.products
            .find { it.productId == BASIC_SUBSCRIPTION }
            ?.run {
                val monthlyOffer = subscriptionOfferDetails?.find { it.basePlanId == MONTHLY_PLAN } ?: return@run null
                val yearlyOffer = subscriptionOfferDetails?.find { it.basePlanId == YEARLY_PLAN } ?: return@run null

                SubscriptionOffer(
                    monthlyPlanId = monthlyOffer.basePlanId,
                    monthlyFormattedPrice = monthlyOffer.pricingPhases.pricingPhaseList.first().formattedPrice,
                    yearlyPlanId = yearlyOffer.basePlanId,
                    yearlyFormattedPrice = yearlyOffer.pricingPhases.pricingPhaseList.first().formattedPrice,
                )
            }

    override suspend fun purchase(
        activity: Activity,
        planId: String,
    ) {
        try {
            _currentPurchaseState.emit(CurrentPurchase.PreFlowInProgress)

            // refresh any existing account / subscription data
            fetchAndStoreAllData()

            if (!isSignedIn()) {
                recoverSubscriptionFromStore()
            } else {
                authRepository.getSubscription()?.run {
                    if (status.isExpired() && platform == "google") {
                        // re-authenticate in case previous subscription was bought using different google account
                        val accountId = authRepository.getAccount()?.externalId
                        recoverSubscriptionFromStore()
                        removeExpiredSubscriptionOnCancelledPurchase =
                            accountId != null && accountId != authRepository.getAccount()?.externalId
                    }
                }
            }

            val subscription = authRepository.getSubscription()

            if (subscription?.isActive() == true) {
                pixelSender.reportSubscriptionActivated()
                pixelSender.reportRestoreAfterPurchaseAttemptSuccess()
                _currentPurchaseState.emit(CurrentPurchase.Recovered)
                return
            }

            if (subscription == null && !isSignedIn()) {
                createAccount()
                if (!shouldUseAuthV2()) {
                    exchangeAuthToken(authRepository.getAuthToken()!!)
                }
            }

            logcat(LogPriority.DEBUG) { "Subs: external id is ${authRepository.getAccount()!!.externalId}" }
            _currentPurchaseState.emit(CurrentPurchase.PreFlowFinished)
            playBillingManager.launchBillingFlow(
                activity = activity,
                planId = planId,
                externalId = authRepository.getAccount()!!.externalId,
            )
        } catch (e: Exception) {
            val error = extractError(e)
            logcat(LogPriority.ERROR) { "Subs: $error" }
            pixelSender.reportPurchaseFailureOther()
            _currentPurchaseState.emit(CurrentPurchase.Failure(error))
        }
    }

    @Deprecated("This method will be removed after migrating to auth v2")
    override suspend fun getAuthToken(): AuthTokenResult {
        if (isSignedInV2()) {
            return when (val accessToken = getAccessToken()) {
                is AccessTokenResult.Failure -> AuthTokenResult.Failure.UnknownError
                is AccessTokenResult.Success -> AuthTokenResult.Success(accessToken.accessToken)
            }
        }

        try {
            return if (isSignedInV1()) {
                logcat { "Subs auth token is ${authRepository.getAuthToken()}" }
                validateToken(authRepository.getAuthToken()!!)
                AuthTokenResult.Success(authRepository.getAuthToken()!!)
            } else {
                AuthTokenResult.Failure.UnknownError
            }
        } catch (e: Exception) {
            return when (extractError(e)) {
                "expired_token" -> {
                    logcat(LogPriority.DEBUG) { "Subs: auth token expired" }
                    val result = recoverSubscriptionFromStore(authRepository.getAccount()?.externalId)
                    if (result is RecoverSubscriptionResult.Success) {
                        AuthTokenResult.Success(authRepository.getAuthToken()!!)
                    } else {
                        AuthTokenResult.Failure.TokenExpired(authRepository.getAuthToken()!!)
                    }
                }
                else -> {
                    AuthTokenResult.Failure.UnknownError
                }
            }
        }
    }

    override suspend fun getAccessToken(): AccessTokenResult {
        return when {
            isSignedIn() && shouldUseAuthV2() -> try {
                AccessTokenResult.Success(getValidAccessTokenV2())
            } catch (e: Exception) {
                AccessTokenResult.Failure("Token not found")
            }
            isSignedInV1() -> AccessTokenResult.Success(authRepository.getAccessToken()!!)
            else -> AccessTokenResult.Failure("Token not found")
        }
    }

    private suspend fun getValidAccessTokenV2(): String {
        check(isSignedIn())
        check(shouldUseAuthV2())

        if (!isSignedInV2() && isSignedInV1()) {
            migrateToAuthV2()
        }

        val accessToken = authRepository.getAccessTokenV2()
            ?.takeIf { isAccessTokenUsable(it) }

        return if (accessToken != null) {
            accessToken.jwt
        } else {
            refreshAccessToken()

            // Rotating auth credentials didn't throw an exception, so a valid access token is expected to be available
            val newAccessToken = authRepository.getAccessTokenV2()
            checkNotNull(newAccessToken)
            check(isAccessTokenUsable(newAccessToken))

            newAccessToken.jwt
        }
    }

    private suspend fun migrateToAuthV2() {
        val accessTokenV1 = checkNotNull(authRepository.getAccessToken())
        val codeVerifier = pkceGenerator.generateCodeVerifier()
        val codeChallenge = pkceGenerator.generateCodeChallenge(codeVerifier)
        val sessionId = authClient.authorize(codeChallenge)
        val authorizationCode = authClient.exchangeV1AccessToken(accessTokenV1, sessionId)
        val tokens = authClient.getTokens(sessionId, authorizationCode, codeVerifier)
        saveTokens(validateTokens(tokens))
        authRepository.setAccessToken(null)
        authRepository.setAuthToken(null)
    }

    private fun isAccessTokenUsable(accessToken: AccessToken): Boolean {
        val currentTime = Instant.ofEpochMilli(timeProvider.currentTimeMillis())
        return accessToken.expiresAt > currentTime + Duration.ofMinutes(1)
    }

    private suspend fun validateToken(token: String): ValidateTokenResponse {
        return authService.validateToken("Bearer $token")
    }

    private suspend fun createAccount() {
        try {
            if (shouldUseAuthV2()) {
                val codeVerifier = pkceGenerator.generateCodeVerifier()
                val codeChallenge = pkceGenerator.generateCodeChallenge(codeVerifier)
                val sessionId = authClient.authorize(codeChallenge)
                val authorizationCode = authClient.createAccount(sessionId)
                val tokens = authClient.getTokens(sessionId, authorizationCode, codeVerifier)
                saveTokens(validateTokens(tokens))
            } else {
                val account = authService.createAccount("Bearer ${emailManager.getToken()}")
                if (account.authToken.isEmpty()) {
                    pixelSender.reportPurchaseFailureAccountCreation()
                } else {
                    authRepository.setAccount(Account(externalId = account.externalId, email = null))
                    authRepository.setAuthToken(account.authToken)
                }
            }
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

    private sealed class StoreLoginResult {
        data class Success(val tokens: ValidatedTokenPair) : StoreLoginResult()
        sealed class Failure : StoreLoginResult() {
            data object PurchaseHistoryNotAvailable : Failure()
            data object AccountExternalIdMismatch : Failure()
            data object AuthenticationError : Failure()
            data object Other : Failure()
        }
    }

    companion object {
        const val SUBSCRIPTION_NOT_FOUND_ERROR = "SubscriptionNotFound"
    }
}

sealed class AccessTokenResult {
    data class Success(val accessToken: String) : AccessTokenResult()
    data class Failure(val message: String) : AccessTokenResult()
}

sealed class AuthTokenResult {
    data class Success(val authToken: String) : AuthTokenResult()
    sealed class Failure : AuthTokenResult() {
        data class TokenExpired(val authToken: String) : Failure()
        data object UnknownError : Failure()
    }
}

fun String.toStatus(): SubscriptionStatus {
    return when (this) {
        "Auto-Renewable" -> AUTO_RENEWABLE
        "Not Auto-Renewable" -> NOT_AUTO_RENEWABLE
        "Grace Period" -> GRACE_PERIOD
        "Inactive" -> INACTIVE
        "Expired" -> EXPIRED
        "Waiting" -> WAITING
        else -> UNKNOWN
    }
}

sealed class CurrentPurchase {
    data object PreFlowInProgress : CurrentPurchase()
    data object PreFlowFinished : CurrentPurchase()
    data object InProgress : CurrentPurchase()
    data object Success : CurrentPurchase()
    data object Waiting : CurrentPurchase()
    data object Recovered : CurrentPurchase()
    data object Canceled : CurrentPurchase()
    data class Failure(val message: String) : CurrentPurchase()
}

data class SubscriptionOffer(
    val monthlyPlanId: String,
    val monthlyFormattedPrice: String,
    val yearlyPlanId: String,
    val yearlyFormattedPrice: String,
)

data class ValidatedTokenPair(
    val accessToken: String,
    val accessTokenClaims: AccessTokenClaims,
    val refreshToken: String,
    val refreshTokenClaims: RefreshTokenClaims,
)

private fun List<com.duckduckgo.authjwt.api.Entitlement>.toEntitlements(): List<Entitlement> =
    map { entitlement -> Entitlement(entitlement.name, entitlement.product) }
