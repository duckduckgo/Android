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

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.ConversionWindow
import com.duckduckgo.feature.toggles.api.FeatureTogglesInventory
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
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

interface ExperimentMetricsHandler {
    /**
     * Hands every experiment metric that listens for [eventType] to the experiments framework, which decides
     * enrolment, conversion windows and thresholds. Call it only for events that passed de-duplication.
     */
    suspend fun onEvent(eventType: String)

    /** Drops the cached metric definitions, so the next event reads them from the current config. */
    fun onConfigChanged()
}

internal data class MetricBinding(val experiment: Toggle, val metric: MetricDefinition)

private class CachedBindings(
    val version: Int,
    val bindingsByEvent: Map<String, List<MetricBinding>>,
    val tdsExperiments: List<Toggle>,
)

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealExperimentMetricsHandler @Inject constructor(
    private val inventory: FeatureTogglesInventory,
) : ExperimentMetricsHandler {

    // Sends are serialised because the framework's counter update isn't atomic: two overlapping sends for the same
    // metric can lose a count or fire twice
    private val mutex = Mutex()

    // The cache is only valid for the config version it was built from. Keeping the version with it, in one atomic value,
    // means a build that finishes after a config change can't leave old definitions in use: its version no longer matches
    private val configVersion = AtomicInteger(0)
    private val cache = AtomicReference<CachedBindings?>(null)

    override suspend fun onEvent(eventType: String) {
        mutex.withLock {
            val cached = bindings() ?: return
            val bindings = cached.bindingsByEvent[eventType] ?: return
            val activeTds by lazy { cached.tdsExperiments.firstOrNull { it.isEnabled() }?.featureName() }
            for ((experiment, metric) in bindings) {
                // Checked per event rather than when building the cache, so the cache depends only on config. The framework
                // checks this too; skipping here just avoids its dispatcher hop for every disabled experiment
                if (!experiment.isEnabled()) continue
                // Only one blocklist experiment is in effect at a time: the one the blocklist is downloaded, enrolled and
                // measured for. An older TDS experiment left enabled would otherwise convert for users on another blocklist
                if (experiment.featureName().parentName == BLOCK_LIST && experiment.featureName() != activeTds) continue
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

    override fun onConfigChanged() {
        configVersion.incrementAndGet()
    }

    private suspend fun bindings(): CachedBindings? {
        val version = configVersion.get()
        cache.get()?.takeIf { it.version == version }?.let { return it }
        val built = try {
            buildBindings(version)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Not cached, so the next event tries again
            logcat(WARN) { "EventHub: failed to read experiment metrics: ${e.message}" }
            return null
        }
        cache.set(built)
        return built
    }

    // Walking every toggle is expensive, so it runs once per config change rather than once per event
    private suspend fun buildBindings(version: Int): CachedBindings {
        val experiments = inventory.getAll().filter { it.featureName().parentName in EXPERIMENT_PARENTS }
        val bindingsByEvent = experiments
            .flatMap { experiment ->
                experiment.getSettings()
                    ?.let(ExperimentMetricsConfigParser::parse)
                    .orEmpty()
                    .map { metric -> MetricBinding(experiment, metric) }
            }
            .groupBy { it.metric.event }
        // Matches the blocklist's activeTdsFlag(): the first enabled blockList subfeature whose name starts with "tds"
        val tdsExperiments = experiments.filter {
            it.featureName().parentName == BLOCK_LIST && it.featureName().name.startsWith(TDS_PREFIX)
        }
        return CachedBindings(version, bindingsByEvent, tdsExperiments)
    }

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

    private companion object {
        const val BLOCK_LIST = "blockList"
        const val TDS_PREFIX = "tds"
        val EXPERIMENT_PARENTS = setOf("contentScopeExperiments", BLOCK_LIST)
    }
}
