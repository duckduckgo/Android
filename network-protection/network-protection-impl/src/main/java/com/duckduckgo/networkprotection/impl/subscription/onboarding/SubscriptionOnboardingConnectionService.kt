/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.networkprotection.impl.subscription.onboarding

import com.duckduckgo.anvil.annotations.ContributesServiceApi
import com.duckduckgo.di.scopes.AppScope
import retrofit2.http.GET

/**
 * Reports the device's currently visible connection as seen by DuckDuckGo. Served from the `@Named("api")`
 * Retrofit base URL (`https://duckduckgo.com`), so [getConnectionInfo] hits `https://duckduckgo.com/connection.json`.
 */
@ContributesServiceApi(AppScope::class)
interface SubscriptionOnboardingConnectionService {
    @GET("/connection.json")
    suspend fun getConnectionInfo(): ConnectionInfo
}

data class ConnectionInfo(
    val ip: String,
    val city: String,
    val country: String,
)
