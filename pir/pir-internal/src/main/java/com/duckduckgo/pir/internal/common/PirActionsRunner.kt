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

package com.duckduckgo.pir.internal.common

import android.content.Context
import android.webkit.WebView
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.pir.internal.common.BrokerStepsParser.BrokerStep
import com.duckduckgo.pir.internal.common.BrokerStepsParser.BrokerStep.OptOutStep
import com.duckduckgo.pir.internal.common.NativeBrokerActionHandler.NativeAction
import com.duckduckgo.pir.internal.common.NativeBrokerActionHandler.NativeAction.GetCaptchaSolutionStatus
import com.duckduckgo.pir.internal.common.NativeBrokerActionHandler.NativeAction.SubmitCaptchaInfo
import com.duckduckgo.pir.internal.common.NativeBrokerActionHandler.NativeActionResult.Failure
import com.duckduckgo.pir.internal.common.NativeBrokerActionHandler.NativeActionResult.Success
import com.duckduckgo.pir.internal.common.NativeBrokerActionHandler.NativeActionResult.Success.NativeSuccessData
import com.duckduckgo.pir.internal.common.NativeBrokerActionHandler.NativeActionResult.Success.NativeSuccessData.CaptchaSolutionStatus
import com.duckduckgo.pir.internal.common.NativeBrokerActionHandler.NativeActionResult.Success.NativeSuccessData.CaptchaSolutionStatus.CaptchaStatus.Ready
import com.duckduckgo.pir.internal.common.NativeBrokerActionHandler.NativeActionResult.Success.NativeSuccessData.CaptchaTransactionIdReceived
import com.duckduckgo.pir.internal.common.NativeBrokerActionHandler.NativeActionResult.Success.NativeSuccessData.Email
import com.duckduckgo.pir.internal.common.PirActionsRunnerFactory.RunType
import com.duckduckgo.pir.internal.common.PirJobConstants.DBP_INITIAL_URL
import com.duckduckgo.pir.internal.common.PirJobConstants.RECOVERY_URL
import com.duckduckgo.pir.internal.common.PirRunStateHandler.PirRunState.BrokerManualScanCompleted
import com.duckduckgo.pir.internal.common.PirRunStateHandler.PirRunState.BrokerManualScanStarted
import com.duckduckgo.pir.internal.common.PirRunStateHandler.PirRunState.BrokerOptOutActionFailed
import com.duckduckgo.pir.internal.common.PirRunStateHandler.PirRunState.BrokerOptOutActionSucceeded
import com.duckduckgo.pir.internal.common.PirRunStateHandler.PirRunState.BrokerOptOutCompleted
import com.duckduckgo.pir.internal.common.PirRunStateHandler.PirRunState.BrokerOptOutStarted
import com.duckduckgo.pir.internal.common.PirRunStateHandler.PirRunState.BrokerRecordOptOutCompleted
import com.duckduckgo.pir.internal.common.PirRunStateHandler.PirRunState.BrokerRecordOptOutStarted
import com.duckduckgo.pir.internal.common.PirRunStateHandler.PirRunState.BrokerScanActionFailed
import com.duckduckgo.pir.internal.common.PirRunStateHandler.PirRunState.BrokerScanActionSucceeded
import com.duckduckgo.pir.internal.common.PirRunStateHandler.PirRunState.BrokerScheduledScanCompleted
import com.duckduckgo.pir.internal.common.PirRunStateHandler.PirRunState.BrokerScheduledScanStarted
import com.duckduckgo.pir.internal.common.RealPirActionsRunner.Command.AwaitCaptchaSolution
import com.duckduckgo.pir.internal.common.RealPirActionsRunner.Command.AwaitEmailConfirmation
import com.duckduckgo.pir.internal.common.RealPirActionsRunner.Command.BrokerCompleted
import com.duckduckgo.pir.internal.common.RealPirActionsRunner.Command.CompleteExecution
import com.duckduckgo.pir.internal.common.RealPirActionsRunner.Command.ExecuteBrokerAction
import com.duckduckgo.pir.internal.common.RealPirActionsRunner.Command.GetCaptchaSolution
import com.duckduckgo.pir.internal.common.RealPirActionsRunner.Command.GetEmail
import com.duckduckgo.pir.internal.common.RealPirActionsRunner.Command.HandleBroker
import com.duckduckgo.pir.internal.common.RealPirActionsRunner.Command.HandleNextProfileForBroker
import com.duckduckgo.pir.internal.common.RealPirActionsRunner.Command.Idle
import com.duckduckgo.pir.internal.common.RealPirActionsRunner.Command.LoadUrl
import com.duckduckgo.pir.internal.common.RealPirActionsRunner.Command.SendCaptchaSolution
import com.duckduckgo.pir.internal.scripts.BrokerActionProcessor
import com.duckduckgo.pir.internal.scripts.BrokerActionProcessor.ActionResultListener
import com.duckduckgo.pir.internal.scripts.models.BrokerAction
import com.duckduckgo.pir.internal.scripts.models.BrokerAction.Click
import com.duckduckgo.pir.internal.scripts.models.BrokerAction.EmailConfirmation
import com.duckduckgo.pir.internal.scripts.models.BrokerAction.Expectation
import com.duckduckgo.pir.internal.scripts.models.BrokerAction.GetCaptchaInfo
import com.duckduckgo.pir.internal.scripts.models.BrokerAction.SolveCaptcha
import com.duckduckgo.pir.internal.scripts.models.DataSource.EXTRACTED_PROFILE
import com.duckduckgo.pir.internal.scripts.models.ExtractedProfile
import com.duckduckgo.pir.internal.scripts.models.ExtractedProfileParams
import com.duckduckgo.pir.internal.scripts.models.PirErrorReponse
import com.duckduckgo.pir.internal.scripts.models.PirScriptRequestData
import com.duckduckgo.pir.internal.scripts.models.PirScriptRequestData.UserProfile
import com.duckduckgo.pir.internal.scripts.models.PirSuccessResponse
import com.duckduckgo.pir.internal.scripts.models.PirSuccessResponse.ClickResponse
import com.duckduckgo.pir.internal.scripts.models.PirSuccessResponse.ExpectationResponse
import com.duckduckgo.pir.internal.scripts.models.PirSuccessResponse.ExtractedResponse
import com.duckduckgo.pir.internal.scripts.models.PirSuccessResponse.FillFormResponse
import com.duckduckgo.pir.internal.scripts.models.PirSuccessResponse.GetCaptchaInfoResponse
import com.duckduckgo.pir.internal.scripts.models.PirSuccessResponse.GetCaptchaInfoResponse.ResponseData
import com.duckduckgo.pir.internal.scripts.models.PirSuccessResponse.NavigateResponse
import com.duckduckgo.pir.internal.scripts.models.PirSuccessResponse.SolveCaptchaResponse
import com.duckduckgo.pir.internal.scripts.models.ProfileQuery
import com.duckduckgo.pir.internal.scripts.models.asActionType
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import logcat.logcat

