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

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.impl.PirRemoteFeatures
import com.duckduckgo.pir.impl.store.PirFreemiumDataStore
import com.duckduckgo.subscriptions.api.Product.PIR
import com.duckduckgo.subscriptions.api.SubscriptionStatus.UNKNOWN
import com.duckduckgo.subscriptions.api.Subscriptions
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.logcat
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealPirFreemium @Inject constructor(
    private val pirRemoteFeatures: PirRemoteFeatures,
    private val subscriptions: Subscriptions,
    private val pirFreemiumDataStore: PirFreemiumDataStore,
    private val pirFreemiumDebugSettings: PirFreemiumDebugSettings,
    private val dispatcherProvider: DispatcherProvider,
) : PirFreemium {

    override suspend fun getPirFreemiumState(): PirFreemiumState = withContext(dispatcherProvider.io()) {
        try {
            resolveState()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(ERROR) { "PIR-FREEMIUM: Failed to resolve freemium state: $e" }
            PirFreemiumState.NOT_ELIGIBLE
        }
    }

    private suspend fun resolveState(): PirFreemiumState = when {
        !isEligible() -> PirFreemiumState.NOT_ELIGIBLE
        pirFreemiumDataStore.firstScanResult == null -> PirFreemiumState.ELIGIBLE
        else -> PirFreemiumState.USED
    }

    private suspend fun isEligible(): Boolean {
        return pirRemoteFeatures.freemium().isEnabled() &&
            subscriptions.getSubscriptionStatus() == UNKNOWN &&
            (pirFreemiumDebugSettings.isEligibilityForced || canPurchasePir())
    }

    private suspend fun canPurchasePir(): Boolean =
        subscriptions.isEligible() && PIR in subscriptions.getPurchasableProducts()
}
