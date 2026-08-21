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

package com.duckduckgo.pir.impl.common.actions

import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStep.EmailConfirmationStep
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStep.OptOutStep
import com.duckduckgo.pir.impl.common.PirJob.RunType
import com.duckduckgo.pir.impl.common.PirJob.RunType.EMAIL_CONFIRMATION
import com.duckduckgo.pir.impl.common.PirJob.RunType.SCHEDULED
import com.duckduckgo.pir.impl.common.PirRunStateHandler
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerOptOutStageValidate
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerRecordEmailConfirmationCompleted
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerRecordOptOutFailed
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerRecordOptOutSubmitted
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerScanFailed
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerScanSuccess
import com.duckduckgo.pir.impl.common.actions.EventHandler.Next
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.BrokerStepCompleted
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.BrokerStepCompleted.StepStatus
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.BrokerStepCompleted.StepStatus.Failure
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.BrokerStepCompleted.StepStatus.Success
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.PirStageStatus
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.SideEffect.CompleteExecution
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.State
import com.duckduckgo.pir.impl.pixels.PirStage
import com.duckduckgo.pir.impl.scripts.models.BrokerAction
import com.duckduckgo.pir.impl.scripts.models.getCategory
import com.duckduckgo.pir.impl.scripts.models.getDetails
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlin.reflect.KClass

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = EventHandler::class,
)
class BrokerStepCompletedEventHandler @Inject constructor(
    private val pirRunStateHandler: PirRunStateHandler,
    private val currentTimeProvider: CurrentTimeProvider,
) : EventHandler {
    override val event: KClass<out Event> = BrokerStepCompleted::class

    override suspend fun invoke(
        state: State,
        event: Event,
    ): Next {
        val completedEvent = event as BrokerStepCompleted
        val currentBrokerStep = state.brokerStep

        if (completedEvent.needsEmailConfirmation) {
            pirRunStateHandler.handleState(
                PirRunStateHandler.PirRunState.BrokerRecordEmailConfirmationNeeded(
                    broker = currentBrokerStep.broker,
                    extractedProfile = (currentBrokerStep as OptOutStep).profileToOptOut,
                    attemptId = state.attemptId,
                    lastActionId = currentBrokerStep.step.actions.getOrNull(state.currentActionIndex)?.id.orEmpty(),
                    durationMs = currentTimeProvider.currentTimeMillis() - state.stageStatus.stageStartMs,
                    currentActionAttemptCount = state.actionRetryCount + 1,
                    generatedEmail = state.generatedEmailData?.emailAddress,
                ),
            )
        } else {
            // Now we emit pixels related to the Broker step
            emitBrokerStepCompletePixel(
                state = state,
                totalTimeMillis = currentTimeProvider.currentTimeMillis() - state.brokerStepStartTime,
                stepStatus = completedEvent.stepStatus,
            )
        }

        // A runner executes exactly one broker step, so completing it completes the run.
        return Next(
            nextState =
            state.copy(
                actionRetryCount = 0,
                generatedEmailData = null,
                emailExtractedData = emptyMap(),
                stageStatus = PirStageStatus(
                    currentStage = PirStage.VALIDATE,
                    stageStartMs = currentTimeProvider.currentTimeMillis(),
                ),
            ),
            sideEffect = CompleteExecution,
        )
    }

    private suspend fun emitBrokerStepCompletePixel(
        state: State,
        totalTimeMillis: Long,
        stepStatus: StepStatus,
    ) {
        val currentBrokerStep = state.brokerStep
        val brokerStartTime = state.brokerStepStartTime
        val isSuccess = stepStatus is Success
        val lastAction = if (isSuccess) {
            currentBrokerStep.step.actions.getLastActionForSuccess(state.currentActionIndex)
        } else {
            currentBrokerStep.step.actions.getLastActionForFailure(state.currentActionIndex)
        }

        when (state.runType) {
            RunType.MANUAL, SCHEDULED -> {
                val isManual = state.runType == RunType.MANUAL
                if (lastAction == null) return

                if (isSuccess) {
                    pirRunStateHandler.handleState(
                        BrokerScanSuccess(
                            broker = currentBrokerStep.broker,
                            profileQueryId = state.profileQuery.id,
                            eventTimeInMillis = currentTimeProvider.currentTimeMillis(),
                            totalTimeMillis = totalTimeMillis,
                            startTimeInMillis = brokerStartTime,
                            isManualRun = isManual,
                            lastAction = lastAction,
                        ),
                    )
                } else {
                    val failure = stepStatus as Failure

                    pirRunStateHandler.handleState(
                        BrokerScanFailed(
                            broker = currentBrokerStep.broker,
                            profileQueryId = state.profileQuery.id,
                            eventTimeInMillis = currentTimeProvider.currentTimeMillis(),
                            totalTimeMillis = totalTimeMillis,
                            startTimeInMillis = brokerStartTime,
                            isManualRun = isManual,
                            errorCategory = failure.error.getCategory(),
                            errorDetails = failure.error.getDetails(),
                            failedAction = lastAction,
                        ),
                    )
                }
            }

            RunType.OPTOUT -> {
                val currentOptOutStep = currentBrokerStep as OptOutStep
                if (lastAction == null) return

                if (isSuccess) {
                    pirRunStateHandler.handleState(
                        BrokerOptOutStageValidate(
                            broker = currentBrokerStep.broker,
                            actionID = lastAction.id,
                            attemptId = state.attemptId,
                            durationMs = currentTimeProvider.currentTimeMillis() - state.stageStatus.stageStartMs,
                            currentActionAttemptCount = state.actionRetryCount + 1,
                        ),
                    )
                    pirRunStateHandler.handleState(
                        BrokerRecordOptOutSubmitted(
                            broker = currentBrokerStep.broker,
                            extractedProfile = currentOptOutStep.profileToOptOut,
                            attemptId = state.attemptId,
                            startTimeInMillis = state.brokerStepStartTime,
                            endTimeInMillis = currentTimeProvider.currentTimeMillis(),
                            emailPattern = state.generatedEmailData?.pattern,
                        ),
                    )
                } else {
                    pirRunStateHandler.handleState(
                        BrokerRecordOptOutFailed(
                            broker = currentBrokerStep.broker,
                            extractedProfile = currentOptOutStep.profileToOptOut,
                            startTimeInMillis = state.brokerStepStartTime,
                            endTimeInMillis = currentTimeProvider.currentTimeMillis(),
                            attemptId = state.attemptId,
                            failedAction = lastAction,
                            stage = state.stageStatus.currentStage,
                            emailPattern = state.generatedEmailData?.pattern,
                        ),
                    )
                }
            }

            EMAIL_CONFIRMATION -> {
                val currentOptOutStep = currentBrokerStep as EmailConfirmationStep
                pirRunStateHandler.handleState(
                    BrokerRecordEmailConfirmationCompleted(
                        broker = currentBrokerStep.broker,
                        isSuccess = isSuccess,
                        lastActionId = lastAction?.id.orEmpty(),
                        totalTimeMillis = totalTimeMillis,
                        extractedProfile = currentOptOutStep.profileToOptOut,
                        attemptId = state.attemptId,
                        emailPattern = state.generatedEmailData?.pattern.orEmpty(),
                    ),
                )
            }

            else -> {
                // No op
            }
        }
    }

    private fun List<BrokerAction>.getLastActionForSuccess(currentActionIndex: Int): BrokerAction? {
        // If success, it means we reached currentActionIndex == currentBrokerStep.step.actions.size, so last action would be -1.
        return getOrNull(currentActionIndex - 1)
    }

    private fun List<BrokerAction>.getLastActionForFailure(currentActionIndex: Int): BrokerAction? {
        // Whatever last action that was executed is the last action that failed.
        return getOrNull(currentActionIndex)
    }
}
