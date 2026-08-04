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

        var claimHeld = false
        val promptsCoordinatorEnabled = promptsCoordinator.isEnabled()
        if (promptsCoordinatorEnabled) {
            // The coordinator's gap subsumes the internal modal↔modal window, kept below as the
            // kill-switch fallback.
            if (!promptsCoordinator.tryClaim(PromptType.MODAL)) {
                logcat { "ModalEvaluatorCoordinator: Evaluation is blocked by the prompts coordinator" }
                return@withLock
            }
            claimHeld = true
        } else if (completionStore.isBlockedBy24HourWindow()) {
            logcat { "ModalEvaluatorCoordinator: Evaluation is blocked by 24-hour window" }
            return@withLock
        }

        // Everything past the claim runs inside the try: evaluators are plugin code from other teams,
        // and a throw anywhere here would otherwise strand the surface for the rest of the process.
        try {
            val evaluators = modalEvaluatorPluginPoint.getPlugins()
                .filter { it.trigger == trigger }
                .sortedBy { it.priority }
            logcat { "ModalEvaluatorCoordinator: Found ${evaluators.size} evaluators for trigger $trigger" }

            for (evaluator in evaluators) {
                logcat { "ModalEvaluatorCoordinator: Evaluating '${evaluator.evaluatorId}' (priority ${evaluator.priority})" }

                when (evaluator.evaluate()) {
                    is ModalEvaluator.EvaluationResult.ModalShown -> {
                        logcat {
                            "ModalEvaluatorCoordinator: Evaluator '${evaluator.evaluatorId}' " +
                                "completed and modal shown, record timestamp and stop"
                        }
                        // Recorded either way so kill-switch flips stay seamless.
                        completionStore.recordCompletion()
                        promptsCoordinator.onClaimDone(PromptType.MODAL)
                        claimHeld = false
                        return@withLock
                    }
                    is ModalEvaluator.EvaluationResult.Skipped -> {
                        logcat { "ModalEvaluatorCoordinator: Evaluator '${evaluator.evaluatorId}' skipped, continue to next" }
                    }
                }
            }
        } finally {
            // Nothing is stamped: a pass that showed nothing is no evidence a prompt reached the user.
            if (claimHeld) promptsCoordinator.onClaimCancelled(PromptType.MODAL)
        }

        logcat { "ModalEvaluatorCoordinator: Coordination complete for trigger $trigger, no action taken" }
    }
}
