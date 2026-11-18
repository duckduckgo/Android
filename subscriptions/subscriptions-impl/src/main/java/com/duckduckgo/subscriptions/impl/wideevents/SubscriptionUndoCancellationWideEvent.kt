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

package com.duckduckgo.subscriptions.impl.wideevents

import com.duckduckgo.app.statistics.wideevents.CleanupPolicy.OnProcessStart
import com.duckduckgo.app.statistics.wideevents.FlowStatus
import com.duckduckgo.app.statistics.wideevents.WideEventClient
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.api.SubscriptionStatus.AUTO_RENEWABLE
import com.duckduckgo.subscriptions.api.SubscriptionStatus.NOT_AUTO_RENEWABLE
import com.duckduckgo.subscriptions.impl.SubscriptionsFeature
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

interface SubscriptionUndoCancellationWideEvent {
    suspend fun onFlowStarted(
        planId: String,
        isFreeTrial: Boolean,
        expiresAt: Long,
    )

    suspend fun onEligibilityChecked(canUndoInApp: Boolean)

    suspend fun onBillingFlowInitSuccess()

    suspend fun onBillingFlowInitFailure(error: String)

    suspend fun onBillingFlowPurchaseSuccess()

    suspend fun onBillingFlowPurchaseFailure(error: String)

    suspend fun onUserCancelled()

    suspend fun onConfirmationSuccess()

    suspend fun onConfirmationFailure(reason: String)

