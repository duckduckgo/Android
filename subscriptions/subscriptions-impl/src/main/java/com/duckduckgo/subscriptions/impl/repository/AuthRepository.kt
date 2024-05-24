/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.subscriptions.impl.repository

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
import com.duckduckgo.subscriptions.impl.services.SubscriptionResponse
import com.duckduckgo.subscriptions.impl.store.SubscriptionsDataStore
import com.duckduckgo.subscriptions.impl.toStatus
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.Moshi
import com.squareup.moshi.Moshi.Builder
import com.squareup.moshi.Types
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.withContext

interface AuthRepository {

    suspend fun isUserAuthenticated(): Boolean
    suspend fun setAccessToken(accessToken: String)
    suspend fun getAccessToken(): String?
    suspend fun saveAuthToken(authToken: String)
    suspend fun getAuthToken(): String?
    suspend fun saveAccountData(authToken: String, externalId: String)
    suspend fun getAccount(): Account?
    suspend fun saveSubscriptionData(subscriptionResponse: SubscriptionResponse, entitlements: List<Entitlement>, email: String?): Subscription?
    suspend fun saveExternalId(externalId: String)
    suspend fun getSubscription(): Subscription?
    suspend fun clearSubscription()
    suspend fun clearAccount()
    suspend fun setEntitlements(entitlements: List<Entitlement>)
    suspend fun setEmail(email: String?)
    suspend fun purchaseToWaitingStatus()
    suspend fun getStatus(): SubscriptionStatus
    suspend fun canSupportEncryption(): Boolean
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealAuthRepository @Inject constructor(
    private val subscriptionsDataStore: SubscriptionsDataStore,
    private val dispatcherProvider: DispatcherProvider,
) : AuthRepository {

    private val moshi = Builder().build()

    private inline fun <reified T> Moshi.listToJson(list: List<T>): String {
        return adapter<List<T>>(Types.newParameterizedType(List::class.java, T::class.java)).toJson(list)
    }
    private inline fun <reified T> Moshi.parseList(jsonString: String): List<T>? {
        return adapter<List<T>>(Types.newParameterizedType(List::class.java, T::class.java)).fromJson(jsonString)
    }

    override suspend fun setEmail(email: String?) = withContext(dispatcherProvider.io()) {
        subscriptionsDataStore.email = email
    }

    override suspend fun setEntitlements(entitlements: List<Entitlement>) = withContext(dispatcherProvider.io()) {
        subscriptionsDataStore.entitlements = moshi.listToJson(entitlements)
    }

    override suspend fun isUserAuthenticated(): Boolean = withContext(dispatcherProvider.io()) {
        !subscriptionsDataStore.accessToken.isNullOrBlank() && !subscriptionsDataStore.authToken.isNullOrBlank()
    }

    override suspend fun saveAccountData(authToken: String, externalId: String) = withContext(dispatcherProvider.io()) {
        subscriptionsDataStore.authToken = authToken
        subscriptionsDataStore.externalId = externalId
    }

    override suspend fun setAccessToken(accessToken: String) = withContext(dispatcherProvider.io()) {
        subscriptionsDataStore.accessToken = accessToken
    }

    override suspend fun saveAuthToken(authToken: String) = withContext(dispatcherProvider.io()) {
        subscriptionsDataStore.authToken = authToken
    }

    override suspend fun clearAccount() = withContext(dispatcherProvider.io()) {
        subscriptionsDataStore.email = null
        subscriptionsDataStore.externalId = null
        subscriptionsDataStore.authToken = null
        subscriptionsDataStore.accessToken = null
    }

    override suspend fun purchaseToWaitingStatus() = withContext(dispatcherProvider.io()) {
        subscriptionsDataStore.status = WAITING.statusName
    }

    override suspend fun getStatus(): SubscriptionStatus = withContext(dispatcherProvider.io()) {
        subscriptionsDataStore.status?.toStatus() ?: UNKNOWN
    }

    override suspend fun clearSubscription() = withContext(dispatcherProvider.io()) {
        subscriptionsDataStore.status = null
        subscriptionsDataStore.startedAt = null
        subscriptionsDataStore.expiresOrRenewsAt = null
        subscriptionsDataStore.platform = null
        subscriptionsDataStore.productId = null
        subscriptionsDataStore.entitlements = null
    }

    override suspend fun getAccessToken(): String? = withContext(dispatcherProvider.io()) {
        subscriptionsDataStore.accessToken
    }

    override suspend fun getAuthToken(): String? = withContext(dispatcherProvider.io()) {
        subscriptionsDataStore.authToken
    }

    override suspend fun getSubscription(): Subscription? = withContext(dispatcherProvider.io()) {
        val entitlements = subscriptionsDataStore.entitlements ?: "[]"
        val productId = subscriptionsDataStore.productId ?: return@withContext null
        val platform = subscriptionsDataStore.platform ?: return@withContext null
        val startedAt = subscriptionsDataStore.startedAt ?: return@withContext null
        val expiresOrRenewsAt = subscriptionsDataStore.expiresOrRenewsAt ?: return@withContext null
        val status = subscriptionsDataStore.status?.toStatus() ?: return@withContext null
        Subscription(
            productId = productId,
            platform = platform,
            startedAt = startedAt,
            expiresOrRenewsAt = expiresOrRenewsAt,
            status = status,
            entitlements = moshi.parseList<Entitlement>(entitlements).orEmpty(),
        )
    }

    override suspend fun getAccount(): Account? = withContext(dispatcherProvider.io()) {
        val externalId = subscriptionsDataStore.externalId ?: return@withContext null
        val email = subscriptionsDataStore.email

        Account(
            email = email,
            externalId = externalId,
        )
    }

    override suspend fun saveSubscriptionData(
        subscriptionResponse: SubscriptionResponse,
        entitlements: List<Entitlement>,
        email: String?,
    ): Subscription? = withContext(dispatcherProvider.io()) {
        subscriptionsDataStore.status = subscriptionResponse.status.toStatus().statusName
        subscriptionsDataStore.startedAt = subscriptionResponse.startedAt
        subscriptionsDataStore.expiresOrRenewsAt = subscriptionResponse.expiresOrRenewsAt
        subscriptionsDataStore.platform = subscriptionResponse.platform
        subscriptionsDataStore.productId = subscriptionResponse.productId
        subscriptionsDataStore.email = email
        subscriptionsDataStore.entitlements = moshi.listToJson(entitlements)
        getSubscription()
    }

    override suspend fun saveExternalId(externalId: String) = withContext(dispatcherProvider.io()) {
        subscriptionsDataStore.externalId = externalId
    }

    override suspend fun canSupportEncryption(): Boolean = withContext(dispatcherProvider.io()) {
        subscriptionsDataStore.canUseEncryption()
    }
}

data class Account(
    val email: String?,
    val externalId: String,
)

data class Subscription(
    val productId: String,
    val startedAt: Long,
    val expiresOrRenewsAt: Long,
    val status: SubscriptionStatus,
    val platform: String,
    val entitlements: List<Entitlement>,
) {
    fun isActive(): Boolean = status.isActive()
}

fun SubscriptionStatus.isActive(): Boolean {
    return when (this) {
        AUTO_RENEWABLE, NOT_AUTO_RENEWABLE, GRACE_PERIOD -> true
        else -> false
    }
}

fun SubscriptionStatus.isExpired(): Boolean =
    this == EXPIRED || this == INACTIVE

fun SubscriptionStatus.isActiveOrWaiting(): Boolean {
    return this.isActive() || this == WAITING
}

fun List<Entitlement>.toProductList(): List<Product> {
    return try {
        this.mapNotNull { entitlement ->
            Product.entries.find { it.value == entitlement.product }
        }
    } catch (e: Exception) {
        emptyList()
    }
}

data class Entitlement(
    val name: String,
    val product: String,
)
