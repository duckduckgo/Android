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

package com.duckduckgo.pir.impl.checker

import android.content.Context
import android.content.Intent
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.impl.PirRemoteFeatures
import com.duckduckgo.pir.impl.notifications.PirNotificationManager
import com.duckduckgo.pir.impl.optout.PirForegroundOptOutService
import com.duckduckgo.pir.impl.scan.PirForegroundScanService
import com.duckduckgo.pir.impl.scan.PirScanScheduler
import com.duckduckgo.pir.impl.store.PirRepository
import com.duckduckgo.pir.impl.wideevents.PirScanWideEvent
import com.duckduckgo.pir.impl.wideevents.PirScanWideEvent.CancellationReason
import com.duckduckgo.subscriptions.api.Product.PIR
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.api.Subscriptions
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Handler to control PIR work execution and cancellation.
 */
interface PirWorkHandler {
    /**
     * Checks if PIR can run based on remote features, subscription status, entitlement and
     * repository availability.
     *
     * @return Flow that emits [PirEligibility.Enabled] when PIR can run, or
     * [PirEligibility.Disabled] (carrying the failing [DisabledReason]) otherwise.
     */
    suspend fun canRunPir(): Flow<PirEligibility>

    /**
     * Cancels any ongoing PIR work, including foreground services and scheduled scans.
     *
     * @param reason why the work is being cancelled, used to attribute any in-flight scan run in
     * the wide event.
     */
    suspend fun cancelWork(reason: CancellationReason)
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
    private val pirRepository: PirRepository,
    private val pirNotificationManager: PirNotificationManager,
    private val pirScanWideEvent: PirScanWideEvent,
) : PirWorkHandler {

    override suspend fun canRunPir(): Flow<PirEligibility> {
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
                    resolveEligibility(subscriptionStatus, hasValidEntitlement)
                }
            } else {
                flowOf(PirEligibility.Disabled(DisabledReason.FEATURE_DISABLED))
            }
        }
    }

    override suspend fun cancelWork(reason: CancellationReason) {
        // Finalize any in-flight wide-event run as Cancelled
        pirScanWideEvent.onWorkCancelled(reason)
        // Stop any running foreground services
        context.stopService(Intent(context, PirForegroundScanService::class.java))
        context.stopService(Intent(context, PirForegroundOptOutService::class.java))
        // Cancel any running or scheduled workers
        pirScanScheduler.cancelScheduledScans(context)
        pirNotificationManager.cancelNotifications()
    }

    private suspend fun resolveEligibility(
        subscriptionStatus: SubscriptionStatus,
        hasValidEntitlement: Boolean,
    ): PirEligibility {
        val subscriptionActive = when (subscriptionStatus) {
            SubscriptionStatus.UNKNOWN,
            SubscriptionStatus.INACTIVE,
            SubscriptionStatus.EXPIRED,
            SubscriptionStatus.WAITING,
            -> false

            SubscriptionStatus.AUTO_RENEWABLE,
            SubscriptionStatus.NOT_AUTO_RENEWABLE,
            SubscriptionStatus.GRACE_PERIOD,
            -> true
        }

        return when {
            !subscriptionActive -> PirEligibility.Disabled(DisabledReason.SUBSCRIPTION_EXPIRED)
            !hasValidEntitlement -> PirEligibility.Disabled(DisabledReason.ENTITLEMENT_LOST)
            !pirRepository.isRepositoryAvailable() -> PirEligibility.Disabled(DisabledReason.REPOSITORY_UNAVAILABLE)
            else -> PirEligibility.Enabled
        }
    }
}
