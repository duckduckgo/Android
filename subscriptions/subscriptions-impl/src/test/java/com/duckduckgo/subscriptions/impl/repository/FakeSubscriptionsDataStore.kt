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

package com.duckduckgo.subscriptions.impl.repository

import com.duckduckgo.subscriptions.impl.store.SubscriptionsDataStore

class FakeSubscriptionsDataStore : SubscriptionsDataStore {

    // Auth
    override var accessToken: String? = null
    override var authToken: String? = null
    override var email: String? = null
    override var externalId: String? = null

    // Subscription
    override var expiresOrRenewsAt: Long? = 0L
    override var platform: String? = null
    override var startedAt: Long? = 0L
    override var status: String? = null
    override var entitlements: String? = null
    override var productId: String? = null
    override fun canUseEncryption(): Boolean = true
}
