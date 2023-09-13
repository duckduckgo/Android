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
import com.duckduckgo.subscriptions.impl.ExternalIdResult.ExternalId
import com.duckduckgo.subscriptions.impl.ExternalIdResult.Failure
import com.duckduckgo.subscriptions.impl.auth.AuthService
import com.duckduckgo.subscriptions.impl.auth.CreateAccountResponse
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
    suspend fun getExternalId(): ExternalIdResult

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

    override suspend fun getExternalId(): ExternalIdResult {
        try {
            val externalId = if (isUserAuthenticated()) {
                getExternalIdFromToken()
            } else {
                getExternalIdFromPurchaseHistory()
            }
            return if (externalId is ExternalId) {
                externalId
            } else {
                val newAccount = createAccount()
                logcat(LogPriority.DEBUG) { "Subs: account created ${newAccount.externalId}" }
                ExternalId(newAccount.externalId)
            }
        } catch (e: HttpException) {
            val error = parseError(e)?.error ?: "An error happened"
            return Failure(error)
        } catch (e: Exception) {
            return Failure(e.message ?: "An error happened")
        }
    }

    private suspend fun getExternalIdFromPurchaseHistory(): ExternalIdResult {
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
                return ExternalId(response.externalId)
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

    private suspend fun getExternalIdFromToken(): ExternalIdResult {
        try {
            val response = authService.validateToken("Bearer ${authDataStore.token}")
            logcat(LogPriority.DEBUG) { "Subs: token validated" }
            return ExternalId(response.account.externalId)
        } catch (e: HttpException) {
            val error = parseError(e)
            return when (error?.error) {
                "expired_token" -> {
                    logcat(LogPriority.DEBUG) { "Subs: token expired" }
                    getExternalIdFromPurchaseHistory()
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

sealed class ExternalIdResult {
    data class ExternalId(val id: String) : ExternalIdResult()
    data class Failure(val message: String) : ExternalIdResult()
}
