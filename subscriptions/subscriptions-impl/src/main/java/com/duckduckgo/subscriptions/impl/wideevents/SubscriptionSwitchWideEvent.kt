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

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.wideevents.CleanupPolicy.OnProcessStart
import com.duckduckgo.app.statistics.wideevents.FlowStatus
import com.duckduckgo.app.statistics.wideevents.WideEventClient
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.impl.PrivacyProFeature
import com.duckduckgo.subscriptions.impl.SubscriptionTier
import com.duckduckgo.subscriptions.impl.SubscriptionTier.PLUS
import com.duckduckgo.subscriptions.impl.SubscriptionTier.PRO
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.LIST_MONTHLY_PLUS_PLANS
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.LIST_MONTHLY_PRO_PLANS
import com.duckduckgo.subscriptions.impl.repository.isActive
import com.squareup.anvil.annotations.ContributesBinding
import dagger.Lazy
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import javax.inject.Inject

interface SubscriptionSwitchWideEvent {
    suspend fun onSwitchFlowStarted(
        context: String?,
        fromPlan: String,
        toPlan: String,
    )

    suspend fun onCurrentSubscriptionValidated()

    suspend fun onValidationFailure(error: String)

    suspend fun onTargetPlanRetrieved()

    suspend fun onTargetPlanRetrievalFailure()

    suspend fun onBillingFlowInitSuccess()

    suspend fun onBillingFlowInitFailure(error: String)

    suspend fun onUserCancelled()

    suspend fun onPlayBillingSwitchSuccess()

    suspend fun onSwitchConfirmationSuccess()

    suspend fun onSubscriptionUpdated(
        oldStatus: SubscriptionStatus?,
        newStatus: SubscriptionStatus?,
    )

    fun onUIRefreshed()

