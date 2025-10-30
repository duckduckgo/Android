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

package com.duckduckgo.subscriptions.impl.metrics

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.attributed.metrics.api.AttributedMetric
import com.duckduckgo.app.attributed.metrics.api.AttributedMetricClient
import com.duckduckgo.app.attributed.metrics.api.AttributedMetricConfig
import com.duckduckgo.app.attributed.metrics.api.MetricBucket
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.repository.AuthRepository
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.LAZY
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import logcat.logcat
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@ContributesMultibinding(AppScope::class, AttributedMetric::class)
@ContributesMultibinding(AppScope::class, MainProcessLifecycleObserver::class)
@SingleInstanceIn(AppScope::class)
class SubscriptionStatusAttributedMetric @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val attributedMetricClient: AttributedMetricClient,
    private val authRepository: AuthRepository,
    private val attributedMetricConfig: AttributedMetricConfig,
    private val subscriptionsManager: SubscriptionsManager,
) : AttributedMetric, MainProcessLifecycleObserver {

    companion object {
        private const val PIXEL_NAME = "attributed_metric_subscribed"
        private const val FEATURE_TOGGLE_NAME = "subscriptionRetention"
        private const val FEATURE_EMIT_TOGGLE_NAME = "canEmitSubscriptionRetention"
    }

    private val isEnabled: Deferred<Boolean> = appCoroutineScope.async(start = LAZY) {
        getToggle(FEATURE_TOGGLE_NAME)?.isEnabled() ?: false
    }

    private val canEmit: Deferred<Boolean> = appCoroutineScope.async(start = LAZY) {
        getToggle(FEATURE_EMIT_TOGGLE_NAME)?.isEnabled() ?: false
    }

    private val bucketConfig: Deferred<MetricBucket> = appCoroutineScope.async(start = LAZY) {
        attributedMetricConfig.getBucketConfiguration()[PIXEL_NAME] ?: MetricBucket(
            buckets = listOf(0, 1),
            version = 0,
        )
    }

    override fun onCreate(owner: LifecycleOwner) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            if (!isEnabled.await() || !canEmit.await()) {
                logcat(tag = "AttributedMetrics") {
                    "SubscriptionStatusAttributedMetric disabled"
                }
                return@launch
            }
            subscriptionsManager.subscriptionStatus.distinctUntilChanged().collect { status ->
                logcat(tag = "AttributedMetrics") {
                    "SubscriptionStatusAttributedMetric subscription status changed: $status"
                }
                if (shouldSendPixel()) {
                    logcat(tag = "AttributedMetrics") {
                        "SubscriptionStatusAttributedMetric emitting metric on status change"
                    }
                    attributedMetricClient.emitMetric(
                        this@SubscriptionStatusAttributedMetric,
                    )
                }
            }
        }
    }

    override fun getPixelName(): String = PIXEL_NAME

    override suspend fun getMetricParameters(): Map<String, String> {
        val daysSinceSubscribed = daysSinceSubscribed()
        if (daysSinceSubscribed == -1) {
            return emptyMap() // Should not happen as we check enrollment before sending the pixel
        }
        val isOnTrial = authRepository.isFreeTrialActive()
        val params = mutableMapOf(
            "month" to getBucketValue(daysSinceSubscribed, isOnTrial).toString(),
            "version" to bucketConfig.await().version.toString(),
        )
        return params
    }

    override suspend fun getTag(): String {
        val daysSinceSubscribed = daysSinceSubscribed()
        val isOnTrial = authRepository.isFreeTrialActive()
        return getBucketValue(daysSinceSubscribed, isOnTrial).toString()
    }

    private suspend fun shouldSendPixel(): Boolean {
        val isActive = isSubscriptionActive()
        logcat(tag = "AttributedMetrics") {
            "SubscriptionStatusAttributedMetric shouldSendPixel isActive: $isActive"
        }
        val enrolled = daysSinceSubscribed() != -1
        logcat(tag = "AttributedMetrics") {
            "SubscriptionStatusAttributedMetric shouldSendPixel enrolled: $enrolled daysSinceSubscribed() = ${daysSinceSubscribed()}"
        }
        return isActive && enrolled
    }

    private suspend fun isSubscriptionActive(): Boolean {
        return authRepository.getStatus() == SubscriptionStatus.AUTO_RENEWABLE ||
            authRepository.getStatus() == SubscriptionStatus.NOT_AUTO_RENEWABLE
    }

    private suspend fun daysSinceSubscribed(): Int {
        return authRepository.getLocalPurchasedAt()?.let { nonNullStartedAt ->
            val etZone = ZoneId.of("America/New_York")
            val installInstant = Instant.ofEpochMilli(nonNullStartedAt)
            val nowInstant = Instant.now()

            val installInEt = installInstant.atZone(etZone)
            val nowInEt = nowInstant.atZone(etZone)

            return ChronoUnit.DAYS.between(installInEt.toLocalDate(), nowInEt.toLocalDate()).toInt()
        } ?: -1
    }

    private suspend fun getBucketValue(
        days: Int,
        isOnTrial: Boolean,
    ): Int {
        if (isOnTrial) {
            return 0
        }

        // Calculate which month the user is in (1-based)
        // Each 28 days is a new month
        val monthNumber = days / 28 + 1

        // Get the bucket configuration
        val buckets = bucketConfig.await().buckets
        return buckets.indexOfFirst { bucket -> monthNumber <= bucket }.let { index ->
            if (index == -1) buckets.size else index
        }
    }

    private suspend fun getToggle(toggleName: String) =
        attributedMetricConfig.metricsToggles().firstOrNull { toggle ->
            toggle.featureName().name == toggleName
        }
}
