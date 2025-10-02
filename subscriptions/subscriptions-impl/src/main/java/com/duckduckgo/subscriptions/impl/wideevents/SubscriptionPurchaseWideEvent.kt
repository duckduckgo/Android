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

import com.duckduckgo.app.statistics.wideevents.CleanupPolicy.OnProcessStart
import com.duckduckgo.app.statistics.wideevents.FlowStatus
import com.duckduckgo.app.statistics.wideevents.WideEventClient
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.impl.PrivacyProFeature
import com.duckduckgo.subscriptions.impl.repository.isActive
import com.squareup.anvil.annotations.ContributesBinding
import dagger.Lazy
import dagger.SingleInstanceIn
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.withContext

interface SubscriptionPurchaseWideEvent {
    suspend fun onPurchaseFlowStarted(
        subscriptionIdentifier: String,
        freeTrialEligible: Boolean,
        origin: String?,
    )

    suspend fun onSubscriptionRefreshSuccess()

    suspend fun onSubscriptionRefreshFailure(e: Exception)

    suspend fun onExistingSubscriptionRestored()

    suspend fun onAccountCreationStarted()

    suspend fun onAccountCreationSuccess()

    suspend fun onAccountCreationFailure(e: Exception)

    suspend fun onBillingFlowInitSuccess()

    suspend fun onBillingFlowInitFailure(error: String)

    suspend fun onBillingFlowPurchaseSuccess()

    suspend fun onBillingFlowPurchaseFailure(error: String)

    suspend fun onPurchaseConfirmationSuccess()

    suspend fun onSubscriptionUpdated(
        oldStatus: SubscriptionStatus?,
        newStatus: SubscriptionStatus?,
    )

    suspend fun onPurchaseCancelledByUser()

