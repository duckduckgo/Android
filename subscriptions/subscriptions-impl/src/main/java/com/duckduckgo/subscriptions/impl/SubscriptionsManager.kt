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

import android.content.Context
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.subscriptions.impl.SubscriptionsDataResult.Failure
import com.duckduckgo.subscriptions.impl.SubscriptionsDataResult.Success
import com.duckduckgo.subscriptions.impl.auth.AuthService
import com.duckduckgo.subscriptions.impl.auth.CreateAccountResponse
import com.duckduckgo.subscriptions.impl.auth.EntitlementsResponse
import com.duckduckgo.subscriptions.impl.auth.ResponseError
import com.duckduckgo.subscriptions.impl.auth.StoreLoginBody
import com.duckduckgo.subscriptions.impl.repository.SubscriptionsRepository
import com.duckduckgo.subscriptions.store.AuthDataStore
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.Moshi
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import logcat.LogPriority
import logcat.logcat
import retrofit2.HttpException

interface SubscriptionsManager {

    /**
     * Executes the pre-purchase flow which tries to recover the external_id from the store,
     * if it cannot, it creates a new account
     */
    suspend fun prePurchaseFlow(): SubscriptionsDataResult

    /**
     * Recovers a subscription from the store
     */
    suspend fun recoverSubscriptionFromStore(): SubscriptionsDataResult

    /**
     * Gets the subscription data for an authenticated user
     */
    suspend fun getSubscriptionData(): SubscriptionsDataResult

    suspend fun authenticate(token: String): SubscriptionsDataResult

    /**
     * Flow to know if a user is signed in or not
     */
    val isSignedIn: Flow<Boolean>
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealSubscriptionsManager @Inject constructor(
    private val authService: AuthService,
    private val authDataStore: AuthDataStore,
    private val subscriptionsRepository: SubscriptionsRepository,
    private val context: Context,
) : SubscriptionsManager {

    private val adapter = Moshi.Builder().build().adapter(ResponseError::class.java)
    private val _isSignedIn = MutableStateFlow(isUserAuthenticated())
    override val isSignedIn = _isSignedIn.asStateFlow()

    private fun isUserAuthenticated(): Boolean = !authDataStore.token.isNullOrBlank()

    override suspend fun authenticate(token: String): SubscriptionsDataResult {
        return try {
            val accessToken = getAccessToken(token)
            authDataStore.token = accessToken
            _isSignedIn.emit(isUserAuthenticated())
            return getSubscriptionDataFromToken()
        } catch (e: HttpException) {
            val error = parseError(e)?.error ?: "An error happened"
            Failure(error)
        } catch (e: Exception) {
            Failure(e.message ?: "An error happened")
        }
    }

    override suspend fun recoverSubscriptionFromStore(): SubscriptionsDataResult {
        return try {
            val externalId = getDataFromPurchaseHistory()
            if (externalId is Success) {
                externalId
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

    override suspend fun getSubscriptionData(): SubscriptionsDataResult {
        return try {
            if (isUserAuthenticated()) {
                getSubscriptionDataFromToken()
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

    override suspend fun prePurchaseFlow(): SubscriptionsDataResult {
        return try {
            val subscriptionData = if (isUserAuthenticated()) {
                getSubscriptionDataFromToken()
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

    private suspend fun getDataFromPurchaseHistory(): SubscriptionsDataResult {
        return try {
            val purchase = subscriptionsRepository.lastPurchaseHistoryRecord.value
            return if (purchase != null) {
                val signature = purchase.signature
                val body = purchase.originalJson
                val storeLoginBody = StoreLoginBody(
                    signature = signature,
                    signedData = body,
                    packageName = context.packageName,
                )
                val response = authService.storeLogin(storeLoginBody)
                logcat(LogPriority.DEBUG) { "Subs: store login succeeded" }
                authenticate(response.authToken)
            } else {
                Failure("Subs: no previous purchases found")
            }
        } catch (e: HttpException) {
            val error = parseError(e)?.error ?: "An error happened"
            Failure(error)
        } catch (e: Exception) {
            Failure(e.message ?: "An error happened")
        }
    }

    private suspend fun getSubscriptionDataFromToken(): SubscriptionsDataResult {
        return try {
            val response = authService.validateToken("Bearer ${authDataStore.token}")
            logcat(LogPriority.DEBUG) { "Subs: token validated ${authDataStore.token}" }
            Success(externalId = response.account.externalId, pat = authDataStore.token!!, entitlements = response.account.entitlements)
        } catch (e: HttpException) {
            val error = parseError(e)
            Failure(error?.error ?: "An error happened")
        } catch (e: Exception) {
            Failure(e.message ?: "An error happened")
        }
    }

    private suspend fun getAccessToken(token: String): String {
        logcat(LogPriority.DEBUG) { "Subs: getting access token $token" }
        val response = authService.accessToken("Bearer $token")
        logcat(LogPriority.DEBUG) { "Subs: access token ${response.accessToken}" }
        return response.accessToken
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
}

sealed class SubscriptionsDataResult {
    data class Success(val externalId: String, val pat: String, val entitlements: List<EntitlementsResponse>) : SubscriptionsDataResult()
    data class Failure(val message: String) : SubscriptionsDataResult()
}
