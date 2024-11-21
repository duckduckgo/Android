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
import com.duckduckgo.data.store.api.SharedPreferencesProvider
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
import com.duckduckgo.subscriptions.impl.serp_promo.SerpPromo
import com.duckduckgo.subscriptions.impl.store.SubscriptionsDataStore
import com.duckduckgo.subscriptions.impl.store.SubscriptionsEncryptedDataStore
import com.duckduckgo.subscriptions.impl.toStatus
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.moshi.Moshi
import com.squareup.moshi.Moshi.Builder
import com.squareup.moshi.Types
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import java.time.Instant
import kotlinx.coroutines.withContext

interface AuthRepository {
    suspend fun setAccessTokenV2(accessToken: AccessToken?)
    suspend fun getAccessTokenV2(): AccessToken?
    suspend fun setRefreshTokenV2(refreshToken: RefreshToken?)
    suspend fun getRefreshTokenV2(): RefreshToken?
    suspend fun setAccessToken(accessToken: String?)
    suspend fun getAccessToken(): String?
    suspend fun setAuthToken(authToken: String?)
    suspend fun getAuthToken(): String?
    suspend fun setAccount(account: Account?)
    suspend fun getAccount(): Account?
    suspend fun setSubscription(subscription: Subscription?)
    suspend fun getSubscription(): Subscription?
    suspend fun setEntitlements(entitlements: List<Entitlement>)
    suspend fun getEntitlements(): List<Entitlement>
    suspend fun purchaseToWaitingStatus()
    suspend fun getStatus(): SubscriptionStatus
    suspend fun canSupportEncryption(): Boolean
}

@Module
@ContributesTo(AppScope::class)
object AuthRepositoryModule {
    @Provides
    @SingleInstanceIn(AppScope::class)
    fun provideAuthRepository(
        dispatcherProvider: DispatcherProvider,
        sharedPreferencesProvider: SharedPreferencesProvider,
        serpPromo: SerpPromo,
    ): AuthRepository {
        return RealAuthRepository(SubscriptionsEncryptedDataStore(sharedPreferencesProvider), dispatcherProvider, serpPromo)
    }
}

internal class RealAuthRepository constructor(
    private val subscriptionsDataStore: SubscriptionsDataStore,
    private val dispatcherProvider: DispatcherProvider,
    private val serpPromo: SerpPromo,
) : AuthRepository {

    private val moshi = Builder().build()

    private inline fun <reified T> Moshi.listToJson(list: List<T>): String {
        return adapter<List<T>>(Types.newParameterizedType(List::class.java, T::class.java)).toJson(list)
    }
    private inline fun <reified T> Moshi.parseList(jsonString: String): List<T>? {
        return adapter<List<T>>(Types.newParameterizedType(List::class.java, T::class.java)).fromJson(jsonString)
    }

    override suspend fun setAccessTokenV2(accessToken: AccessToken?) = withContext(dispatcherProvider.io()) {
        subscriptionsDataStore.accessTokenV2 = accessToken?.jwt
        subscriptionsDataStore.accessTokenV2ExpiresAt = accessToken?.expiresAt
        updateSerpPromoCookie()
    }

    override suspend fun getAccessTokenV2(): AccessToken? {
        val jwt = subscriptionsDataStore.accessTokenV2 ?: return null
        val expiresAt = subscriptionsDataStore.accessTokenV2ExpiresAt ?: return null
        return AccessToken(jwt, expiresAt)
    }

    override suspend fun setRefreshTokenV2(refreshToken: RefreshToken?) {
        subscriptionsDataStore.refreshTokenV2 = refreshToken?.jwt
        subscriptionsDataStore.refreshTokenV2ExpiresAt = refreshToken?.expiresAt
    }

    override suspend fun getRefreshTokenV2(): RefreshToken? {
        val jwt = subscriptionsDataStore.refreshTokenV2 ?: return null
        val expiresAt = subscriptionsDataStore.refreshTokenV2ExpiresAt ?: return null
        return RefreshToken(jwt, expiresAt)
    }

    override suspend fun setEntitlements(entitlements: List<Entitlement>) = withContext(dispatcherProvider.io()) {
        subscriptionsDataStore.entitlements = moshi.listToJson(entitlements)
    }

    override suspend fun getEntitlements(): List<Entitlement> {
        return subscriptionsDataStore.entitlements?.let { moshi.parseList(it) } ?: emptyList()
    }

    override suspend fun setAccessToken(accessToken: String?) = withContext(dispatcherProvider.io()) {
        subscriptionsDataStore.accessToken = accessToken
        updateSerpPromoCookie()
    }

    override suspend fun setAuthToken(authToken: String?) = withContext(dispatcherProvider.io()) {
        subscriptionsDataStore.authToken = authToken
    }

    override suspend fun purchaseToWaitingStatus() = withContext(dispatcherProvider.io()) {
        subscriptionsDataStore.status = WAITING.statusName
    }

    override suspend fun getStatus(): SubscriptionStatus = withContext(dispatcherProvider.io()) {
        subscriptionsDataStore.status?.toStatus() ?: UNKNOWN
    }

    override suspend fun getAccessToken(): String? = withContext(dispatcherProvider.io()) {
        subscriptionsDataStore.accessToken
    }

    override suspend fun getAuthToken(): String? = withContext(dispatcherProvider.io()) {
        subscriptionsDataStore.authToken
    }

    override suspend fun setSubscription(subscription: Subscription?) = withContext(dispatcherProvider.io()) {
        with(subscriptionsDataStore) {
            productId = subscription?.productId
            platform = subscription?.platform
            startedAt = subscription?.startedAt
            expiresOrRenewsAt = subscription?.expiresOrRenewsAt
            status = subscription?.status?.statusName
        }
    }

    override suspend fun getSubscription(): Subscription? = withContext(dispatcherProvider.io()) {
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
        )
    }

    override suspend fun setAccount(account: Account?) = withContext(dispatcherProvider.io()) {
        subscriptionsDataStore.externalId = account?.externalId
        subscriptionsDataStore.email = account?.email
    }

    override suspend fun getAccount(): Account? = withContext(dispatcherProvider.io()) {
        val externalId = subscriptionsDataStore.externalId ?: return@withContext null
        val email = subscriptionsDataStore.email

        Account(
            email = email,
            externalId = externalId,
        )
    }

    override suspend fun canSupportEncryption(): Boolean = withContext(dispatcherProvider.io()) {
        subscriptionsDataStore.canUseEncryption()
    }

    private suspend fun updateSerpPromoCookie() {
        val accessToken = subscriptionsDataStore.run { accessTokenV2 ?: accessToken }
        serpPromo.injectCookie(accessToken)
    }
}

data class AccessToken(
    val jwt: String,
    val expiresAt: Instant,
)

data class RefreshToken(
    val jwt: String,
    val expiresAt: Instant,
)

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
