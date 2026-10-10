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
import com.duckduckgo.pir.impl.common.BrokerStepsParser
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStep.ScanStep
import com.duckduckgo.pir.impl.models.Broker
import com.duckduckgo.pir.impl.scripts.models.BrokerAction
import com.duckduckgo.pir.impl.store.PirRepository
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

interface PirFreeScanBrokerFilter {
    /** Drops brokers whose scan needs a DBP auth token. A broker with no scan step is not gated, so it stays. */
    suspend fun excludingGatedBrokers(brokers: List<Broker>): List<Broker>

    /**
     * Active brokers a free user can actually scan: the scan step parses and needs no auth token.
     * An unparseable scan step is skipped at scan time, so counting it would strand the initial scan short.
     */
    suspend fun freeScannableBrokerNames(): Set<String>
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealPirFreeScanBrokerFilter @Inject constructor(
    private val pirRepository: PirRepository,
    private val brokerStepsParser: BrokerStepsParser,
    private val dispatcherProvider: DispatcherProvider,
) : PirFreeScanBrokerFilter {

    // Keyed by version so a broker update invalidates its own entry.
    private val classificationByBrokerVersion = ConcurrentHashMap<String, ScanStepClassification>()

    override suspend fun excludingGatedBrokers(brokers: List<Broker>): List<Broker> =
        withContext(dispatcherProvider.io()) {
            brokers.filterNot { classify(it) == ScanStepClassification.GATED }
        }

    override suspend fun freeScannableBrokerNames(): Set<String> = withContext(dispatcherProvider.io()) {
        pirRepository.getAllActiveBrokerObjects()
            .filter { classify(it) == ScanStepClassification.FREE_SCANNABLE }
            .mapTo(hashSetOf()) { it.name }
    }

    private suspend fun classify(broker: Broker): ScanStepClassification =
        classificationByBrokerVersion.getOrPut("${broker.name}@${broker.version}") {
            val scanStep = scanStepOf(broker) ?: return@getOrPut ScanStepClassification.NO_USABLE_SCAN_STEP
            if (scanStep.step.actions.any { it.isTokenGated() }) {
                ScanStepClassification.GATED
            } else {
                ScanStepClassification.FREE_SCANNABLE
            }
        }

    private suspend fun scanStepOf(broker: Broker): ScanStep? {
        val stepsJson = pirRepository.getBrokerScanSteps(broker.name) ?: return null
        return brokerStepsParser.parseStep(broker, stepsJson).firstOrNull() as? ScanStep
    }
}

private enum class ScanStepClassification {
    GATED,
    FREE_SCANNABLE,
    NO_USABLE_SCAN_STEP,
}

/**
 * @return true if this broker action is auth-gated
 */
private fun BrokerAction.isTokenGated(): Boolean = when (this) {
    is BrokerAction.GenerateEmail,
    is BrokerAction.GetEmailData,
    is BrokerAction.GetCaptchaInfo,
    is BrokerAction.SolveCaptcha,
    is BrokerAction.EmailConfirmation,
    -> true

    is BrokerAction.Condition -> actions.any { it.isTokenGated() }

    is BrokerAction.Navigate,
    is BrokerAction.Extract,
    is BrokerAction.FillForm,
    is BrokerAction.Click,
    is BrokerAction.ExecuteScript,
    is BrokerAction.Expectation,
    -> false
}
