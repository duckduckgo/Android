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

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.subscriptions.impl.store.SubscriptionsDataStore
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

interface AuthRepository {

    fun isUserAuthenticated(): Boolean

    suspend fun signOut()

    fun tokens(): SubscriptionsTokens

    suspend fun authenticate(authToken: String?, accessToken: String?, externalId: String?, email: String?)

    suspend fun saveSubscriptionData(platform: String, expiresOrRenewsAt: Long)

    suspend fun clearSubscriptionData()
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealAuthRepository @Inject constructor(
    private val subscriptionsDataStore: SubscriptionsDataStore,
) : AuthRepository {

    override fun isUserAuthenticated(): Boolean =
        !subscriptionsDataStore.accessToken.isNullOrBlank() && !subscriptionsDataStore.authToken.isNullOrBlank()

    override suspend fun signOut() {
        subscriptionsDataStore.authToken = null
        subscriptionsDataStore.accessToken = null
        subscriptionsDataStore.platform = null
        subscriptionsDataStore.email = null
        subscriptionsDataStore.externalId = null
        subscriptionsDataStore.expiresOrRenewsAt = 0
    }

    override suspend fun clearSubscriptionData() {
        subscriptionsDataStore.platform = null
        subscriptionsDataStore.expiresOrRenewsAt = 0
    }

    override suspend fun authenticate(authToken: String?, accessToken: String?, externalId: String?, email: String?) {
        subscriptionsDataStore.authToken = authToken
        subscriptionsDataStore.accessToken = accessToken
        subscriptionsDataStore.externalId = externalId
        subscriptionsDataStore.email = email
    }

    override fun tokens(): SubscriptionsTokens = SubscriptionsTokens(
        authToken = subscriptionsDataStore.authToken,
        accessToken = subscriptionsDataStore.accessToken,
    )

    override suspend fun saveSubscriptionData(platform: String, expiresOrRenewsAt: Long) {
        subscriptionsDataStore.platform = platform
        subscriptionsDataStore.expiresOrRenewsAt = expiresOrRenewsAt
    }
}

data class SubscriptionsTokens(val authToken: String?, val accessToken: String?)
