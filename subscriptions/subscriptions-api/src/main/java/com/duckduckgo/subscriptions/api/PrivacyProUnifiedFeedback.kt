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

package com.duckduckgo.subscriptions.api

interface PrivacyProUnifiedFeedback {
    /**
     * @return `true` if for the given source, we should allow the usage of unified feedback flow or `false` otherwise
     */
    suspend fun shouldUseUnifiedFeedback(source: PrivacyProFeedbackSource): Boolean

    enum class PrivacyProFeedbackSource {
        DDG_SETTINGS,
        SUBSCRIPTION_SETTINGS,
        VPN_MANAGEMENT,
        VPN_EXCLUDED_APPS,
        UNKNOWN,
    }
}
