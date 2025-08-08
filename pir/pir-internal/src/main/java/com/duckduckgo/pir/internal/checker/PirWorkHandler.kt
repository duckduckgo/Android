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

package com.duckduckgo.pir.internal.checker

import android.content.Context
import android.content.Intent
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.impl.PirRemoteFeatures
import com.duckduckgo.pir.internal.optout.PirForegroundOptOutService
import com.duckduckgo.pir.internal.scan.PirForegroundScanService
import com.duckduckgo.pir.internal.scan.PirScanScheduler
import com.duckduckgo.subscriptions.api.Product.PIR
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.api.Subscriptions
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Handler to control PIR work execution and cancellation.
 */
interface PirWorkHandler {
    /**
     * Checks if PIR can run based on remote features and subscription status.
     *
     * @return Flow that emits true if PIR can run, false otherwise.
     */
    suspend fun canRunPir(): Flow<Boolean>

    /**
     * Cancels any ongoing PIR work, including foreground services and scheduled scans.
     */
    fun cancelWork()
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(
    scope = AppScope::class,
    boundType = PirWorkHandler::class,
)
class RealPirWorkHandler @Inject constructor(
    private val pirRemoteFeatures: PirRemoteFeatures,
    private val dispatcherProvider: DispatcherProvider,
    private val subscriptions: Subscriptions,
    private val context: Context,
    private val pirScanScheduler: PirScanScheduler,
) : PirWorkHandler {

    override suspend fun canRunPir(): Flow<Boolean> {
        return withContext(dispatcherProvider.io()) {
            if (pirRemoteFeatures.pirBeta().isEnabled()) {
                // User could have a valid subscription but is not entitled to PIR,
                // so we have to check both
                combine(
                    subscriptions.getSubscriptionStatusFlow()
                        .distinctUntilChanged(),
                    subscriptions.getEntitlementStatus()
                        .map { entitledProducts ->
                            entitledProducts.contains(PIR)
                        }
                        .distinctUntilChanged(),
                ) { subscriptionStatus, hasValidEntitlement ->
                    isPirEnabled(hasValidEntitlement, subscriptionStatus)
                }
            } else {
                flowOf(false)
            }
        }
    }

    override fun cancelWork() {
        // Stop any running foreground services
        context.stopService(Intent(context, PirForegroundScanService::class.java))
        context.stopService(Intent(context, PirForegroundOptOutService::class.java))
        // Cancel any running or scheduled workers
        pirScanScheduler.cancelScheduledScans(context)
    }

    private fun isPirEnabled(
        hasValidEntitlement: Boolean,
        subscriptionStatus: SubscriptionStatus,
    ): Boolean {
        return when (subscriptionStatus) {
            SubscriptionStatus.UNKNOWN,
            SubscriptionStatus.INACTIVE,
            SubscriptionStatus.EXPIRED,
            SubscriptionStatus.WAITING,
            -> {
                false
            }

            SubscriptionStatus.AUTO_RENEWABLE,
            SubscriptionStatus.NOT_AUTO_RENEWABLE,
            SubscriptionStatus.GRACE_PERIOD,
            -> {
                hasValidEntitlement
            }
        }
    }
}
