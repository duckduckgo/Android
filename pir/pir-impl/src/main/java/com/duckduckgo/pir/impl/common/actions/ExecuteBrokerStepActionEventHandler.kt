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
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStep
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStep.EmailConfirmationStep
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStep.OptOutStep
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStep.ScanStep
import com.duckduckgo.pir.impl.common.PirJob
import com.duckduckgo.pir.impl.common.PirRunStateHandler
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerOptOutStageCaptchaSolved
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerOptOutStageSubmit
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerScanActionStarted
import com.duckduckgo.pir.impl.common.actions.EventHandler.Next
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.BrokerStepCompleted
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.BrokerStepCompleted.StepStatus
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.ExecuteBrokerStepAction
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.PirStageStatus
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.SideEffect.AwaitCaptchaSolution
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.SideEffect.GetEmailForProfile
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.SideEffect.LoadUrl
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.SideEffect.PushJsAction
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.State
import com.duckduckgo.pir.impl.common.toParams
import com.duckduckgo.pir.impl.models.ProfileQuery
import com.duckduckgo.pir.impl.pixels.PirStage
import com.duckduckgo.pir.impl.scripts.models.BrokerAction
import com.duckduckgo.pir.impl.scripts.models.BrokerAction.Click
import com.duckduckgo.pir.impl.scripts.models.BrokerAction.EmailConfirmation
import com.duckduckgo.pir.impl.scripts.models.BrokerAction.Expectation
import com.duckduckgo.pir.impl.scripts.models.BrokerAction.FillForm
import com.duckduckgo.pir.impl.scripts.models.BrokerAction.GetCaptchaInfo
import com.duckduckgo.pir.impl.scripts.models.BrokerAction.SolveCaptcha
import com.duckduckgo.pir.impl.scripts.models.DataSource.EXTRACTED_PROFILE
import com.duckduckgo.pir.impl.scripts.models.PirError
import com.duckduckgo.pir.impl.scripts.models.PirScriptRequestData
import com.duckduckgo.pir.impl.scripts.models.PirScriptRequestData.UserProfile
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlin.reflect.KClass

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = EventHandler::class,
)
class ExecuteBrokerStepActionEventHandler @Inject constructor(
    private val pirRunStateHandler: PirRunStateHandler,
    private val currentTimeProvider: CurrentTimeProvider,
) : EventHandler {
    override val event: KClass<out Event> = ExecuteBrokerStepAction::class

    override suspend fun invoke(
        state: State,
        event: Event,
    ): Next {
        /**
         * If we have executed ALL actions for the broker, we have successfylly completed the run for the broker.
         * If there are remaining actions:
         *  - If the action needs an email from the profile and no email is present, we get one [GetEmailForProfile]
         *  - If the action is a click or expectation, we need to add a short delay.
         *  - If the action is EmailConfirmation, we need to execute a BE request to do it via [AwaitEmailConfirmation]
         *  - If the action is SolveCaptcha AND actionRequestData is NOT [PirScriptRequestData.SolveCaptcha] (no solution yet),
         *      we execute a BE request via [AwaitCaptchaSolution]
         *  - If the action is SolveCaptcha AND actionRequestData is already [PirScriptRequestData.SolveCaptcha],
         *      we are ready to push the action to js layer
         *  - For any other action, we push it to the js layer via [PushJsAction]
         */
        val currentBrokerStep = state.brokerStepsToExecute[state.currentBrokerStepIndex]
        val requestData = (event as ExecuteBrokerStepAction).actionRequestData

        return if (state.currentActionIndex == currentBrokerStep.step.actions.size) {
            Next(
                nextState = state,
                nextEvent = BrokerStepCompleted(needsEmailConfirmation = false, stepStatus = StepStatus.Success),
            )
        } else {
            val actionToExecute = currentBrokerStep.step.actions[state.currentActionIndex]

            if ((currentBrokerStep is OptOutStep || currentBrokerStep is EmailConfirmationStep) &&
                actionToExecute.needsEmail &&
                !hasEmail(currentBrokerStep)
            ) {
                val extractedProfile = when (currentBrokerStep) {
                    is OptOutStep -> {
                        currentBrokerStep.profileToOptOut
                    }

                    is EmailConfirmationStep -> {
                        currentBrokerStep.profileToOptOut
                    }

                    else -> null // Invalid state
                }

                Next(
                    nextState = state.copy(
                        stageStatus = PirStageStatus(
                            currentStage = PirStage.EMAIL_GENERATE,
                            stageStartMs = currentTimeProvider.currentTimeMillis(),
                        ),
                    ),
                    sideEffect =
                    GetEmailForProfile(
                        actionId = actionToExecute.id,
                        brokerName = currentBrokerStep.broker.name,
                        extractedProfile = extractedProfile!!,
                        profileQuery = state.profileQuery,
                    ),
                )
            } else {
                var pushDelay = 0L
                // Adding a delay here similar to macOS - to ensure the site completes loading before executing anything.
                if (actionToExecute is Click || actionToExecute is Expectation) {
                    pushDelay = 10_000
                }

                // Adding a temporary delay to potentially workaround captcha for optouts
                if ((state.runType == PirJob.RunType.OPTOUT || state.runType == PirJob.RunType.EMAIL_CONFIRMATION) &&
                    actionToExecute is BrokerAction.FillForm
                ) {
                    pushDelay = 5_000
                }

                if (currentBrokerStep is OptOutStep && actionToExecute is EmailConfirmation) {
                    pirRunStateHandler.handleState(
                        BrokerOptOutStageSubmit(
                            broker = currentBrokerStep.broker,
                            actionID = actionToExecute.id,
                            attemptId = state.attemptId,
                            durationMs = currentTimeProvider.currentTimeMillis() - state.stageStatus.stageStartMs,
                            currentActionAttemptCount = state.actionRetryCount + 1,
                        ),
                    )
                    Next(
                        nextState = state.copy(
                            stageStatus = PirStageStatus(
                                currentStage = PirStage.EMAIL_CONFIRM_HALTED,
                                stageStartMs = currentTimeProvider.currentTimeMillis(),
                            ),
                        ),
                        nextEvent =
                        BrokerStepCompleted(
                            needsEmailConfirmation = true,
                            stepStatus = StepStatus.Success,
                        ),
                    )
                } else if (currentBrokerStep is EmailConfirmationStep && actionToExecute is EmailConfirmation) {
                    val confirmationLink = currentBrokerStep.emailConfirmationJob.linkFetchData.emailConfirmationLink
                    if (confirmationLink.isEmpty()) {
                        // This is an invalid state. We should not be here if we don't have a confirmation link
                        Next(
                            nextState = state,
                            nextEvent =
                            BrokerStepCompleted(
                                needsEmailConfirmation = true,
                                stepStatus = StepStatus.Failure(
                                    error = PirError.Unknown(""),
                                ),
                            ),
                        )
                    } else {
                        Next(
                            nextState = state.copy(
                                pendingUrl = confirmationLink,
                            ),
                            sideEffect = LoadUrl(
                                url = confirmationLink,
                            ),
                        )
                    }
                } else if (actionToExecute is SolveCaptcha && requestData !is PirScriptRequestData.SolveCaptcha) {
                    Next(
                        nextState = state.copy(
                            stageStatus = PirStageStatus(
                                currentStage = PirStage.CAPTCHA_SOLVE,
                                stageStartMs = currentTimeProvider.currentTimeMillis(),
                            ),
                        ),
                        sideEffect =
                        AwaitCaptchaSolution(
                            actionId = actionToExecute.id,
                            brokerName = currentBrokerStep.broker.name,
                            transactionID = state.transactionID,
                            attempt = 0,
                        ),
                    )
                } else {
                    if (currentBrokerStep is ScanStep) {
                        pirRunStateHandler.handleState(
                            BrokerScanActionStarted(
                                broker = currentBrokerStep.broker,
                                profileQueryId = state.profileQuery.id,
                                currentActionAttemptCount = state.actionRetryCount + 1, // actionRetryCount starts at 0
                                currentAction = actionToExecute,
                            ),
                        )
                    } else if (currentBrokerStep is OptOutStep && actionToExecute is SolveCaptcha) {
                        pirRunStateHandler.handleState(
                            BrokerOptOutStageCaptchaSolved(
                                broker = currentBrokerStep.broker,
                                actionID = currentBrokerStep.step.actions[state.currentActionIndex].id,
                                attemptId = state.attemptId,
                                durationMs = currentTimeProvider.currentTimeMillis() - state.stageStatus.stageStartMs,
                                currentActionAttemptCount = state.actionRetryCount + 1,
                            ),
                        )
                    }

                    val nextState = if (actionToExecute is GetCaptchaInfo) {
                        state.copy(
                            stageStatus = PirStageStatus(
                                currentStage = PirStage.CAPTCHA_PARSE,
                                stageStartMs = currentTimeProvider.currentTimeMillis(),
                            ),
                        )
                    } else if (actionToExecute is Expectation) {
                        state.copy(
                            stageStatus = PirStageStatus(
                                currentStage = PirStage.SUBMIT,
                                stageStartMs = currentTimeProvider.currentTimeMillis(),
                            ),
                        )
                    } else if (actionToExecute is FillForm || actionToExecute is Click) {
                        state.copy(
                            stageStatus = PirStageStatus(
                                currentStage = PirStage.FILL_FORM,
                                stageStartMs = currentTimeProvider.currentTimeMillis(),
                            ),
                        )
                    } else {
                        state
                    }

                    Next(
                        nextState = nextState,
                        sideEffect =
                        PushJsAction(
                            actionToExecute.id,
                            actionToExecute,
                            pushDelay,
                            completeRequestData(currentBrokerStep, actionToExecute, state.profileQuery, requestData),
                        ),
                    )
                }
            }
        }
    }

    private fun hasEmail(brokerStep: BrokerStep): Boolean {
        return if (brokerStep is OptOutStep) {
            brokerStep.profileToOptOut.email.isNotEmpty()
        } else if (brokerStep is EmailConfirmationStep) {
            brokerStep.profileToOptOut.email.isNotEmpty()
        } else {
            false
        }
    }

    private fun completeRequestData(
        brokerStep: BrokerStep,
        actionToExecute: BrokerAction,
        profileQuery: ProfileQuery,
        requestData: PirScriptRequestData,
    ): PirScriptRequestData {
        val extractedProfile = if (brokerStep is OptOutStep && actionToExecute.dataSource == EXTRACTED_PROFILE &&
            (requestData as UserProfile).extractedProfile == null
        ) {
            brokerStep.profileToOptOut
        } else if (brokerStep is EmailConfirmationStep && actionToExecute.dataSource == EXTRACTED_PROFILE &&
            (requestData as UserProfile).extractedProfile == null
        ) {
            brokerStep.profileToOptOut
        } else {
            null
        }

        return if (extractedProfile != null && requestData is UserProfile) {
            UserProfile(
                userProfile = requestData.userProfile,
                extractedProfile = extractedProfile.toParams(profileQuery.fullName),
            )
        } else {
            requestData
        }
    }
}
