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

package com.duckduckgo.pir.impl.freemium

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.impl.pixels.PirPixelSender
import com.duckduckgo.pir.impl.store.PirFreemiumDataStore
import com.duckduckgo.subscriptions.api.SubscriptionStatus.AUTO_RENEWABLE
import com.duckduckgo.subscriptions.api.SubscriptionStatus.GRACE_PERIOD
import com.duckduckgo.subscriptions.api.SubscriptionStatus.NOT_AUTO_RENEWABLE
import com.duckduckgo.subscriptions.api.Subscriptions
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Reports that a user who ran a free scan went on to hold a subscription, from whichever surface they
 * bought it — matching iOS, whose equivalent pixel is guarded only on activation and fires for a
 * purchase made from any entry point.
 */
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
class PirFreemiumUpsellObserver @Inject constructor(
    private val subscriptions: Subscriptions,
    private val pirFreemiumDataStore: PirFreemiumDataStore,
    private val pirPixelSender: PirPixelSender,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : MainProcessLifecycleObserver {

    override fun onCreate(owner: LifecycleOwner) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            subscriptions.getSubscriptionStatusFlow()
                .filter { it in ACTIVE_STATUSES }
                // The pixel is unique per install and the pixel layer owns that bookkeeping, so this
                // re-evaluates the condition on every launch rather than trying to catch the single
                // moment the subscription became active — which a process death could otherwise lose.
                .collect {
                    if (pirFreemiumDataStore.didActivate) {
                        pirPixelSender.reportFreemiumUpsell()
                    }
                }
        }
    }

    private companion object {
        val ACTIVE_STATUSES = setOf(AUTO_RENEWABLE, NOT_AUTO_RENEWABLE, GRACE_PERIOD)
    }
}
