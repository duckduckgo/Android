/*
 * Copyright (c) 2025 DuckDuckGo
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

interface SubscriptionRebrandingFeatureToggle {
    /**
     * This method is safe to call from the main thread.
     * @return true if the subscription rebranding feature is enabled, false otherwise
     */
    @Deprecated(
        message = "This method is kept temporarily for legacy use in one location. It will be removed by November 2025 as the rebranding feature has been retired.",
    )
    fun isSubscriptionRebrandingEnabled(): Boolean
}