interface PirActionsRunner {
    /**
     * This function is responsible for executing the [BrokerStep] passed on its own detached WebView
     *
     * @param profileQuery - Profile to be passed along actions in [BrokerStep]
     * @param brokerSteps - List of [BrokerStep] each containing a broker + actions to be executed.
     */
    suspend fun start(
        profileQuery: ProfileQuery,
        brokerSteps: List<BrokerStep>,
    ): Result<Unit>

    /**
     * This function is responsible for executing the [BrokerStep] passed on the passed [webView].
     * This initializes everything necessary on the [webView].
     *
     * @param webView - WebView in which we want to execute the actions on
     * @param profileQuery - Profile to be passed along actions in [BrokerStep]
     * @param brokerSteps - List of [BrokerStep] each containing a broker + actions to be executed.
     */
    suspend fun startOn(
        webView: WebView,
        profileQuery: ProfileQuery,
        brokerSteps: List<BrokerStep>,
    ): Result<Unit>

    /**
     * Forcefully stops / aborts a runner if it is running.
     */
    fun stop()
}

internal class RealPirActionsRunner(
    private val dispatcherProvider: DispatcherProvider,
    private val pirDetachedWebViewProvider: PirDetachedWebViewProvider,
    private val brokerActionProcessor: BrokerActionProcessor,
    private val context: Context,
    private val pirScriptToLoad: String,
    private val runType: RunType,
    private val currentTimeProvider: CurrentTimeProvider,
    private val nativeBrokerActionHandler: NativeBrokerActionHandler,
    private val pirRunStateHandler: PirRunStateHandler,
) : PirActionsRunner, ActionResultListener {
    private val coroutineScope: CoroutineScope
        get() = CoroutineScope(SupervisorJob() + dispatcherProvider.io())

    private var detachedWebView: WebView? = null
    private val brokersToExecute: MutableList<BrokerStep> = mutableListOf()

    private val commandsFlow = MutableStateFlow<Command>(Idle)
    private var timerJob: ConflatedJob = ConflatedJob()
    private var commandsJob: ConflatedJob = ConflatedJob()

    override suspend fun start(
        profileQuery: ProfileQuery,
        brokerSteps: List<BrokerStep>,
    ): Result<Unit> {
        if (brokerSteps.isEmpty()) {
            logcat { "PIR-RUNNER ($this): No broker steps to execute ${Thread.currentThread().name}" }
            return Result.success(Unit)
        }

        brokersToExecute.clear()
        brokersToExecute.addAll(brokerSteps)

        initializeDetachedWebView(profileQuery)

        return awaitResult()
    }

    private suspend fun initializeDetachedWebView(profileQuery: ProfileQuery) {
        withContext(dispatcherProvider.main()) {
            logcat { "PIR-RUNNER (${this@RealPirActionsRunner}): ${Thread.currentThread().name} Brokers to execute $brokersToExecute" }
            logcat { "PIR-RUNNER (${this@RealPirActionsRunner}): ${Thread.currentThread().name} Brokers size: ${brokersToExecute.size}" }
            detachedWebView = pirDetachedWebViewProvider.createInstance(
                context,
                pirScriptToLoad,
                onPageLoaded = {
                    onLoadingComplete(it, profileQuery)
                },
                onPageLoadFailed = {
                    onLoadingFailed(it)
                },
            )

            initializeRunner()
        }
    }

    override suspend fun startOn(
        webView: WebView,
        profileQuery: ProfileQuery,
        brokerSteps: List<BrokerStep>,
    ): Result<Unit> {
        if (brokerSteps.isEmpty()) {
            logcat { "PIR-RUNNER ($this): No broker steps to execute ${Thread.currentThread().name}" }
            return Result.success(Unit)
        }

        brokersToExecute.clear()
        brokersToExecute.addAll(brokerSteps)

        withContext(dispatcherProvider.main()) {
            logcat { "PIR-RUNNER (${this@RealPirActionsRunner}): ${Thread.currentThread().name} Brokers to execute $brokersToExecute" }
            logcat { "PIR-RUNNER (${this@RealPirActionsRunner}): ${Thread.currentThread().name} Brokers size: ${brokersToExecute.size}" }
            detachedWebView = pirDetachedWebViewProvider.setupWebView(
                webView,
                pirScriptToLoad,
                onPageLoaded = {
                    onLoadingComplete(it, profileQuery)
                },
                onPageLoadFailed = {
                    onLoadingFailed(it)
                },
            )
            initializeRunner()
        }
        return awaitResult()
    }

    private fun initializeRunner() {
        brokerActionProcessor.register(detachedWebView!!, this@RealPirActionsRunner)
        detachedWebView!!.loadUrl(DBP_INITIAL_URL)
    }

    private suspend fun awaitResult(): Result<Unit> {
        return suspendCoroutine { continuation ->
            commandsJob += coroutineScope.launch {
                commandsFlow.asStateFlow().collect {
                    handleCommand(it, continuation)
                }
            }
        }
    }

    private fun onLoadingComplete(
        url: String?,
        profileQuery: ProfileQuery,
    ) {
        logcat { "PIR-RUNNER ($this): finished loading $url and latest action ${commandsFlow.value}" }
        if (url == null) {
            return
        }

        // A completed initial scan means we are ready to run the scan for the brokers
        if (url == DBP_INITIAL_URL) {
            nextCommand(
                HandleBroker(
                    commandsFlow.value.state.copy(
                        currentBrokerIndex = 0,
                        currentActionIndex = 0,
                        profileQuery = profileQuery,
                    ),
                ),
            )
        } else if (commandsFlow.value is Command.RecoverFromFailedUrlLoad && url == RECOVERY_URL) {
            logcat { "PIR-RUNNER ($this): Completing broker due to recovery" }
            nextCommand(BrokerCompleted(commandsFlow.value.state, isSuccess = false))
        } else if (commandsFlow.value is LoadUrl) {
            // If the current action is still navigate, it means we just finished loading and we can proceed to next action.
            // Sometimes the loaded url gets redirected to another url (could be different domain too) so we can't really check here.
            logcat { "PIR-RUNNER ($this): Completed loading for ${commandsFlow.value}" }
            nextCommand(
                ExecuteBrokerAction(
                    commandsFlow.value.state.copy(
                        currentActionIndex = commandsFlow.value.state.currentActionIndex + 1,
                    ),
                ),
            )
        } else {
            logcat { "PIR-RUNNER ($this): Ignoring $url as next action has been pushed" }
        }
    }

    private fun onLoadingFailed(url: String?) {
        logcat { "PIR-RUNNER: Recovering from loading $url failure" }
        coroutineScope.launch(dispatcherProvider.main()) {
            nextCommand(
                Command.RecoverFromFailedUrlLoad(commandsFlow.value.state),
            )
        }
    }

    private suspend fun handleCommand(
        command: Command,
        continuationResult: Continuation<Result<Unit>>,
    ) {
        logcat { "PIR-RUNNER ($this): Handle command: $command" }
        when (command) {
            Idle -> {} // Do nothing
            is HandleBroker -> handleBrokerAction(command.state)
            is ExecuteBrokerAction -> executeBrokerAction(command.state, command.actionRequestData)
            is CompleteExecution -> {
                continuationResult.resume(Result.success(Unit))
                cleanUpRunner()
            }
            // TODO add loading timeout
            is LoadUrl -> coroutineScope.launch(dispatcherProvider.main()) {
                detachedWebView!!.loadUrl(command.urlToLoad)
            }

            is Command.RecoverFromFailedUrlLoad -> coroutineScope.launch(dispatcherProvider.main()) {
                detachedWebView!!.loadUrl(RECOVERY_URL)
            }

            is HandleNextProfileForBroker -> handleNextProfileForBroker(command.state)
            is BrokerCompleted -> handleBrokerCompleted(command.state, command.isSuccess)
            is GetEmail -> handleGetEmail(command.state)
            is AwaitEmailConfirmation -> handleEmailConfirmation(
                command.state,
                command.pollingIntervalSeconds,
            )

            is GetCaptchaSolution -> handleGetCaptchaSolution(
                command.state,
                command.responseData,
                command.isRetry,
            )

            is AwaitCaptchaSolution -> handleAwaitCaptchaSolution(
                command.state,
                command.pollingIntervalSeconds,
                command.retries,
                command.attempt,
            )

            is SendCaptchaSolution -> handleSendCaptchaSolution(command.state, command.callback)
        }
    }

    private suspend fun handleNextProfileForBroker(state: State) {
        // We reset action to 0 and update the profile state to the next profile
        val newState = state.copy(
            currentActionIndex = 0,
            extractedProfileState = state.extractedProfileState.copy(
                currentExtractedProfileIndex = state.extractedProfileState.currentExtractedProfileIndex + 1,
            ),
        )

        // Should only be run for opt out really
        if (runType == RunType.OPTOUT) {
            // Signal start for current run.
            pirRunStateHandler.handleState(
                BrokerRecordOptOutStarted(
                    brokerName = brokersToExecute[state.currentBrokerIndex].brokerName,
                    extractedProfile = newState.extractedProfileState.extractedProfile[state.extractedProfileState.currentExtractedProfileIndex],
                ),
            )
        }
        // Restart for broker but with different profile
        nextCommand(
            ExecuteBrokerAction(
                state = newState,
            ),
        )
    }

    private fun nextCommand(command: Command) {
        coroutineScope.launch {
            commandsFlow.emit(command)
        }
    }

    private suspend fun handleSendCaptchaSolution(
        state: State,
        callback: String,
    ) {
        withContext(dispatcherProvider.main()) {
            detachedWebView?.evaluateJavascript(callback, null)
        }
        nextCommand(
            ExecuteBrokerAction(
                state = state.copy(
                    currentActionIndex = state.currentActionIndex + 1,
                ),
            ),
        )
    }

    private suspend fun handleAwaitCaptchaSolution(
        state: State,
        pollingIntervalSeconds: Int,
        retries: Int,
        attempt: Int,
    ) {
        val broker = brokersToExecute[state.currentBrokerIndex]

        if (state.transactionID.isEmpty()) {
            onError(
                PirErrorReponse(
                    actionID = broker.actions[state.currentActionIndex].id,
                    message = "Unable to solve captcha",
                ),
            )
        } else {
            nativeBrokerActionHandler.pushAction(
                GetCaptchaSolutionStatus(
                    actionId = broker.actions[state.currentActionIndex].id,
                    transactionID = state.transactionID,
                ),
            ).run {
                if (this is Success && this.data is CaptchaSolutionStatus) {
                    when (this.data.status) {
                        is Ready -> nextCommand(
                            ExecuteBrokerAction(
                                state = state,
                                actionRequestData = PirScriptRequestData.SolveCaptcha(
                                    token = this.data.status.token,
                                ),
                            ),
                        )

                        else -> {
                            if (attempt == retries) {
                                onError(
                                    PirErrorReponse(
                                        actionID = broker.actions[state.currentActionIndex].id,
                                        message = "Unable to solve captcha",
                                    ),
                                )
                            } else {
                                delay(pollingIntervalSeconds * 1000L)
                                nextCommand(
                                    AwaitCaptchaSolution(
                                        state = state,
                                        attempt = attempt + 1,
                                    ),
                                )
                            }
                        }
                    }
                } else if (this is Failure) {
                    onError(
                        PirErrorReponse(
                            actionID = broker.actions[state.currentActionIndex].id,
                            message = "Unable to solve captcha",
                        ),
                    )
                }
            }
        }
    }

    private suspend fun handleGetCaptchaSolution(
        state: State,
        responseData: ResponseData,
        isRetry: Boolean,
    ) {
        val broker = brokersToExecute[state.currentBrokerIndex]
        nativeBrokerActionHandler.pushAction(
            SubmitCaptchaInfo(
                actionId = broker.actions[state.currentActionIndex].id,
                siteKey = responseData.siteKey,
                url = responseData.url,
                type = responseData.type,
            ),
        ).also {
            if (it is Success) {
                nextCommand(
                    ExecuteBrokerAction(
                        state = state.copy(
                            currentActionIndex = state.currentActionIndex + 1,
                            transactionID = (it.data as CaptchaTransactionIdReceived).transactionID,
                        ),
                    ),
                )
            } else if (it is Failure && !isRetry && it.retryNativeAction) {
                delay(60_000)
                nextCommand(
                    GetCaptchaSolution(
                        state = state,
                        responseData = responseData,
                        isRetry = true,
                    ),
                )
            } else {
                val result = it as Failure
                onError(
                    PirErrorReponse(
                        actionID = it.actionId,
                        message = result.message,
                    ),
                )
            }
        }
    }

    private suspend fun handleEmailConfirmation(
        state: State,
        pollingIntervalSeconds: Float,
    ) {
        val broker = brokersToExecute[state.currentBrokerIndex]
        val extractedProfileState = state.extractedProfileState
        if (extractedProfileState.extractedProfile.isNotEmpty()) {
            nativeBrokerActionHandler.pushAction(
                NativeAction.GetEmailStatus(
                    actionId = broker.actions[state.currentActionIndex].id,
                    brokerName = broker.brokerName,
                    email = extractedProfileState.extractedProfile[extractedProfileState.currentExtractedProfileIndex].email!!,
                    pollingIntervalSeconds = pollingIntervalSeconds,
                ),
            ).also {
                if (it is Success) {
                    nextCommand(
                        LoadUrl(
                            state = state,
                            urlToLoad = (it.data as NativeSuccessData.EmailConfirmation).link,
                        ),
                    )
                } else {
                    val result = it as Failure
                    onError(
                        PirErrorReponse(
                            actionID = result.actionId,
                            message = result.message,
                        ),
                    )
                }
            }
        }
    }

    private suspend fun handleGetEmail(state: State) {
        val broker = brokersToExecute[state.currentBrokerIndex]

        nativeBrokerActionHandler.pushAction(
            NativeAction.GetEmail(
                actionId = broker.actions[state.currentActionIndex].id,
                brokerName = broker.brokerName,
            ),
        ).also {
            if (it is Success && state.extractedProfileState.extractedProfile.isNotEmpty()) {
                val extractedProfileState = state.extractedProfileState
                val extractedProfileWithEmail =
                    extractedProfileState.extractedProfile[extractedProfileState.currentExtractedProfileIndex].copy(
                        email = (it.data as Email).email,
                    )
                val updatedList = extractedProfileState.extractedProfile.toMutableList()

                updatedList[extractedProfileState.currentExtractedProfileIndex] =
                    extractedProfileWithEmail

                extractedProfileState.extractedProfile[extractedProfileState.currentExtractedProfileIndex]
                nextCommand(
                    ExecuteBrokerAction(
                        state = state.copy(
                            extractedProfileState = extractedProfileState.copy(
                                extractedProfile = updatedList,
                            ),
                        ),
                        actionRequestData = UserProfile(
                            userProfile = state.profileQuery,
                            extractedProfile = extractedProfileWithEmail.run {
                                ExtractedProfileParams(
                                    name = this.name,
                                    profileUrl = this.profileUrl?.profileUrl,
                                    fullName = state.profileQuery?.fullName,
                                    email = this.email,
                                )
                            },
                        ),
                    ),
                )
            } else {
                val result = it as Failure
                onError(
                    PirErrorReponse(
                        actionID = result.actionId,
                        message = result.message,
                    ),
                )
            }
        }
    }

    private suspend fun handleBrokerAction(state: State) {
        if (state.currentBrokerIndex >= brokersToExecute.size) {
            nextCommand(CompleteExecution)
        } else {
            // Entry point of execution for a Broker
            brokersToExecute.get(state.currentBrokerIndex).let {
                emitBrokerStartPixel(it)

                nextCommand(
                    ExecuteBrokerAction(
                        commandsFlow.value.state.copy(
                            currentActionIndex = 0,
                            brokerStartTime = currentTimeProvider.currentTimeMillis(),
                            extractedProfileState = if (it is OptOutStep) {
                                ExtractedProfileState(
                                    currentExtractedProfileIndex = 0,
                                    extractedProfile = it.profilesToOptOut,
                                )
                            } else {
                                ExtractedProfileState()
                            },
                        ),
                    ),
                )
            }
        }
    }

    private suspend fun handleBrokerCompleted(
        state: State,
        isSuccess: Boolean,
    ) {
        if (state.extractedProfileState.currentExtractedProfileIndex < state.extractedProfileState.extractedProfile.size - 1) {
            if (runType == RunType.OPTOUT) {
                // Signal complete for previous run.
                pirRunStateHandler.handleState(
                    BrokerRecordOptOutCompleted(
                        brokerName = brokersToExecute[state.currentBrokerIndex].brokerName,
                        extractedProfile = state.extractedProfileState.extractedProfile[state.extractedProfileState.currentExtractedProfileIndex],
                        startTimeInMillis = state.brokerStartTime,
                        endTimeInMillis = currentTimeProvider.currentTimeMillis(),
                        isSubmitSuccess = isSuccess,
                    ),
                )
            }

            // Broker is not yet completed as another profile can be run
            nextCommand(
                HandleNextProfileForBroker(
                    state = state,
                ),
            )
        } else {
            // Exit point of execution for a Blocker
            emitBrokerCompletePixel(
                brokerName = brokersToExecute[state.currentBrokerIndex].brokerName,
                state = state,
                startTimeInMillis = state.brokerStartTime,
                totalTimeMillis = currentTimeProvider.currentTimeMillis() - state.brokerStartTime,
                isSuccess = isSuccess,
            )
            nextCommand(
                HandleBroker(
                    state = state.copy(
                        currentBrokerIndex = state.currentBrokerIndex + 1,
                    ),
                ),
            )
        }
    }

    private suspend fun emitBrokerStartPixel(brokerStep: BrokerStep) {
        when (runType) {
            RunType.MANUAL -> pirRunStateHandler.handleState(
                BrokerManualScanStarted(
                    brokerStep.brokerName,
                    currentTimeProvider.currentTimeMillis(),
                ),
            )

            RunType.SCHEDULED -> pirRunStateHandler.handleState(
                BrokerScheduledScanStarted(
                    brokerStep.brokerName,
                    currentTimeProvider.currentTimeMillis(),
                ),
            )

            RunType.OPTOUT -> {
                // When we get here it means we are starting a new process for a new broker
                pirRunStateHandler.handleState(
                    BrokerOptOutStarted(
                        brokerStep.brokerName,
                    ),
                )

                // It also means we are starting it for the first profile. Succeeding profiles are handled in HandleNextProfileForBroker
                pirRunStateHandler.handleState(
                    BrokerRecordOptOutStarted(
                        brokerStep.brokerName,
                        (brokerStep as OptOutStep).profilesToOptOut[0],
                    ),
                )
            }

            else -> {}
        }
    }

    private suspend fun emitBrokerCompletePixel(
        brokerName: String,
        state: State,
        startTimeInMillis: Long,
        totalTimeMillis: Long,
        isSuccess: Boolean,
    ) {
        when (runType) {
            RunType.MANUAL ->
                pirRunStateHandler.handleState(
                    BrokerManualScanCompleted(
                        brokerName = brokerName,
                        eventTimeInMillis = currentTimeProvider.currentTimeMillis(),
                        totalTimeMillis = totalTimeMillis,
                        isSuccess = isSuccess,
                        startTimeInMillis = startTimeInMillis,
                    ),
                )

            RunType.SCHEDULED -> pirRunStateHandler.handleState(
                BrokerScheduledScanCompleted(
                    brokerName = brokerName,
                    eventTimeInMillis = currentTimeProvider.currentTimeMillis(),
                    totalTimeMillis = totalTimeMillis,
                    isSuccess = isSuccess,
                    startTimeInMillis = startTimeInMillis,
                ),
            )

            RunType.OPTOUT -> {
                pirRunStateHandler.handleState(
                    BrokerRecordOptOutCompleted(
                        brokerName = brokerName,
                        startTimeInMillis = startTimeInMillis,
                        endTimeInMillis = currentTimeProvider.currentTimeMillis(),
                        extractedProfile = state.extractedProfileState.extractedProfile[state.extractedProfileState.currentExtractedProfileIndex],
                        isSubmitSuccess = isSuccess,
                    ),
                )
                pirRunStateHandler.handleState(
                    BrokerOptOutCompleted(
                        brokerName = brokerName,
                        startTimeInMillis = startTimeInMillis,
                        endTimeInMillis = currentTimeProvider.currentTimeMillis(),
                    ),
                )
            }

            else -> {}
        }
    }

    private fun executeBrokerAction(
        state: State,
        requestData: PirScriptRequestData,
    ) {
        val currentBroker = brokersToExecute[state.currentBrokerIndex]
        if (state.currentActionIndex == currentBroker.actions.size) {
            nextCommand(
                BrokerCompleted(state, true),
            )
        } else {
            val actionToExecute = currentBroker.actions[state.currentActionIndex]

            if (actionToExecute.needsEmail && !hasEmail(state.extractedProfileState)) {
                nextCommand(GetEmail(state))
            } else {
                // Adding a delay here similar to macOS - to ensure the site completes loading before executing anything.
                if (actionToExecute is Click || actionToExecute is Expectation) {
                    runBlocking(dispatcherProvider.io()) {
                        delay(10_000)
                    }
                }

                if (actionToExecute is EmailConfirmation) {
                    nextCommand(
                        AwaitEmailConfirmation(
                            state = state,
                            pollingIntervalSeconds = actionToExecute.pollingTime.toFloat(),
                        ),
                    )
                } else if (actionToExecute is SolveCaptcha && requestData !is PirScriptRequestData.SolveCaptcha) {
                    nextCommand(
                        AwaitCaptchaSolution(
                            state = state,
                            attempt = 0,
                        ),
                    )
                } else {
                    timerJob.cancel()
                    timerJob += coroutineScope.launch {
                        delay(60000) // 1 minute
                        // IF this timer completes, then timeout was reached
                        val currentState = commandsFlow.value.state
                        kotlin.runCatching {
                            val id =
                                brokersToExecute[currentState.currentBrokerIndex].actions[currentState.currentActionIndex].id
                            onError(
                                PirErrorReponse(
                                    actionID = id,
                                    message = "Local timeout",
                                ),
                            )
                        }
                    }

                    brokerActionProcessor.pushAction(
                        actionToExecute,
                        completeRequestData(state, actionToExecute, requestData),
                    )
                }
            }
        }
    }

    private fun completeRequestData(
        state: State,
        actionToExecute: BrokerAction,
        requestData: PirScriptRequestData,
    ): PirScriptRequestData {
        return if (actionToExecute.dataSource == EXTRACTED_PROFILE && (requestData as UserProfile).extractedProfile == null) {
            val extractedProfileState = state.extractedProfileState
            val extractedProfile =
                extractedProfileState.extractedProfile[extractedProfileState.currentExtractedProfileIndex]

            UserProfile(
                userProfile = requestData.userProfile,
                extractedProfile = extractedProfile.run {
                    ExtractedProfileParams(
                        name = this.name,
                        profileUrl = this.profileUrl?.profileUrl,
                        fullName = state.profileQuery?.fullName,
                        email = this.email,
                    )
                },
            )
        } else {
            requestData
        }
    }

    private fun hasEmail(extractedProfileState: ExtractedProfileState): Boolean {
        return extractedProfileState.extractedProfile[extractedProfileState.currentExtractedProfileIndex].email != null
    }

    private fun cleanUpRunner() {
        timerJob.cancel()
        nextCommand(Idle)
        commandsJob.cancel()
        coroutineScope.launch(dispatcherProvider.main()) {
            detachedWebView?.stopLoading()
            detachedWebView?.loadUrl("about:blank")
            detachedWebView?.evaluateJavascript("window.stop();", null)
            detachedWebView?.destroy()
            logcat { "PIR-RUNNER ($this): Destroyed webview" }
        }
    }

    override fun stop() {
        logcat { "PIR-RUNNER ($this): Stopping and resetting values" }
        cleanUpRunner()
    }

    override fun onSuccess(pirSuccessResponse: PirSuccessResponse) {
        runBlocking(dispatcherProvider.main()) {
            val lastState = commandsFlow.value.state
            val currentBroker = brokersToExecute[lastState.currentBrokerIndex]
            val currentAction = currentBroker.actions[lastState.currentActionIndex]

            if (pirSuccessResponse.actionID == currentAction.id) {
                timerJob.cancel()

                logcat { "PIR-RUNNER (${this@RealPirActionsRunner}): onSuccess: $pirSuccessResponse" }
                if (runType != RunType.OPTOUT) {
                    pirRunStateHandler.handleState(
                        BrokerScanActionSucceeded(
                            currentBroker.brokerName,
                            pirSuccessResponse,
                        ),
                    )
                } else {
                    lastState.extractedProfileState.extractedProfile.get(lastState.extractedProfileState.currentExtractedProfileIndex)
                        .let {
                            pirRunStateHandler.handleState(
                                BrokerOptOutActionSucceeded(
                                    brokerName = currentBroker.brokerName,
                                    extractedProfile = it,
                                    completionTimeInMillis = currentTimeProvider.currentTimeMillis(),
                                    actionType = pirSuccessResponse.actionType,
                                    result = pirSuccessResponse,
                                ),
                            )
                        }
                }
                when (pirSuccessResponse) {
                    is NavigateResponse -> {
                        nextCommand(
                            LoadUrl(
                                urlToLoad = pirSuccessResponse.response.url,
                                state = lastState,
                            ),
                        )
                    }

                    is ExtractedResponse -> {
                        nextCommand(
                            ExecuteBrokerAction(
                                state = lastState.copy(
                                    currentActionIndex = lastState.currentActionIndex + 1,
                                ),
                            ),
                        )
                    }

                    is ClickResponse, is ExpectationResponse -> {
                        nextCommand(
                            ExecuteBrokerAction(
                                state = lastState.copy(
                                    currentActionIndex = lastState.currentActionIndex + 1,
                                ),
                            ),
                        )
                    }

                    is FillFormResponse -> {
                        nextCommand(
                            ExecuteBrokerAction(
                                state = lastState.copy(
                                    currentActionIndex = lastState.currentActionIndex + 1,
                                ),
                            ),
                        )
                    }

                    is GetCaptchaInfoResponse -> {
                        pirSuccessResponse.response?.let {
                            nextCommand(
                                GetCaptchaSolution(
                                    state = lastState,
                                    responseData = it,
                                    isRetry = false,
                                ),
                            )
                        }
                    }

                    is SolveCaptchaResponse -> {
                        nextCommand(
                            SendCaptchaSolution(
                                state = lastState,
                                callback = pirSuccessResponse.response!!.callback.eval,
                            ),
                        )
                    }

                    else -> {
                        logcat { "PIR-RUNNER (${this@RealPirActionsRunner}): Do nothing for $pirSuccessResponse" }
                    }
                }
            } else {
                logcat { "PIR-RUNNER (${this@RealPirActionsRunner}): Runner can't handle $pirSuccessResponse" }
            }
        }
    }

    override fun onError(pirErrorReponse: PirErrorReponse) {
        runBlocking(dispatcherProvider.main()) {
            val lastState = commandsFlow.value.state
            val currentBroker = brokersToExecute[lastState.currentBrokerIndex]
            val currentAction = currentBroker.actions[lastState.currentActionIndex]

            if (pirErrorReponse.actionID == currentAction.id) {
                timerJob.cancel()
                logcat { "PIR-RUNNER (${this@RealPirActionsRunner}): onError: $pirErrorReponse" }
                if (runType != RunType.OPTOUT) {
                    pirRunStateHandler.handleState(
                        BrokerScanActionFailed(
                            brokerName = currentBroker.brokerName,
                            actionType = currentAction.asActionType(),
                            pirErrorReponse = pirErrorReponse,
                        ),
                    )
                } else {
                    lastState.extractedProfileState.extractedProfile?.get(lastState.extractedProfileState.currentExtractedProfileIndex)
                        ?.let {
                            pirRunStateHandler.handleState(
                                BrokerOptOutActionFailed(
                                    brokerName = currentBroker.brokerName,
                                    extractedProfile = it,
                                    completionTimeInMillis = currentTimeProvider.currentTimeMillis(),
                                    actionType = currentAction.asActionType(),
                                    result = pirErrorReponse,
                                ),
                            )
                        }
                }
                if (currentAction is GetCaptchaInfo || currentAction is SolveCaptcha) {
                    nextCommand(
                        ExecuteBrokerAction(
                            lastState.copy(
                                currentActionIndex = lastState.currentActionIndex + 1,
                            ),
                        ),
                    )
                } else {
                    // If error happens we skip to next Broker as next steps will not make sense
                    nextCommand(
                        BrokerCompleted(lastState, isSuccess = false),
                    )
                }
            } else {
                logcat { "PIR-RUNNER (${this@RealPirActionsRunner}): Runner can't handle $pirErrorReponse" }
            }
        }
    }

    sealed class Command(open val state: State) {
        data object Idle : Command(State())
        data class HandleBroker(
            override val state: State,
        ) : Command(state)

        data class ExecuteBrokerAction(
            override val state: State,
            val actionRequestData: PirScriptRequestData = UserProfile(
                userProfile = state.profileQuery,
            ),
        ) : Command(state)

        data class LoadUrl(
            override val state: State,
            val urlToLoad: String,
        ) : Command(State())

        data class HandleNextProfileForBroker(
            override val state: State,
        ) : Command(State())

        data class GetEmail(
            override val state: State,
        ) : Command(State())

        data class RecoverFromFailedUrlLoad(
            override val state: State,
        ) : Command(State())

        data class BrokerCompleted(
            override val state: State,
            val isSuccess: Boolean,
        ) : Command(state)

        data class AwaitEmailConfirmation(
            override val state: State,
            val pollingIntervalSeconds: Float,
        ) : Command(state)

        data class GetCaptchaSolution(
            override val state: State,
            val responseData: ResponseData,
            val isRetry: Boolean,
        ) : Command(state)

        data class AwaitCaptchaSolution(
            override val state: State,
            val pollingIntervalSeconds: Int = 5,
            val retries: Int = 50,
            val attempt: Int = 0,
        ) : Command(state)

        data class SendCaptchaSolution(
            override val state: State,
            val callback: String,
        ) : Command(state)

        data object CompleteExecution : Command(State())
    }

    data class State(
        val currentBrokerIndex: Int = 0,
        val currentActionIndex: Int = 0,
        val brokerStartTime: Long = -1L,
        val profileQuery: ProfileQuery? = null,
        val extractedProfileState: ExtractedProfileState = ExtractedProfileState(),
        val transactionID: String = "",
    )

    data class ExtractedProfileState(
        val currentExtractedProfileIndex: Int = 0,
        val extractedProfile: List<ExtractedProfile> = emptyList(),
    )
}
