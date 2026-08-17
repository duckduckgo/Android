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

package com.duckduckgo.pir.impl.common

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.impl.PirRemoteFeatures
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStep
import com.duckduckgo.pir.impl.common.PirJobConstants.GATED_ACTION_PUSH_DELAY_MS
import com.duckduckgo.pir.impl.common.PirJobConstants.OPT_OUT_FILL_FORM_PUSH_DELAY_MS
import com.duckduckgo.pir.impl.models.ProfileQuery
import com.duckduckgo.pir.impl.scripts.models.BrokerAction.Click
import com.duckduckgo.pir.impl.scripts.models.BrokerAction.Expectation
import com.duckduckgo.pir.impl.scripts.models.BrokerAction.FillForm
import com.duckduckgo.pir.impl.scripts.models.BrokerAction.SolveCaptcha
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import logcat.logcat
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject

/**
 * Distributes work across a pool of [PirActionsRunner]s.
 *
 * The default scheduling is a shared queue: each runner pulls the next available step until the
 * queue drains, so a runner that finishes a cheap step takes more work instead of idling while
 * others still have steps. Steps are dequeued most-expensive-first, which stops a long step from
 * being picked up last and setting the wall-clock on its own, but round-robin across parent brokers, so
 * that no set of concurrently running steps belongs to a single one.
 */
interface PirWorkDistributor {
    /**
     * Executes every (profile, step) pair in [work] across [runners]; order is not preserved.
     *
     * @param onStepCompleted invoked once per completed step, whichever runner executed it
     */
    suspend fun executeAll(
        runners: List<PirActionsRunner>,
        work: List<Pair<ProfileQuery, BrokerStep>>,
        onStepCompleted: suspend () -> Unit = {},
    )
}

@ContributesBinding(AppScope::class)
class RealPirWorkDistributor @Inject constructor(
    private val pirRemoteFeatures: PirRemoteFeatures,
) : PirWorkDistributor {

    override suspend fun executeAll(
        runners: List<PirActionsRunner>,
        work: List<Pair<ProfileQuery, BrokerStep>>,
        onStepCompleted: suspend () -> Unit,
    ) {
        if (work.isEmpty() || runners.isEmpty()) {
            logcat { "PIR-DISTRIBUTOR: Nothing to distribute (work=${work.size}, runners=${runners.size})" }
            return
        }

        if (pirRemoteFeatures.workQueueScheduling().isEnabled()) {
            executeQueued(runners, work, onStepCompleted)
        } else {
            executeStaticallyPartitioned(runners, work, onStepCompleted)
        }
    }

    private suspend fun executeQueued(
        runners: List<PirActionsRunner>,
        work: List<Pair<ProfileQuery, BrokerStep>>,
        onStepCompleted: suspend () -> Unit,
    ) {
        val queue = ConcurrentLinkedQueue(work.orderedForDispatch())

        val workers = minOf(runners.size, work.size)
        logcat { "PIR-DISTRIBUTOR: Distributing ${work.size} steps across $workers runners" }

        coroutineScope {
            runners.take(workers).map { runner ->
                async {
                    try {
                        while (true) {
                            val (profileQuery, brokerStep) = queue.poll() ?: break
                            runner.execute(profileQuery, brokerStep)
                            onStepCompleted()
                        }
                    } finally {
                        runner.stop()
                    }
                }
            }.awaitAll()
        }
    }

    private suspend fun executeStaticallyPartitioned(
        runners: List<PirActionsRunner>,
        work: List<Pair<ProfileQuery, BrokerStep>>,
        onStepCompleted: suspend () -> Unit,
    ) {
        val parts = work.splitIntoParts(runners.size)
        logcat { "PIR-DISTRIBUTOR: Statically distributing ${work.size} steps across ${parts.size} runners" }

        coroutineScope {
            parts.mapIndexed { index, partSteps ->
                async {
                    try {
                        partSteps.forEach { (profileQuery, brokerStep) ->
                            runners[index].execute(profileQuery, brokerStep)
                            onStepCompleted()
                        }
                    } finally {
                        runners[index].stop()
                    }
                }
            }.awaitAll()
        }
    }

    /**
     * Orders steps so that consecutive queue slots belong to different parent brokers as running multiple child brokers
     * with same actions in parallel can cause resource starvation issues with anti-bot challenges, which in turn causes timeouts,
     * so the scan reports no matches for sites that do hold records.
     */
    private fun List<Pair<ProfileQuery, BrokerStep>>.orderedForDispatch(): List<Pair<ProfileQuery, BrokerStep>> {
        val perParentBroker = groupBy { (_, brokerStep) -> brokerStep.parentBroker() }
            .values
            .map { steps -> steps.sortedByDescending { (_, brokerStep) -> brokerStep.estimatedCostMs() } }
            .sortedByDescending { steps -> steps.first().second.estimatedCostMs() }

        val deepest = perParentBroker.maxOfOrNull { it.size } ?: 0
        return (0 until deepest).flatMap { index ->
            perParentBroker.mapNotNull { it.getOrNull(index) }
        }
    }

    private fun BrokerStep.parentBroker(): String = broker.parent ?: broker.url

    private fun BrokerStep.estimatedCostMs(): Long =
        step.actions.count { it is Click || it is Expectation } * GATED_ACTION_PUSH_DELAY_MS +
            step.actions.count { it is FillForm } * OPT_OUT_FILL_FORM_PUSH_DELAY_MS +
            step.actions.count { it is SolveCaptcha } * CAPTCHA_SOLVE_COST_MS +
            step.actions.size * ACTION_BASE_COST_MS

    companion object {
        private const val ACTION_BASE_COST_MS = 1_000L

        // Estimated cost SolveCaptcha actions since the latency is network- and service-dependent.
        private const val CAPTCHA_SOLVE_COST_MS = 15_000L
    }
}