    suspend fun onPurchaseFailed(error: String)
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class SubscriptionPurchaseWideEventImpl @Inject constructor(
    private val wideEventClient: WideEventClient,
    private val privacyProFeature: Lazy<PrivacyProFeature>,
    private val dispatchers: DispatcherProvider,
) : SubscriptionPurchaseWideEvent {

    private var cachedFlowId: Long? = null

    override suspend fun onPurchaseFlowStarted(
        subscriptionIdentifier: String,
        freeTrialEligible: Boolean,
        origin: String?,
    ) {
        if (!isFeatureEnabled()) return

        getCurrentWideEventId()?.let { wideEventId ->
            wideEventClient.flowFinish(
                wideEventId = wideEventId,
                status = FlowStatus.Unknown,
            )
        }

        cachedFlowId = wideEventClient
            .flowStart(
                name = "subscription-purchase",
                flowEntryPoint = origin,
                metadata = mapOf(
                    KEY_SUBSCRIPTION_IDENTIFIER to subscriptionIdentifier,
                    KEY_FREE_TRIAL_ELIGIBLE to freeTrialEligible.toString(),
                ),
                cleanupPolicy = OnProcessStart(ignoreIfIntervalTimeoutPresent = true),
            )
            .getOrNull()
    }

    override suspend fun onSubscriptionRefreshSuccess() {
        if (!isFeatureEnabled()) return
        val wideEventId = getCurrentWideEventId() ?: return

        wideEventClient.flowStep(
            wideEventId = wideEventId,
            stepName = STEP_REFRESH_SUBSCRIPTION,
            success = true,
        )
    }

    override suspend fun onSubscriptionRefreshFailure(e: Exception) {
        if (!isFeatureEnabled()) return
        val wideEventId = getCurrentWideEventId() ?: return

        wideEventClient.flowStep(
            wideEventId = wideEventId,
            stepName = STEP_REFRESH_SUBSCRIPTION,
            success = false,
        )

        wideEventClient.flowFinish(
            wideEventId = wideEventId,
            status = FlowStatus.Failure(reason = e.toErrorString()),
        )
    }

    override suspend fun onAccountCreationStarted() {
        if (!isFeatureEnabled()) return
        val wideEventId = getCurrentWideEventId() ?: return

        wideEventClient.intervalStart(
            wideEventId = wideEventId,
            key = KEY_ACCOUNT_CREATION_LATENCY_MS_BUCKETED,
        )
    }

    override suspend fun onAccountCreationSuccess() {
        if (!isFeatureEnabled()) return
        val wideEventId = getCurrentWideEventId() ?: return

        wideEventClient.intervalEnd(
            wideEventId = wideEventId,
            key = KEY_ACCOUNT_CREATION_LATENCY_MS_BUCKETED,
        )

        wideEventClient.flowStep(
            wideEventId = wideEventId,
            stepName = STEP_CREATE_ACCOUNT,
            success = true,
        )
    }

    override suspend fun onAccountCreationFailure(e: Exception) {
        if (!isFeatureEnabled()) return
        val wideEventId = getCurrentWideEventId() ?: return

        wideEventClient.flowStep(
            wideEventId = wideEventId,
            stepName = STEP_CREATE_ACCOUNT,
            success = false,
        )

        wideEventClient.flowFinish(
            wideEventId = wideEventId,
            status = FlowStatus.Failure(e.toErrorString()),
        )
    }

    override suspend fun onBillingFlowInitSuccess() {
        if (!isFeatureEnabled()) return
        val wideEventId = getCurrentWideEventId() ?: return

        wideEventClient.flowStep(
            wideEventId = wideEventId,
            stepName = STEP_BILLING_FLOW_INIT,
            success = true,
        )
    }

    override suspend fun onBillingFlowInitFailure(error: String) {
        if (!isFeatureEnabled()) return
        val wideEventId = getCurrentWideEventId() ?: return

        wideEventClient.flowStep(
            wideEventId = wideEventId,
            stepName = STEP_BILLING_FLOW_INIT,
            success = false,
        )

        wideEventClient.flowFinish(
            wideEventId = wideEventId,
            status = FlowStatus.Failure(reason = error),
        )
    }

    override suspend fun onBillingFlowPurchaseSuccess() {
        if (!isFeatureEnabled()) return
        val wideEventId = getCurrentWideEventId() ?: return

        wideEventClient.intervalStart(
            wideEventId = wideEventId,
            key = KEY_ACTIVATION_LATENCY_MS_BUCKETED,
            timeout = Duration.ofHours(4),
        )

        wideEventClient.flowStep(
            wideEventId = wideEventId,
            stepName = STEP_BILLING_FLOW_PURCHASE,
            success = true,
        )
    }

    override suspend fun onBillingFlowPurchaseFailure(error: String) {
        if (!isFeatureEnabled()) return
        val wideEventId = getCurrentWideEventId() ?: return

        wideEventClient.flowStep(
            wideEventId = wideEventId,
            stepName = STEP_BILLING_FLOW_PURCHASE,
            success = false,
        )

        wideEventClient.flowFinish(
            wideEventId = wideEventId,
            status = FlowStatus.Failure(reason = error),
        )
    }

    override suspend fun onPurchaseConfirmationSuccess() {
        if (!isFeatureEnabled()) return
        val wideEventId = getCurrentWideEventId() ?: return

        wideEventClient.flowStep(
            wideEventId = wideEventId,
            stepName = STEP_CONFIRM_PURCHASE,
            success = true,
        )

        wideEventClient.intervalEnd(
            wideEventId = wideEventId,
            key = KEY_ACTIVATION_LATENCY_MS_BUCKETED,
        )

        wideEventClient.flowFinish(
            wideEventId = wideEventId,
            status = FlowStatus.Success,
        )

        cachedFlowId = null
    }

    override suspend fun onSubscriptionUpdated(
        oldStatus: SubscriptionStatus?,
        newStatus: SubscriptionStatus?,
    ) {
        if (!isFeatureEnabled()) return

        if (oldStatus == SubscriptionStatus.WAITING && newStatus?.isActive() == true) {
            val wideEventId = getCurrentWideEventId() ?: return
            wideEventClient.flowFinish(wideEventId = wideEventId, status = FlowStatus.Success)
            cachedFlowId = null
        }
    }

    override suspend fun onPurchaseCancelledByUser() {
        if (!isFeatureEnabled()) return
        val wideEventId = getCurrentWideEventId() ?: return

        wideEventClient.flowFinish(wideEventId = wideEventId, status = FlowStatus.Cancelled)
        cachedFlowId = null
    }

    override suspend fun onExistingSubscriptionRestored() {
        if (!isFeatureEnabled()) return

        getCurrentWideEventId()?.let { wideEventClient.flowAbort(wideEventId = it) }
        cachedFlowId = null
    }

    override suspend fun onPurchaseFailed(error: String) {
        if (!isFeatureEnabled()) return

        val wideEventId = getCurrentWideEventId() ?: return

        wideEventClient.flowFinish(
            wideEventId = wideEventId,
            status = FlowStatus.Failure(reason = error),
        )

        cachedFlowId = null
    }

    private suspend fun isFeatureEnabled(): Boolean = withContext(dispatchers.io()) {
        privacyProFeature.get().sendSubscriptionPurchaseWideEvent().isEnabled()
    }

    private suspend fun getCurrentWideEventId(): Long? {
        if (cachedFlowId == null) {
            cachedFlowId = wideEventClient
                .getFlowIds(SUBSCRIPTION_PURCHASE_FEATURE_NAME)
                .getOrNull()
                ?.lastOrNull()
        }

        return cachedFlowId
    }

    private companion object {
        const val SUBSCRIPTION_PURCHASE_FEATURE_NAME = "subscription-purchase"

        // wide event metadata keys
        const val KEY_ACCOUNT_CREATION_LATENCY_MS_BUCKETED = "creation_latency_ms_bucketed"
        const val KEY_ACTIVATION_LATENCY_MS_BUCKETED = "activation_latency_ms_bucketed"
        const val KEY_FREE_TRIAL_ELIGIBLE = "free_trial_eligible"
        const val KEY_SUBSCRIPTION_IDENTIFIER = "subscription_identifier"

        const val STEP_REFRESH_SUBSCRIPTION = "refresh_subscription"
        const val STEP_CREATE_ACCOUNT = "create_account"
        const val STEP_BILLING_FLOW_INIT = "billing_flow_init"
        const val STEP_BILLING_FLOW_PURCHASE = "billing_flow_purchase"
        const val STEP_CONFIRM_PURCHASE = "confirm_purchase"
    }
}

private fun Exception.toErrorString(): String =
    listOf(javaClass.simpleName, message).joinToString(": ")
