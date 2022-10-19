/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.health

import com.duckduckgo.mobile.android.vpn.health.AppTPHealthMonitor.HealthState
import com.duckduckgo.mobile.android.vpn.health.AppTPHealthMonitor.HealthState.*
import com.duckduckgo.mobile.android.vpn.model.HealthTriggerEntity
import com.duckduckgo.mobile.android.vpn.store.AppHealthTriggersRepository
import javax.inject.Inject

class HealthClassifier @Inject constructor(
    private val appHealthTriggersRepository: AppHealthTriggersRepository,
) {

    fun determineHealthVpnConnectivity(connectivityEvents: Long, name: String): HealthState {
        val rawMetrics = mutableMapOf<String, Metric>()
        val metricSummary = RawMetricsSubmission("vpn-no-connectivity-events", rawMetrics)

        val trigger = appHealthTriggersRepository.triggers().trigger(name, connectivityEvents, defaultThreshold = 2)
        rawMetrics["events"] = Metric(connectivityEvents.toString(), badHealthIf { trigger.evaluate() }, isCritical = true)

        return if (metricSummary.isInBadHealth()) BadHealth(metricSummary) else GoodHealth(metricSummary)
    }

    private fun badHealthIf(function: () -> Boolean): Boolean {
        return function.invoke()
    }

    private fun List<HealthTriggerEntity>.trigger(name: String, value: Long, defaultThreshold: Long? = null): Trigger {
        // if there is no trigger use either the [defaultThreshold] or trigger disabled
        val trigger = firstOrNull {
            it.name.endsWith(name, ignoreCase = true)
        } ?: return (defaultThreshold?.let { Trigger { value >= defaultThreshold } } ?: Trigger { false })

        // if remote trigger state is enabled, use the trigger threshold or defaultThreshold or disable trigger
        return if (trigger.enabled) {
            trigger.threshold?.let { Trigger { value >= it } }
                ?: (defaultThreshold?.let { Trigger { value >= defaultThreshold } } ?: Trigger { false })
        } else {
            Trigger { false }
        }
    }

    private fun interface Trigger {
        fun evaluate(): Boolean
    }
}
