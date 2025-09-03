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

package com.duckduckgo.subscriptions.impl

import android.content.Context
import android.net.Uri
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.subscriptions.api.Product
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.api.SubscriptionStatus.UNKNOWN
import com.duckduckgo.subscriptions.api.Subscriptions
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@ContributesBinding(AppScope::class)
class SubscriptionsDummy @Inject constructor() : Subscriptions {
    override suspend fun isSignedIn(): Boolean = false

    override suspend fun getAccessToken(): String? = null

    override fun getEntitlementStatus(): Flow<List<Product>> = flowOf(emptyList())

    override suspend fun isEligible(): Boolean = false

    override fun getSubscriptionStatusFlow(): Flow<SubscriptionStatus> = flowOf(UNKNOWN)

    override suspend fun getSubscriptionStatus(): SubscriptionStatus = UNKNOWN

    override suspend fun getAvailableProducts(): Set<Product> = emptySet()

    override fun shouldLaunchPrivacyProForUrl(url: String): Boolean = false

    override fun launchPrivacyPro(
        context: Context,
        uri: Uri?,
    ) {
        // no-op
    }

    override fun isPrivacyProUrl(uri: Uri): Boolean = false

    override suspend fun isFreeTrialEligible(): Boolean = false
}
