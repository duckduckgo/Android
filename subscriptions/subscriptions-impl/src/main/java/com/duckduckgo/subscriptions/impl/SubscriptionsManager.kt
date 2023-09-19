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
    suspend fun getSubscriptionData(): SubscriptionsDataResult

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

    override suspend fun getSubscriptionData(): SubscriptionsDataResult {
        try {
            val externalId = if (isUserAuthenticated()) {
                getSubscriptionDataFromToken()
            } else {
                getDataFromPurchaseHistory()
            }
            return if (externalId is Success) {
                externalId
            } else {
                val newAccount = createAccount()
                logcat(LogPriority.DEBUG) { "Subs: account created ${newAccount.externalId}" }
                Success(externalId = newAccount.externalId, pat = newAccount.authToken, entitlements = emptyList())
            }
        } catch (e: HttpException) {
            val error = parseError(e)?.error ?: "An error happened"
            return Failure(error)
        } catch (e: Exception) {
            return Failure(e.message ?: "An error happened")
        }
    }

    private suspend fun getDataFromPurchaseHistory(): SubscriptionsDataResult {
        try {
            val purchase = subscriptionsRepository.lastPurchaseHistoryRecord.value
            if (purchase != null) {
                val signature = purchase.signature
                val body = purchase.originalJson
                logcat(LogPriority.DEBUG) { "Subs: body is $body" }
                val storeLoginBody = StoreLoginBody(
                    signature = signature,
                    signedData = body,
                    packageName = context.packageName,
                )
                val response = authService.storeLogin(storeLoginBody)
                logcat(LogPriority.DEBUG) { "Subs: store login succeeded" }
                authDataStore.token = response.authToken
                _isSignedIn.emit(isUserAuthenticated())
                return getSubscriptionDataFromToken()
            } else {
                return Failure("Subs: no previous purchases found")
            }
        } catch (e: HttpException) {
            val error = parseError(e)?.error ?: "An error happened"
            return Failure(error)
        } catch (e: Exception) {
            return Failure(e.message ?: "An error happened")
        }
    }

    private suspend fun getSubscriptionDataFromToken(): SubscriptionsDataResult {
        try {
            val response = authService.validateToken("Bearer ${authDataStore.token}")
            logcat(LogPriority.DEBUG) { "Subs: token validated" }
            return Success(externalId = response.account.externalId, pat = authDataStore.token!!, entitlements = response.account.entitlements)
        } catch (e: HttpException) {
            val error = parseError(e)
            return when (error?.error) {
                "expired_token" -> {
                    logcat(LogPriority.DEBUG) { "Subs: token expired" }
                    getDataFromPurchaseHistory()
                }
                else -> {
                    Failure(error?.error ?: "An error happened")
                }
            }
        } catch (e: Exception) {
            return Failure(e.message ?: "An error happened")
        }
    }

    private suspend fun createAccount(): CreateAccountResponse {
        val response = authService.createAccount()
        authDataStore.token = response.authToken
        _isSignedIn.emit(isUserAuthenticated())
        return response
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
