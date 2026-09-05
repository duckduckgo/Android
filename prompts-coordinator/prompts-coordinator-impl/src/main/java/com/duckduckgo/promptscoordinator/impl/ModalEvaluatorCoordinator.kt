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

package com.duckduckgo.promptscoordinator.impl

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.promptscoordinator.api.ModalEvaluator
import com.duckduckgo.promptscoordinator.api.ModalTrigger
import com.duckduckgo.promptscoordinator.api.NewTabPageModalTrigger
import com.duckduckgo.promptscoordinator.api.PromptType
import com.duckduckgo.promptscoordinator.api.PromptsCoordinator
import com.duckduckgo.promptscoordinator.impl.store.ModalEvaluatorCompletionStore
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
 * - A pass only considers evaluators declaring the [ModalTrigger] that started it
 * - Evaluators are sorted by priority (lowest number = highest priority)
 * - At most one modal shows per coordinated pass
 * - Evaluation is decide-then-show: evaluators answer with a deferred show action, and the shared
 *   prompt surface is claimed only once an evaluator wants to show, never while deliberating
 * - Evaluators blocked by 24-hour window are skipped entirely (evaluate() not called)
 * - When a modal is shown, timestamp is recorded
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
        // The coordinator's gap subsumes the internal modal↔modal window, kept as the kill-switch
        // fallback. When enabled, the gap is enforced at claim time instead, so nothing is checked
        // upfront: evaluation itself is side-effect free and needs no gating.
        if (!promptsCoordinatorEnabled && completionStore.isBlockedBy24HourWindow()) {
            logcat { "ModalEvaluatorCoordinator: Evaluation is blocked by 24-hour window" }
            return@withLock
        }

        val evaluators = modalEvaluatorPluginPoint.getPlugins()
            .filter { it.trigger == trigger }
            .sortedBy { it.priority }
        logcat { "ModalEvaluatorCoordinator: Found ${evaluators.size} evaluators for trigger $trigger" }

        for (evaluator in evaluators) {
            logcat { "ModalEvaluatorCoordinator: Evaluating '${evaluator.evaluatorId}' (priority ${evaluator.priority})" }

            when (val result = evaluator.evaluate()) {
                is ModalEvaluator.EvaluationResult.WantsToShow -> {
                    // The surface is claimed only now that a modal is definitely about to show, so
                    // the claim is never speculative. A refusal means another prompt genuinely holds
                    // the surface (or the quiet gap hasn't elapsed) — either way it refuses every
                    // evaluator in this pass, so stop rather than continue down the priority list.
                    if (promptsCoordinatorEnabled && !promptsCoordinator.tryClaim(PromptType.MODAL)) {
                        logcat {
                            "ModalEvaluatorCoordinator: '${evaluator.evaluatorId}' wants to show " +
                                "but is blocked by the prompts coordinator"
                        }
                        return@withLock
                    }

                    if (show(result, promptsCoordinatorEnabled)) {
                        logcat {
                            "ModalEvaluatorCoordinator: Evaluator '${evaluator.evaluatorId}' " +
                                "modal shown, record timestamp and stop"
                        }
                        // Recorded either way so kill-switch flips stay seamless.
                        completionStore.recordCompletion()
                        return@withLock
                    }
                    logcat { "ModalEvaluatorCoordinator: '${evaluator.evaluatorId}' show fell through, continue to next" }
                }
                is ModalEvaluator.EvaluationResult.Skipped -> {
                    logcat { "ModalEvaluatorCoordinator: Evaluator '${evaluator.evaluatorId}' skipped, continue to next" }
                }
            }
        }

        logcat { "ModalEvaluatorCoordinator: Coordination complete for trigger $trigger, no action taken" }
    }

    /**
     * Runs the deferred show action while guaranteeing the claim is settled afterwards: done when the
     * modal reached the user, cancelled when showing fell through or threw. Show actions are plugin
     * code from other teams, and a throw would otherwise strand the surface for the rest of the process.
     */
    private suspend fun show(
        result: ModalEvaluator.EvaluationResult.WantsToShow,
        claimHeld: Boolean,
    ): Boolean {
        var shown = false
        try {
            shown = result.show()
        } finally {
            if (claimHeld) {
                if (shown) {
                    promptsCoordinator.onClaimDone(PromptType.MODAL)
                } else {
                    // Nothing is stamped: a show that fell through is no evidence a prompt reached the user.
                    promptsCoordinator.onClaimCancelled(PromptType.MODAL)
                }
            }
        }
        return shown
    }
}