    suspend fun onSwitchFailed(error: String)
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class SubscriptionSwitchWideEventImpl @Inject constructor(
    private val wideEventClient: WideEventClient,
    private val privacyProFeature: Lazy<PrivacyProFeature>,
    private val dispatchers: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : SubscriptionSwitchWideEvent {

    private var cachedFlowId: Long? = null

    override suspend fun onSwitchFlowStarted(
        context: String?,
        fromPlan: String,
        toPlan: String,
    ) {
        if (!isFeatureEnabled()) return

        val fromTier = SubscriptionTier.fromPlanId(fromPlan)
        val toTier = SubscriptionTier.fromPlanId(toPlan)
        val switchType = deriveSwitchBillingType(fromPlan, toPlan)
        val changeType = deriveChangeType(fromTier, toTier)

        cachedFlowId = wideEventClient
            .flowStart(
                name = SUBSCRIPTION_PLAN_CHANGE_FEATURE_NAME,
                flowEntryPoint = context,
                metadata = mapOf(
                    KEY_FROM_PLAN to fromPlan,
                    KEY_TO_PLAN to toPlan,
                    KEY_FROM_TIER to fromTier.value,
                    KEY_TO_TIER to toTier.value,
                    KEY_SWITCH_TYPE to switchType,
                    KEY_CHANGE_TYPE to changeType,
                ),
                cleanupPolicy = OnProcessStart(ignoreIfIntervalTimeoutPresent = true),
            )
            .getOrNull()
    }

    private fun deriveSwitchBillingType(
        fromPlan: String,
        toPlan: String,
    ): String {
        val fromIsMonthly = fromPlan in LIST_MONTHLY_PLUS_PLANS + LIST_MONTHLY_PRO_PLANS
        val toIsMonthly = toPlan in LIST_MONTHLY_PLUS_PLANS + LIST_MONTHLY_PRO_PLANS
        return when {
            fromIsMonthly && toIsMonthly -> SWITCH_TYPE_NONE
            !fromIsMonthly && !toIsMonthly -> SWITCH_TYPE_NONE
            toIsMonthly -> SWITCH_TYPE_DOWNGRADE
            else -> SWITCH_TYPE_UPGRADE
        }
    }

    private fun deriveChangeType(
        fromTier: SubscriptionTier,
        toTier: SubscriptionTier,
    ): String {
        if (fromTier == SubscriptionTier.UNKNOWN || toTier == SubscriptionTier.UNKNOWN) {
            return CHANGE_TYPE_CROSSGRADE
        }
        return when {
            fromTier == PLUS && toTier == PRO -> CHANGE_TYPE_UPGRADE
            fromTier == PRO && toTier == PLUS -> CHANGE_TYPE_DOWNGRADE
            else -> CHANGE_TYPE_CROSSGRADE
        }
    }

    override suspend fun onCurrentSubscriptionValidated() {
        if (!isFeatureEnabled()) return

        getCurrentWideEventId()?.let { wideEventId ->
            wideEventClient.flowStep(
                wideEventId = wideEventId,
                stepName = STEP_VALIDATE_CURRENT_SUBSCRIPTION,
                success = true,
            )
        }
    }

    override suspend fun onValidationFailure(error: String) {
        if (!isFeatureEnabled()) return

        getCurrentWideEventId()?.let { wideEventId ->
            wideEventClient.flowStep(
                wideEventId = wideEventId,
                stepName = STEP_VALIDATE_CURRENT_SUBSCRIPTION,
                success = false,
            )
            wideEventClient.flowFinish(
                wideEventId = wideEventId,
                status = FlowStatus.Failure(reason = error),
            )
            cachedFlowId = null
        }
    }

    override suspend fun onTargetPlanRetrieved() {
        if (!isFeatureEnabled()) return

        getCurrentWideEventId()?.let { wideEventId ->
            wideEventClient.flowStep(
                wideEventId = wideEventId,
                stepName = STEP_RETRIEVE_TARGET_PLAN,
                success = true,
            )
        }
    }

    override suspend fun onTargetPlanRetrievalFailure() {
        if (!isFeatureEnabled()) return

        getCurrentWideEventId()?.let { wideEventId ->
            wideEventClient.flowStep(
                wideEventId = wideEventId,
                stepName = STEP_RETRIEVE_TARGET_PLAN,
                success = false,
            )
            wideEventClient.flowFinish(
                wideEventId = wideEventId,
                status = FlowStatus.Failure(reason = "Target plan not found"),
            )
            cachedFlowId = null
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
            wideEventClient.flowFinish(
                wideEventId = wideEventId,
                status = FlowStatus.Failure(reason = error),
            )
            cachedFlowId = null
        }
    }

    override suspend fun onUserCancelled() {
        if (!isFeatureEnabled()) return

        getCurrentWideEventId()?.let { wideEventId ->
            wideEventClient.flowFinish(
                wideEventId = wideEventId,
                status = FlowStatus.Cancelled,
            )
            cachedFlowId = null
        }
    }

    override suspend fun onPlayBillingSwitchSuccess() {
        if (!isFeatureEnabled()) return

        getCurrentWideEventId()?.let { wideEventId ->
            wideEventClient.intervalStart(
                wideEventId = wideEventId,
                key = KEY_ACTIVATION_LATENCY,
                timeout = Duration.ofMinutes(10),
            )
            wideEventClient.flowStep(
                wideEventId = wideEventId,
                stepName = STEP_BILLING_FLOW_SWITCH,
                success = true,
            )
        }
    }

    override suspend fun onSwitchConfirmationSuccess() {
        if (!isFeatureEnabled()) return

        getCurrentWideEventId()?.let { wideEventId ->
            wideEventClient.intervalEnd(
                wideEventId = wideEventId,
                key = KEY_ACTIVATION_LATENCY,
            )
            wideEventClient.flowStep(
                wideEventId = wideEventId,
                stepName = STEP_CONFIRM_SWITCH,
                success = true,
            )
            wideEventClient.flowFinish(
                wideEventId = wideEventId,
                status = FlowStatus.Success,
            )
            cachedFlowId = null
        }
    }

    override suspend fun onSubscriptionUpdated(
        oldStatus: SubscriptionStatus?,
        newStatus: SubscriptionStatus?,
    ) {
        if (!isFeatureEnabled()) return

        if (oldStatus == SubscriptionStatus.WAITING && newStatus?.isActive() == true) {
            val wideEventId = getCurrentWideEventId() ?: return
            wideEventClient.intervalEnd(wideEventId = wideEventId, key = KEY_ACTIVATION_LATENCY)
            wideEventClient.flowFinish(wideEventId = wideEventId, status = FlowStatus.Success)
            cachedFlowId = null
        }
    }

    override fun onUIRefreshed() {
        appCoroutineScope.launch(dispatchers.io()) {
            if (!isFeatureEnabled()) return@launch

            getCurrentWideEventId()?.let { wideEventId ->
                wideEventClient.flowStep(
                    wideEventId = wideEventId,
                    stepName = STEP_UI_REFRESH,
                )
            }
        }
    }

    override suspend fun onSwitchFailed(error: String) {
        if (!isFeatureEnabled()) return

        getCurrentWideEventId()?.let { wideEventId ->
            wideEventClient.flowFinish(
                wideEventId = wideEventId,
                status = FlowStatus.Failure(reason = error),
            )
            cachedFlowId = null
        }
    }

    private suspend fun isFeatureEnabled(): Boolean = withContext(dispatchers.io()) {
        privacyProFeature.get().sendSubscriptionSwitchWideEvent().isEnabled()
    }

    private suspend fun getCurrentWideEventId(): Long? {
        if (cachedFlowId == null) {
            cachedFlowId = wideEventClient
                .getFlowIds(SUBSCRIPTION_PLAN_CHANGE_FEATURE_NAME)
                .getOrNull()
                ?.lastOrNull()
        }

        return cachedFlowId
    }

    private companion object {
        const val SUBSCRIPTION_PLAN_CHANGE_FEATURE_NAME = "subscription-plan-change"
        const val KEY_FROM_PLAN = "from_plan"
        const val KEY_TO_PLAN = "to_plan"
        const val KEY_FROM_TIER = "from_tier"
        const val KEY_TO_TIER = "to_tier"
        const val KEY_SWITCH_TYPE = "billing_cycle_switch_type"
        const val KEY_CHANGE_TYPE = "tier_change_type"
        const val KEY_ACTIVATION_LATENCY = "activation_latency_ms_bucketed"

        // Switch types
        const val SWITCH_TYPE_UPGRADE = "upgrade"
        const val SWITCH_TYPE_DOWNGRADE = "downgrade"
        const val SWITCH_TYPE_NONE = "none"
        const val CHANGE_TYPE_CROSSGRADE = "crossgrade"
        const val CHANGE_TYPE_UPGRADE = "upgrade"
        const val CHANGE_TYPE_DOWNGRADE = "downgrade"

        // Steps
        const val STEP_VALIDATE_CURRENT_SUBSCRIPTION = "validate_current_subscription"
        const val STEP_RETRIEVE_TARGET_PLAN = "retrieve_target_plan"
        const val STEP_BILLING_FLOW_INIT = "billing_flow_init"
        const val STEP_BILLING_FLOW_SWITCH = "billing_flow_switch"
        const val STEP_CONFIRM_SWITCH = "confirm_switch"
        const val STEP_UI_REFRESH = "ui_refresh"
    }
}
