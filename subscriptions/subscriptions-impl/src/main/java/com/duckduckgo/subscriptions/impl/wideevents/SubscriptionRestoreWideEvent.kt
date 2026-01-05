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

import androidx.core.net.toUri
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.wideevents.CleanupPolicy
import com.duckduckgo.app.statistics.wideevents.FlowStatus
import com.duckduckgo.app.statistics.wideevents.WideEventClient
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.subscriptions.impl.PrivacyProFeature
import com.squareup.anvil.annotations.ContributesBinding
import dagger.Lazy
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface SubscriptionRestoreWideEvent {
    suspend fun onEmailRestoreFlowStarted(isOriginWeb: Boolean)
    suspend fun onGooglePlayRestoreFlowStarted(isOriginWeb: Boolean)
    suspend fun onGooglePlayRestoreFlowStartedOnPurchaseAttempt()
    fun onSubscriptionWebViewUrlChanged(url: String)

    suspend fun onEmailRestoreSuccess()
    suspend fun onGooglePlayRestoreSuccess()
    suspend fun onGooglePlayRestoreFailure(error: String)
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class SubscriptionRestoreWideEventImpl @Inject constructor(
    private val wideEventClient: WideEventClient,
    private val privacyProFeature: Lazy<PrivacyProFeature>,
    private val dispatchers: DispatcherProvider,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
) : SubscriptionRestoreWideEvent {

    private var cachedFlowId: Long? = null

    override suspend fun onEmailRestoreFlowStarted(isOriginWeb: Boolean) {
        if (!isFeatureEnabled()) return
        val flowEntryPoint = if (isOriginWeb) FLOW_ENTRY_POINT_PURCHASE_OFFER_PAGE else FLOW_ENTRY_POINT_APP_SETTINGS
        onRestoreFlowStarted(restorePlatform = RESTORE_PLATFORM_EMAIL_ADDRESS, purchaseAttempt = false, flowEntryPoint = flowEntryPoint)
    }

    override suspend fun onGooglePlayRestoreFlowStarted(isOriginWeb: Boolean) {
        if (!isFeatureEnabled()) return
        val flowEntryPoint = if (isOriginWeb) FLOW_ENTRY_POINT_PURCHASE_OFFER_PAGE else FLOW_ENTRY_POINT_APP_SETTINGS
        onRestoreFlowStarted(restorePlatform = RESTORE_PLATFORM_GOOGLE_PLAY, purchaseAttempt = false, flowEntryPoint = flowEntryPoint)
    }

    override suspend fun onGooglePlayRestoreFlowStartedOnPurchaseAttempt() {
        if (!isFeatureEnabled()) return
        onRestoreFlowStarted(restorePlatform = RESTORE_PLATFORM_GOOGLE_PLAY, purchaseAttempt = true, flowEntryPoint = null)
    }

    override suspend fun onEmailRestoreSuccess() {
        if (!isFeatureEnabled()) return
        val wideEventId = getCurrentWideEventId() ?: return

        wideEventClient.intervalEnd(wideEventId, key = INTERVAL_RESTORE_LATENCY)
        wideEventClient.flowFinish(wideEventId, status = FlowStatus.Success)
        cachedFlowId = null
    }

    override suspend fun onGooglePlayRestoreSuccess() {
        if (!isFeatureEnabled()) return
        val wideEventId = getCurrentWideEventId() ?: return

        wideEventClient.intervalEnd(wideEventId, key = INTERVAL_RESTORE_LATENCY)
        wideEventClient.flowFinish(wideEventId, status = FlowStatus.Success)
        cachedFlowId = null
    }

    override suspend fun onGooglePlayRestoreFailure(error: String) {
        if (!isFeatureEnabled()) return
        val wideEventId = getCurrentWideEventId() ?: return

        wideEventClient.flowFinish(wideEventId, status = FlowStatus.Failure(reason = error))
        cachedFlowId = null
    }

    override fun onSubscriptionWebViewUrlChanged(url: String) {
        coroutineScope.launch {
            runCatching {
                if (!isFeatureEnabled()) return@runCatching
                val wideEventId = getCurrentWideEventId() ?: return@runCatching

                val stepName = when (url.toUri().path) {
                    PATH_ACTIVATE_BY_EMAIL_OTP -> STEP_ONE_TIME_PASSWORD_INPUT
                    PATH_ACTIVATE_BY_EMAIL -> STEP_EMAIL_INPUT
                    PATH_ACTIVATION_FLOW -> STEP_ACTIVATION_FLOW_STARTED
                    else -> null
                }

                if (stepName != null) {
                    wideEventClient.flowStep(wideEventId, stepName)
                }
            }
        }
    }

    private suspend fun onRestoreFlowStarted(restorePlatform: String, purchaseAttempt: Boolean, flowEntryPoint: String?) {
        getCurrentWideEventId()?.let { wideEventId ->
            wideEventClient.flowFinish(wideEventId = wideEventId, status = FlowStatus.Unknown)
            cachedFlowId = null
        }

        val flowId = wideEventClient
            .flowStart(
                name = SUBSCRIPTION_RESTORE_FEATURE_NAME,
                cleanupPolicy = CleanupPolicy.OnProcessStart(ignoreIfIntervalTimeoutPresent = true),
                flowEntryPoint = flowEntryPoint,
                metadata = mapOf(
                    KEY_RESTORE_PLATFORM to restorePlatform,
                    KEY_IS_PURCHASE_ATTEMPT to purchaseAttempt.toString(),
                ),
            )
            .getOrNull() ?: return

        wideEventClient.intervalStart(wideEventId = flowId, key = INTERVAL_RESTORE_LATENCY)

        cachedFlowId = flowId
    }

    private suspend fun isFeatureEnabled(): Boolean = withContext(dispatchers.io()) {
        privacyProFeature.get().sendSubscriptionRestoreWideEvent().isEnabled()
    }

    private suspend fun getCurrentWideEventId(): Long? {
        if (cachedFlowId == null) {
            cachedFlowId = wideEventClient
                .getFlowIds(SUBSCRIPTION_RESTORE_FEATURE_NAME)
                .getOrNull()
                ?.lastOrNull()
        }

        return cachedFlowId
    }

    private companion object {
        const val SUBSCRIPTION_RESTORE_FEATURE_NAME = "subscription-restore"
        const val INTERVAL_RESTORE_LATENCY = "restore_latency_ms_bucketed"
        const val KEY_RESTORE_PLATFORM = "restore_platform"
        const val KEY_IS_PURCHASE_ATTEMPT = "is_purchase_attempt"
        const val RESTORE_PLATFORM_GOOGLE_PLAY = "google_play"
        const val RESTORE_PLATFORM_EMAIL_ADDRESS = "email_address"
        const val FLOW_ENTRY_POINT_APP_SETTINGS = "funnel_appsettings_android"
        const val FLOW_ENTRY_POINT_PURCHASE_OFFER_PAGE = "funnel_purchase_offer_page_android"

        const val PATH_ACTIVATION_FLOW = "/subscriptions/activation-flow"
        const val PATH_ACTIVATE_BY_EMAIL = "/subscriptions/activation-flow/this-device/activate-by-email"
        const val PATH_ACTIVATE_BY_EMAIL_OTP = "/subscriptions/activation-flow/this-device/activate-by-email/otp"

        const val STEP_ACTIVATION_FLOW_STARTED = "activation_flow_started"
        const val STEP_EMAIL_INPUT = "email_address_input"
        const val STEP_ONE_TIME_PASSWORD_INPUT = "one_time_password_input"
    }
}
