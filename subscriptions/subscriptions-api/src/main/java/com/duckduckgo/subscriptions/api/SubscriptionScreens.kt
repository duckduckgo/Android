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

import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams

sealed class SubscriptionScreens {
    data object SubscriptionsSettingsScreenWithEmptyParams : ActivityParams
    data class RestoreSubscriptionScreenWithParams(val isOriginWeb: Boolean = true) : ActivityParams
    data class SubscriptionPurchase(val origin: String? = null) : ActivityParams
}
