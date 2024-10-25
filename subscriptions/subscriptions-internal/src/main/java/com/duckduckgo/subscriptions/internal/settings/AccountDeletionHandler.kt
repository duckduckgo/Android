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

package com.duckduckgo.subscriptions.internal.settings

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.subscriptions.impl.AuthTokenResult
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.services.AuthService
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface AccountDeletionHandler {
    suspend fun deleteAccountAndSignOut(): Boolean
}

@ContributesBinding(AppScope::class)
class AccountDeletionHandlerImpl @Inject constructor(
    private val subscriptionsManager: SubscriptionsManager,
    private val authService: AuthService,
) : AccountDeletionHandler {

    override suspend fun deleteAccountAndSignOut(): Boolean {
        val accountDeleted = deleteAccount()
        if (accountDeleted) {
            subscriptionsManager.signOut()
        }
        return accountDeleted
    }

    private suspend fun deleteAccount(): Boolean {
        return try {
            val token = subscriptionsManager.getAuthToken()
            if (token is AuthTokenResult.Success) {
                val state = authService.delete("Bearer ${token.authToken}")
                (state.status == "deleted")
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
}
