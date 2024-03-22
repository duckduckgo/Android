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

package com.duckduckgo.subscriptions.api

import kotlinx.coroutines.flow.Flow

interface Subscriptions {

    /**
     * This method returns a [String] with the access token for the authenticated user or [null] if it doesn't exist
     * or any errors arise.
     * @return [String]
     */
    suspend fun getAccessToken(): String?

    /**
     * This method returns a [true] if a  given [product] can be found in the entitlements list or [false] otherwise
     * @return [Boolean]
     */
    fun getEntitlementStatus(): Flow<List<Product>>

    /**
     * @return `true` if the Privacy Pro product is enabled and live, `false` otherwise
     */
    suspend fun isEnabled(): Boolean

    /**
     * @return `true` if the Privacy Pro product is available for the user, `false` otherwise
     */
    suspend fun isEligible(): Boolean

    /**
     * @return `SubscriptionStatus` with the current subscription status
     */
    suspend fun getSubscriptionStatus(): SubscriptionStatus
}

enum class Product(val value: String) {
    NetP("Network Protection"),
    ITR("Identity Theft Restoration"),
    PIR("Data Broker Protection"),
}

enum class SubscriptionStatus(val statusName: String) {
    AUTO_RENEWABLE("Auto-Renewable"),
    NOT_AUTO_RENEWABLE("Not Auto-Renewable"),
    GRACE_PERIOD("Grace Period"),
    INACTIVE("Inactive"),
    EXPIRED("Expired"),
    UNKNOWN("Unknown"),
    WAITING("Waiting"),
}
