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

package com.duckduckgo.subscriptions.api

interface SubscriptionPurchaseSuccessPlugin {
    /**
     * Invoked once a subscription purchase has completed and the subscription is confirmed active.
     *
     * Called for every purchase, not only those originating from a particular surface, so an
     * implementation that cares about provenance must gate on its own state.
     */
    suspend fun onSubscriptionPurchaseSuccess()
}
