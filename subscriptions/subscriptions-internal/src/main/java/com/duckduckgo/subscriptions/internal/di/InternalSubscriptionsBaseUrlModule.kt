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

package com.duckduckgo.subscriptions.internal.di

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.subscriptions.impl.internal.SubscriptionsBaseUrl
import com.duckduckgo.subscriptions.impl.internal.SubscriptionsBaseUrlModule
import com.duckduckgo.subscriptions.internal.SubscriptionsInternalStore
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn

@Module
@ContributesTo(
    scope = AppScope::class,
    replaces = [SubscriptionsBaseUrlModule::class],
)
class InternalSubscriptionsBaseUrlModule {
    @Provides
    @SingleInstanceIn(AppScope::class)
    fun provideSubscriptionsBaseUrl(
        store: SubscriptionsInternalStore,
    ): SubscriptionsBaseUrl {
        return object : SubscriptionsBaseUrl {
            val defaultUrl = "http://duckduckgo.com/subscriptions"
            override val subscriptionsBaseUrl: String = store.getBaseUrl() ?: defaultUrl
        }
    }
}
