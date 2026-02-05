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

package com.duckduckgo.modalcoordinator.impl

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.modalcoordinator.api.ModalEvaluator
import com.duckduckgo.modalcoordinator.impl.store.ModalEvaluatorCompletionStore
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logcat.logcat
import javax.inject.Inject

/**
 * Coordinates evaluation of modal evaluators with priority ordering and 24-hour blocking.
 *
 * Key behaviors:
 * - Evaluators are sorted by priority (lowest number = highest priority)
 * - Only one evaluator runs per lifecycle resume
 * - Evaluators blocked by 24-hour window are skipped entirely (evaluate() not called)
 * - When evaluation completes (with or without action), timestamp is recorded
 */
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
@SingleInstanceIn(AppScope::class)
class ModalEvaluatorCoordinator @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val modalEvaluatorPluginPoint: PluginPoint<ModalEvaluator>,
    private val completionStore: ModalEvaluatorCompletionStore,
    private val dispatchers: DispatcherProvider,
) : MainProcessLifecycleObserver {

    private val evaluationMutex = Mutex()

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        appCoroutineScope.launch(dispatchers.io()) {
            coordinateEvaluation()
        }
    }

    private suspend fun coordinateEvaluation() = evaluationMutex.withLock {
        logcat { "ModalEvaluatorCoordinator: Starting coordinated evaluation" }

        // Check 24-hour blocking first
        if (completionStore.isBlockedBy24HourWindow()) {
            logcat { "ModalEvaluatorCoordinator: Evaluation is blocked by 24-hour window" }
            return@withLock
        }

        val evaluators = modalEvaluatorPluginPoint.getPlugins().sortedBy { it.priority }
        logcat { "ModalEvaluatorCoordinator: Found ${evaluators.size} evaluators" }

        for (evaluator in evaluators) {
            logcat { "ModalEvaluatorCoordinator: Evaluating '${evaluator.evaluatorId}' (priority ${evaluator.priority})" }

            // Start evaluation
            when (evaluator.evaluate()) {
                is ModalEvaluator.EvaluationResult.ModalShown -> {
                    logcat { "ModalEvaluatorCoordinator: Evaluator '${evaluator.evaluatorId}' completed and modal shown, record timestamp and stop" }
                    completionStore.recordCompletion()
                    return@withLock
                }
                is ModalEvaluator.EvaluationResult.Skipped -> {
                    logcat { "ModalEvaluatorCoordinator: Evaluator '${evaluator.evaluatorId}' skipped, continue to next" }
                }
            }
        }

        logcat { "ModalEvaluatorCoordinator: Coordination complete, no action taken" }
    }
}
