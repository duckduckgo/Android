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

package com.duckduckgo.networkprotection.subscription

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.subscriptions.api.Subscriptions
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.withContext

interface NetpSubscriptionManager {
    suspend fun getToken(): String?
    suspend fun hasValidEntitlement(): Boolean
}

@ContributesBinding(AppScope::class)
class RealNetpSubscriptionManager @Inject constructor(
    private val subscriptions: Subscriptions,
    private val dispatcherProvider: DispatcherProvider,
) : NetpSubscriptionManager {
    override suspend fun getToken(): String? = withContext(dispatcherProvider.io()) {
        subscriptions.getAccessToken()
    }

    override suspend fun hasValidEntitlement(): Boolean = withContext(dispatcherProvider.io()) {
        subscriptions.hasEntitlement(NETP_ENTITLEMENT)
    }

    companion object {
        private const val NETP_ENTITLEMENT = "Dummy"
    }
}