    suspend fun onSubscriptionUpdated(
        oldStatus: SubscriptionStatus?,
        newStatus: SubscriptionStatus?,
    )
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class SubscriptionUndoCancellationWideEventImpl @Inject constructor(
    private val wideEventClient: WideEventClient,
    private val subscriptionsFeature: Lazy<SubscriptionsFeature>,
    private val dispatchers: DispatcherProvider,
    private val timeProvider: CurrentTimeProvider,
) : SubscriptionUndoCancellationWideEvent {

    private var cachedFlowId: Long? = null

    override suspend fun onFlowStarted(
        planId: String,
        isFreeTrial: Boolean,
        expiresAt: Long,
    ) {
        if (!isFeatureEnabled()) return

        cachedFlowId = wideEventClient
            .flowStart(
                name = UNDO_CANCELLATION_FEATURE_NAME,
                metadata = mapOf(
                    KEY_PLAN to planId,
                    KEY_FREE_TRIAL to isFreeTrial.toString(),
                    KEY_DAYS_TO_EXPIRY to daysToExpiryBucket(expiresAt),
                ),
                cleanupPolicy = OnProcessStart(ignoreIfIntervalTimeoutPresent = true),
            )
            .getOrNull()
    }

    override suspend fun onEligibilityChecked(canUndoInApp: Boolean) {
        if (!isFeatureEnabled()) return

        getCurrentWideEventId()?.let { wideEventId ->
            wideEventClient.flowStep(
                wideEventId = wideEventId,
                stepName = STEP_CHECK_ELIGIBILITY,
                success = canUndoInApp,
                metadata = mapOf(KEY_METHOD to if (canUndoInApp) METHOD_IN_APP else METHOD_STORE),
            )
            // Store fallback: only a later refresh can confirm it
            if (!canUndoInApp) {
                startActivationInterval(wideEventId)
            }
        }
    }

    override suspend fun onBillingFlowInitSuccess() {
        if (!isFeatureEnabled()) return

        getCurrentWideEventId()?.let { wideEventId ->
            wideEventClient.flowStep(
                wideEventId = wideEventId,
                stepName = STEP_BILLING_FLOW_INIT,
                success = true,
            )
        }
    }

    override suspend fun onBillingFlowInitFailure(error: String) {
        if (!isFeatureEnabled()) return

        getCurrentWideEventId()?.let { wideEventId ->
            wideEventClient.flowStep(
                wideEventId = wideEventId,
                stepName = STEP_BILLING_FLOW_INIT,
                success = false,
            )
            finishFlow(wideEventId, FlowStatus.Failure(reason = error))
        }
    }

    override suspend fun onBillingFlowPurchaseSuccess() {
        if (!isFeatureEnabled()) return

        getCurrentWideEventId()?.let { wideEventId ->
            startActivationInterval(wideEventId)
            wideEventClient.flowStep(
                wideEventId = wideEventId,
                stepName = STEP_BILLING_FLOW_PURCHASE,
                success = true,
            )
        }
    }

    override suspend fun onBillingFlowPurchaseFailure(error: String) {
        if (!isFeatureEnabled()) return

        getCurrentWideEventId()?.let { wideEventId ->
            wideEventClient.flowStep(
                wideEventId = wideEventId,
                stepName = STEP_BILLING_FLOW_PURCHASE,
                success = false,
            )
            finishFlow(wideEventId, FlowStatus.Failure(reason = error))
        }
    }

    override suspend fun onUserCancelled() {
        if (!isFeatureEnabled()) return

        getCurrentWideEventId()?.let { wideEventId ->
            finishFlow(wideEventId, FlowStatus.Cancelled)
        }
    }

    override suspend fun onConfirmationSuccess() {
        if (!isFeatureEnabled()) return

        getCurrentWideEventId()?.let { wideEventId ->
            wideEventClient.intervalEnd(wideEventId = wideEventId, key = KEY_ACTIVATION_LATENCY)
            wideEventClient.flowStep(
                wideEventId = wideEventId,
                stepName = STEP_CONFIRM_PURCHASE,
                success = true,
            )
            finishFlow(wideEventId, FlowStatus.Success)
        }
    }

    override suspend fun onConfirmationFailure(reason: String) {
        if (!isFeatureEnabled()) return

        // Kept open so a later refresh can still confirm the undo
        getCurrentWideEventId()?.let { wideEventId ->
            wideEventClient.flowStep(
                wideEventId = wideEventId,
                stepName = STEP_CONFIRM_PURCHASE,
                success = false,
                metadata = mapOf(KEY_CONFIRM_FAILURE_REASON to reason),
            )
        }
    }

    override suspend fun onSubscriptionUpdated(
        oldStatus: SubscriptionStatus?,
        newStatus: SubscriptionStatus?,
    ) {
        if (!isFeatureEnabled()) return

        if (oldStatus == NOT_AUTO_RENEWABLE && newStatus == AUTO_RENEWABLE) {
            val wideEventId = getCurrentWideEventId() ?: return
            wideEventClient.intervalEnd(wideEventId = wideEventId, key = KEY_ACTIVATION_LATENCY)
            finishFlow(wideEventId, FlowStatus.Success)
        }
    }

    private suspend fun startActivationInterval(wideEventId: Long) {
        wideEventClient.intervalStart(
            wideEventId = wideEventId,
            key = KEY_ACTIVATION_LATENCY,
            timeout = ACTIVATION_TIMEOUT,
        )
    }

    private suspend fun finishFlow(
        wideEventId: Long,
        status: FlowStatus,
    ) {
        wideEventClient.flowFinish(wideEventId = wideEventId, status = status)
        cachedFlowId = null
    }

    private fun daysToExpiryBucket(expiresAt: Long): String {
        val daysToExpiry = (expiresAt - timeProvider.currentTimeMillis()).milliseconds.inWholeDays
        return when {
            daysToExpiry < 1 -> DAYS_TO_EXPIRY_D0
            daysToExpiry <= 3 -> DAYS_TO_EXPIRY_D1_TO_D3
            daysToExpiry <= 7 -> DAYS_TO_EXPIRY_D4_TO_D7
            else -> DAYS_TO_EXPIRY_D8_PLUS
        }
    }

    private suspend fun isFeatureEnabled(): Boolean = withContext(dispatchers.io()) {
        subscriptionsFeature.value.sendSubscriptionUndoCancellationWideEvent().isEnabled()
    }

    private suspend fun getCurrentWideEventId(): Long? {
        if (cachedFlowId == null) {
            cachedFlowId = wideEventClient
                .getFlowIds(UNDO_CANCELLATION_FEATURE_NAME)
                .getOrNull()
                ?.lastOrNull()
        }

        return cachedFlowId
    }

    private companion object {
        const val UNDO_CANCELLATION_FEATURE_NAME = "subscription-undo-cancellation"
        const val KEY_PLAN = "plan"
        const val KEY_FREE_TRIAL = "free_trial"
        const val KEY_METHOD = "method"
        const val KEY_DAYS_TO_EXPIRY = "days_to_expiry"
        const val KEY_ACTIVATION_LATENCY = "activation_latency_ms_bucketed"
        const val KEY_CONFIRM_FAILURE_REASON = "confirm_failure_reason"

        const val METHOD_IN_APP = "in_app"
        const val METHOD_STORE = "store"

        const val DAYS_TO_EXPIRY_D0 = "d0"
        const val DAYS_TO_EXPIRY_D1_TO_D3 = "d1_to_d3"
        const val DAYS_TO_EXPIRY_D4_TO_D7 = "d4_to_d7"
        const val DAYS_TO_EXPIRY_D8_PLUS = "d8_plus"

        val ACTIVATION_TIMEOUT = 10.minutes

        const val STEP_CHECK_ELIGIBILITY = "check_eligibility"
        const val STEP_BILLING_FLOW_INIT = "billing_flow_init"
        const val STEP_BILLING_FLOW_PURCHASE = "billing_flow_purchase"
        const val STEP_CONFIRM_PURCHASE = "confirm_purchase"
    }
}
