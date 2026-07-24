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
import com.duckduckgo.modalcoordinator.api.ModalTrigger
import com.duckduckgo.modalcoordinator.api.NewTabPageModalTrigger
import com.duckduckgo.modalcoordinator.impl.store.ModalEvaluatorCompletionStore
import com.duckduckgo.promptscoordinator.api.PromptType
import com.duckduckgo.promptscoordinator.api.PromptsCoordinator
import com.squareup.anvil.annotations.ContributesBinding
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
 * - Each coordinated pass is started by a [ModalTrigger] and only considers evaluators declaring
 *   that trigger (see [ModalEvaluator.trigger]); evaluators on different triggers never compete
 * - [ModalTrigger.APP_RESUME] passes run on process foreground (onResume)
 * - [ModalTrigger.NTP_RENDER] passes run when a host reports a New Tab Page render
 * - Evaluators are sorted by priority (lowest number = highest priority)
 * - Only one evaluator runs per coordinated pass
 * - Evaluators blocked by 24-hour window are skipped entirely (evaluate() not called)
 * - When evaluation completes (with or without action), timestamp is recorded
 */
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
@ContributesBinding(
    scope = AppScope::class,
    boundType = NewTabPageModalTrigger::class,
)
@SingleInstanceIn(AppScope::class)
class ModalEvaluatorCoordinator @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val modalEvaluatorPluginPoint: PluginPoint<ModalEvaluator>,
    private val completionStore: ModalEvaluatorCompletionStore,
    private val promptsCoordinator: PromptsCoordinator,
    private val dispatchers: DispatcherProvider,
) : MainProcessLifecycleObserver, NewTabPageModalTrigger {

    private val evaluationMutex = Mutex()

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        appCoroutineScope.launch(dispatchers.io()) {
            coordinateEvaluation(ModalTrigger.APP_RESUME)
        }
    }

    override fun onNewTabPageShown() {
        appCoroutineScope.launch(dispatchers.io()) {
            coordinateEvaluation(ModalTrigger.NTP_RENDER)
        }
    }

    private suspend fun coordinateEvaluation(trigger: ModalTrigger) = evaluationMutex.withLock {
        logcat { "ModalEvaluatorCoordinator: Starting coordinated evaluation for trigger $trigger" }

        val promptsCoordinatorEnabled = promptsCoordinator.isEnabled()
        if (promptsCoordinatorEnabled) {
            // The prompts-coordinator owns all prompt gaps (its 24h-since-any-prompt gate subsumes
            // the internal modal↔modal window, which is kept below only as the kill-switch fallback).
            if (!promptsCoordinator.tryClaim(PromptType.MODAL)) {
                logcat { "ModalEvaluatorCoordinator: Evaluation is blocked by the prompts coordinator" }
                return@withLock
            }
        } else if (completionStore.isBlockedBy24HourWindow()) {
            logcat { "ModalEvaluatorCoordinator: Evaluation is blocked by 24-hour window" }
            return@withLock
        }

        val evaluators = modalEvaluatorPluginPoint.getPlugins()
            .filter { it.trigger == trigger }
            .sortedBy { it.priority }
        logcat { "ModalEvaluatorCoordinator: Found ${evaluators.size} evaluators for trigger $trigger" }

        for (evaluator in evaluators) {
            logcat { "ModalEvaluatorCoordinator: Evaluating '${evaluator.evaluatorId}' (priority ${evaluator.priority})" }

            // Start evaluation
            when (evaluator.evaluate()) {
                is ModalEvaluator.EvaluationResult.ModalShown -> {
                    logcat { "ModalEvaluatorCoordinator: Evaluator '${evaluator.evaluatorId}' completed and modal shown, record timestamp and stop" }
                    // Always recorded (even when the prompts-coordinator owns gating) so flipping
                    // the kill-switch either way stays seamless. The claim stays held: the modal's
                    // dismissal call-site reports onClaimDone(MODAL) when the prompt leaves the screen.
                    completionStore.recordCompletion()
                    return@withLock
                }
                is ModalEvaluator.EvaluationResult.Skipped -> {
                    logcat { "ModalEvaluatorCoordinator: Evaluator '${evaluator.evaluatorId}' skipped, continue to next" }
                }
            }
        }

        // Nothing was shown: free the surface without stamping the gap timestamp (no-op when the
        // prompts-coordinator is disabled or holds no claim).
        promptsCoordinator.onClaimCancelled(PromptType.MODAL)
        logcat { "ModalEvaluatorCoordinator: Coordination complete for trigger $trigger, no action taken" }
    }
}
