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

object SubscriptionsConstants {

    // List of subscriptions
    const val BASIC_SUBSCRIPTION = "ddg_privacy_pro"
    val LIST_OF_PRODUCTS = listOf(BASIC_SUBSCRIPTION)

    // List of plans
    const val YEARLY_PLAN = "ddg-privacy-pro-sandbox-yearly-renews-us"
    const val MONTHLY_PLAN = "ddg-privacy-pro-sandbox-monthly-renews-us"

    // List of features
    const val NETP = "vpn"
    const val ITR = "identity-theft-restoration"
    const val PIR = "personal-information-removal"

    // Platform
    const val PLATFORM = "android"

    // Recurrence
    const val MONTHLY = "monthly"
    const val YEARLY = "yearly"

    // URLs
    const val BUY_URL = "https://abrown.duckduckgo.com/subscriptions"
}
