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

package com.duckduckgo.pir.impl.brokers

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.impl.PirFeatureDataCleaner
import com.duckduckgo.pir.impl.checker.PirEligibility
import com.duckduckgo.pir.impl.checker.PirWorkHandler
import com.duckduckgo.pir.impl.pixels.PirPixelSender
import com.duckduckgo.pir.impl.scan.PirScanScheduler
import com.duckduckgo.pir.impl.store.PirRepository
import com.duckduckgo.pir.impl.wideevents.PirScanWideEvent.CancellationReason
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import logcat.logcat
import javax.inject.Inject

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
class PirDataUpdateObserver @Inject constructor(
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val brokerJsonUpdater: BrokerJsonUpdater,
    private val pirWorkHandler: PirWorkHandler,
    private val pirFeatureDataCleaner: PirFeatureDataCleaner,
    private val pirRepository: PirRepository,
    private val currentTimeProvider: CurrentTimeProvider,
    private val pirPixelSender: PirPixelSender,
    private val pirScanScheduler: PirScanScheduler,
) : MainProcessLifecycleObserver {
    override fun onCreate(owner: LifecycleOwner) {
        coroutineScope.launch(dispatcherProvider.io()) {
            // Observe when PIR becomes available so we can fetch the broker data or cancel all related work
            pirWorkHandler
                .canRunPir()
                .collectLatest { eligibility ->
                    val featureReceiveMs = pirRepository.getFeatureReceivedMs()
                    when (eligibility) {
                        is PirEligibility.Enabled -> {
                            pirPixelSender.reportCanRunPir()
                            // We only set the value if it was not set previously
                            if (featureReceiveMs == 0L) {
                                pirRepository.setFeatureReceivedMs(currentTimeProvider.currentTimeMillis())
                            }

                            logcat { "PIR-update: Attempting to update all broker data" }
                            if (brokerJsonUpdater.update()) {
                                logcat { "PIR-update: Update successfully completed." }
                            } else {
                                logcat { "PIR-update: Failed to complete." }
                            }

                            // Re-apply the periodic scan schedule so changes to the interval/constraints
                            // (e.g. after an app update) are picked up by already-enrolled users
                            if (pirRepository.getValidUserProfileQueries().isNotEmpty()) {
                                pirScanScheduler.reschedulePirScans()
                            }
                        }

                        is PirEligibility.Disabled -> {
                            logcat { "PIR-update: PIR not enabled" }
                            // We also check the etag to handle scenarios where featureReceiveMs was not yet available
                            if (featureReceiveMs != 0L || pirRepository.getCurrentMainEtag() != null) {
                                logcat { "PIR-update: resetting feature" }
                                // This will also cancel any ongoing work that is currently running if PIR is not enabled
                                // This will also clear the featureReceivedMs
                                pirWorkHandler.cancelWork(CancellationReason.fromDisabledReason(eligibility.reason))
                                pirFeatureDataCleaner.removeAllData()
                            }
                        }
                    }
                }
        }
    }
}
