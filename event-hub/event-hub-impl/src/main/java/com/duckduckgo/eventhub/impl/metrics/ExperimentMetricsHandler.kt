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

package com.duckduckgo.eventhub.impl.metrics

import com.duckduckgo.app.trackerdetection.blocklist.BlockListExperiment
import com.duckduckgo.contentscopescripts.api.contentscopeExperiments.ContentScopeExperiments
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.ConversionWindow
import com.duckduckgo.feature.toggles.api.MetricType
import com.duckduckgo.feature.toggles.api.MetricsPixel
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.send
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logcat.LogPriority.VERBOSE
import logcat.LogPriority.WARN
import logcat.logcat
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

interface ExperimentMetricsHandler {
    /**
     * Hands every experiment metric that listens for [eventType] to the experiments framework, which decides
     * enrolment, conversion windows and thresholds. Call it only for events that passed de-duplication.
     */
    suspend fun onEvent(eventType: String)
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealExperimentMetricsHandler @Inject constructor(
    private val contentScopeExperiments: ContentScopeExperiments,
    private val blockListExperiment: BlockListExperiment,
) : ExperimentMetricsHandler {

    // Sends are serialised because the framework's counter update isn't atomic: two overlapping sends for the same
    // metric can lose a count or fire twice
    private val mutex = Mutex()

    override suspend fun onEvent(eventType: String) {
        mutex.withLock {
            val experiments = try {
                activeExperiments()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(WARN) { "EventHub: failed to read active experiments: ${e.message}" }
                return
            }
            for (experiment in experiments) {
                val metrics = experiment.getSettings()?.let(ExperimentMetricsConfigParser::parse).orEmpty()
                for (metric in metrics.filter { it.event == eventType }) {
                    for ((threshold, windows) in metric.windowsByThreshold) {
                        logcat(VERBOSE) { "EventHub: metric ${metric.name} on ${experiment.featureName().name}, threshold $threshold" }
                        // The framework runs on the app scope, where an uncaught exception crashes the app; one failed send
                        // also mustn't stop the others
                        try {
                            sendConversion(experiment, metric.name, threshold, windows)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            logcat(WARN) { "EventHub: failed to send metric ${metric.name} on ${experiment.featureName().name}: ${e.message}" }
                        }
                    }
                }
            }
        }
    }

    // Each feature that runs experiments decides which are active, so the hub never disagrees with it about which one
    // a user is measured for
    private suspend fun activeExperiments(): List<Toggle> =
        contentScopeExperiments.getActiveExperiments() + listOfNotNull(blockListExperiment.activeExperiment())

    // value comes from remote config, already validated by the parser as a whole number of at least 1
    @Suppress("MetricsPixelNumericValue")
    private suspend fun sendConversion(experiment: Toggle, metric: String, threshold: Int, windows: List<ConversionWindow>) {
        MetricsPixel(
            metric = metric,
            value = threshold.toString(),
            conversionWindow = windows,
            toggle = experiment,
            type = MetricType.COUNT_WHEN_IN_WINDOW,
        ).send()
    }
}
