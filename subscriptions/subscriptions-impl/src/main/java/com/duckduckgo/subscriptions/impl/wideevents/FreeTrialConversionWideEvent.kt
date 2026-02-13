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

package com.duckduckgo.subscriptions.impl.wideevents

import com.duckduckgo.app.statistics.wideevents.CleanupPolicy.OnTimeout
import com.duckduckgo.app.statistics.wideevents.FlowStatus
import com.duckduckgo.app.statistics.wideevents.WideEventClient
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.subscriptions.impl.PrivacyProFeature
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixelSender
import com.duckduckgo.subscriptions.impl.repository.AuthRepository
import com.squareup.anvil.annotations.ContributesBinding
import dagger.Lazy
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import java.time.Duration
import java.util.concurrent.TimeUnit
import javax.inject.Inject

interface FreeTrialConversionWideEvent {
    suspend fun onFreeTrialStarted(productId: String)

    suspend fun onVpnActivatedSuccessfully()

    /**
     * Called when subscription data is refreshed to detect free trial end.
     * @param wasFreeTrial Whether the previous subscription state was a free trial
     * @param isFreeTrial Whether the current subscription state is a free trial
     * @param isSubscriptionActive Whether the subscription is currently active
     */
    suspend fun onSubscriptionRefreshed(
        wasFreeTrial: Boolean,
        isFreeTrial: Boolean,
        isSubscriptionActive: Boolean,
    )
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class FreeTrialConversionWideEventImpl @Inject constructor(
    private val wideEventClient: WideEventClient,
    private val privacyProFeature: Lazy<PrivacyProFeature>,
    private val authRepository: AuthRepository,
    private val timeProvider: CurrentTimeProvider,
    private val dispatchers: DispatcherProvider,
    private val pixelSender: SubscriptionPixelSender,
) : FreeTrialConversionWideEvent {

    private var cachedFlowId: Long? = null
    private var vpnActivationStepRecorded: Boolean = false

    override suspend fun onFreeTrialStarted(productId: String) {
        pixelSender.reportFreeTrialStart()

        if (!isFeatureEnabled()) return

        // Skip if there's already an active flow to avoid restarting on subsequent updates
        if (getCurrentWideEventId() != null) {
            return
        }

        vpnActivationStepRecorded = false

        cachedFlowId = wideEventClient
            .flowStart(
                name = FREE_TRIAL_CONVERSION_FEATURE_NAME,
                metadata = mapOf(
                    KEY_FREE_TRIAL_PLAN to productId,
                ),
                cleanupPolicy = OnTimeout(
                    duration = Duration.ofDays(FREE_TRIAL_TIMEOUT_DAYS),
                    flowStatus = FlowStatus.Failure(FAILURE_REASON_TIMEOUT),
                ),
            )
            .getOrNull()
    }

    override suspend fun onVpnActivatedSuccessfully() {
        val subscription = authRepository.getSubscription() ?: return

        // Only proceed if subscription is in free trial
        if (!authRepository.isFreeTrialActive()) {
            return
        }

        val subscriptionStartedAt = subscription.startedAt
        val currentTime = timeProvider.currentTimeMillis()
        val daysSinceStart = TimeUnit.MILLISECONDS.toDays(currentTime - subscriptionStartedAt)

        val activationDay = if (daysSinceStart < 1) {
            STEP_VPN_ACTIVATED_D1
        } else {
            STEP_VPN_ACTIVATED_D2_TO_D7
        }

        // Fire pixel for VPN activation during free trial (independent of wide event feature flag)
        pixelSender.reportFreeTrialVpnActivation(activationDay, subscription.platform)

        // Wide event logic (gated by feature flag)
        if (!isFeatureEnabled()) return
        if (vpnActivationStepRecorded) return

        val wideEventId = getCurrentWideEventId() ?: return

        wideEventClient.flowStep(
            wideEventId = wideEventId,
            stepName = activationDay,
            success = true,
        )

        vpnActivationStepRecorded = true
    }

    override suspend fun onSubscriptionRefreshed(
        wasFreeTrial: Boolean,
        isFreeTrial: Boolean,
        isSubscriptionActive: Boolean,
    ) {
        if (!isFeatureEnabled()) return

        val wideEventId = getCurrentWideEventId() ?: return

        when {
            // Free trial converted to paid subscription (was free trial, still active, but no longer free trial)
            wasFreeTrial && isSubscriptionActive && !isFreeTrial -> {
                wideEventClient.flowFinish(
                    wideEventId = wideEventId,
                    status = FlowStatus.Success,
                )
                cachedFlowId = null
                vpnActivationStepRecorded = false
            }

            // Free trial expired (was free trial, no longer active)
            wasFreeTrial && !isSubscriptionActive -> {
                wideEventClient.flowFinish(
                    wideEventId = wideEventId,
                    status = FlowStatus.Failure(FAILURE_REASON_EXPIRED),
                )
                cachedFlowId = null
                vpnActivationStepRecorded = false
            }
        }
    }

    private suspend fun isFeatureEnabled(): Boolean = withContext(dispatchers.io()) {
        privacyProFeature.get().sendFreeTrialConversionWideEvent().isEnabled()
    }

    private suspend fun getCurrentWideEventId(): Long? {
        if (cachedFlowId == null) {
            cachedFlowId = wideEventClient
                .getFlowIds(FREE_TRIAL_CONVERSION_FEATURE_NAME)
                .getOrNull()
                ?.lastOrNull()
        }

        return cachedFlowId
    }

    private companion object {
        const val FREE_TRIAL_CONVERSION_FEATURE_NAME = "free-trial-conversion"

        // Timeout duration - flow persists across app closures for up to 8 days
        const val FREE_TRIAL_TIMEOUT_DAYS = 8L

        // Metadata keys
        const val KEY_FREE_TRIAL_PLAN = "free_trial_plan"

        // Failure reasons
        const val FAILURE_REASON_EXPIRED = "expired"
        const val FAILURE_REASON_TIMEOUT = "timeout"

        // Steps
        const val STEP_VPN_ACTIVATED_D1 = "vpn_activated_d1"
        const val STEP_VPN_ACTIVATED_D2_TO_D7 = "vpn_activated_d2_to_d7"
    }
}
