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

import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.subscriptions.api.Product
import com.duckduckgo.subscriptions.api.Subscriptions
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

@ContributesBinding(AppScope::class)
class RealSubscriptions @Inject constructor(
    private val subscriptionsManager: SubscriptionsManager,
    private val privacyProFeature: PrivacyProFeature,
) : Subscriptions {
    override suspend fun getAccessToken(): String? {
        if (!isEnabled()) return null

        return when (val result = subscriptionsManager.getAccessToken()) {
            is AccessToken.Success -> result.accessToken
            is AccessToken.Failure -> null
        }
    }

    override fun getEntitlementStatus(): Flow<List<Product>> {
        return subscriptionsManager.entitlements.map {
            if (!isEnabled()) emptyList() else it
        }
    }

    override suspend fun isEnabled(): Boolean {
        return privacyProFeature.self().isEnabled()
    }
}

@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "privacyPro",
)
interface PrivacyProFeature {
    @Toggle.DefaultValue(false)
    fun self(): Toggle
}
