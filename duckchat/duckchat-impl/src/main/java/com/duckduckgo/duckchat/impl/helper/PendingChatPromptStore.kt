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

package com.duckduckgo.duckchat.impl.helper

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

interface PendingSubscriptionEventStore {
    fun store(event: SubscriptionEventData)
    fun consume(): SubscriptionEventData?
    fun clear()
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealPendingSubscriptionEventStore @Inject constructor() : PendingSubscriptionEventStore {

    private val pending = AtomicReference<SubscriptionEventData?>(null)

    override fun store(event: SubscriptionEventData) {
        pending.set(event)
    }

    override fun consume(): SubscriptionEventData? {
        return pending.getAndSet(null)
    }

    override fun clear() {
        pending.set(null)
    }
}
