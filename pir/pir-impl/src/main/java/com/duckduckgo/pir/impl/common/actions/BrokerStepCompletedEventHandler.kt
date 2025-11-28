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
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.ExecuteNextBrokerStep
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.PirStageStatus
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.State
import com.duckduckgo.pir.impl.pixels.PirStage
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
        val currentBrokerStep = state.brokerStepsToExecute[state.currentBrokerStepIndex]

        if (completedEvent.needsEmailConfirmation) {
            pirRunStateHandler.handleState(
                PirRunStateHandler.PirRunState.BrokerRecordEmailConfirmationNeeded(
                    broker = currentBrokerStep.broker,
                    extractedProfile = (currentBrokerStep as OptOutStep).profileToOptOut,
                    attemptId = state.attemptId,
                    lastActionId = currentBrokerStep.step.actions[state.currentActionIndex].id,
                    durationMs = currentTimeProvider.currentTimeMillis() - state.stageStatus.stageStartMs,
                    tries = state.actionRetryCount + 1,
                ),
            )
        } else {
            // Now we emit pixels related to the Broker step
            emitBrokerStepCompletePixel(
                state = state,
                totalTimeMillis = currentTimeProvider.currentTimeMillis() - state.brokerStepStartTime,
                isSuccess = completedEvent.isSuccess,
            )
        }

        return Next(
            nextState =
            state.copy(
                currentBrokerStepIndex = state.currentBrokerStepIndex + 1,
                actionRetryCount = 0,
                generatedEmailData = null,
                stageStatus = PirStageStatus(
                    currentStage = PirStage.VALIDATE,
                    stageStartMs = currentTimeProvider.currentTimeMillis(),
                ),
            ),
            nextEvent = ExecuteNextBrokerStep,
        )
    }

    private suspend fun emitBrokerStepCompletePixel(
        state: State,
        totalTimeMillis: Long,
        isSuccess: Boolean,
    ) {
        val currentBrokerStep = state.brokerStepsToExecute[state.currentBrokerStepIndex]
        val brokerStartTime = state.brokerStepStartTime
        when (state.runType) {
            RunType.MANUAL, SCHEDULED -> {
                val isManual = state.runType == RunType.MANUAL
                if (isSuccess) {
                    // If success, it means we reached currentBrokerStepIndex == currentBrokerStep.step.actions.size, so last action would be -1.
                    val lastAction = currentBrokerStep.step.actions[state.currentActionIndex - 1]

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
                    // Whatever last action that was executed is the last action that failed.
                    val lastAction = currentBrokerStep.step.actions[state.currentActionIndex]
                    pirRunStateHandler.handleState(
                        BrokerScanFailed(
                            broker = currentBrokerStep.broker,
                            profileQueryId = state.profileQuery.id,
                            eventTimeInMillis = currentTimeProvider.currentTimeMillis(),
                            totalTimeMillis = totalTimeMillis,
                            startTimeInMillis = brokerStartTime,
                            isManualRun = isManual,
                            errorCategory = "", // TODO: Integrate failure later on
                            errorDetails = "", // TODO: Integrate failure later on
                            failedAction = lastAction,
                        ),
                    )
                }
            }

            RunType.OPTOUT -> {
                val currentOptOutStep = currentBrokerStep as OptOutStep
                if (isSuccess) {
                    pirRunStateHandler.handleState(
                        BrokerOptOutStageValidate(
                            broker = currentBrokerStep.broker,
                            actionID = currentBrokerStep.step.actions[state.currentActionIndex].id,
                            attemptId = state.attemptId,
                            durationMs = currentTimeProvider.currentTimeMillis() - state.stageStatus.stageStartMs,
                            tries = state.actionRetryCount + 1,
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
                    // Whatever last action that was executed is the last action that failed.
                    val lastAction = currentBrokerStep.step.actions[state.currentActionIndex]

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
                        // Success means we finished all steps and reaching here means that index has been incremented. If error, we don't increment.
                        lastActionId = if (isSuccess) {
                            currentOptOutStep.step.actions[state.currentActionIndex - 1]
                        } else {
                            currentOptOutStep.step.actions[state.currentActionIndex]
                        }.id,
                        totalTimeMillis = totalTimeMillis,
                        extractedProfileId = currentBrokerStep.emailConfirmationJob.extractedProfileId,
                    ),
                )
            }

            else -> {
                // No op
            }
        }
    }
}
