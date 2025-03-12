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

package com.duckduckgo.pir.internal.component

import android.content.Context
import android.webkit.WebView
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.pir.internal.component.RealPirActionsRunner.Command.CompleteExecution
import com.duckduckgo.pir.internal.component.RealPirActionsRunner.Command.ExecuteBrokerAction
import com.duckduckgo.pir.internal.component.RealPirActionsRunner.Command.HandleBroker
import com.duckduckgo.pir.internal.component.RealPirActionsRunner.Command.Idle
import com.duckduckgo.pir.internal.component.RealPirActionsRunner.Command.LoadUrl
import com.duckduckgo.pir.internal.scan.BrokerStepsParser.BrokerStep
import com.duckduckgo.pir.internal.scripts.BrokerActionProcessor
import com.duckduckgo.pir.internal.scripts.BrokerActionProcessor.ActionResultListener
import com.duckduckgo.pir.internal.scripts.models.PirErrorReponse
import com.duckduckgo.pir.internal.scripts.models.PirSuccessResponse
import com.duckduckgo.pir.internal.scripts.models.PirSuccessResponse.ExtractedResponse
import com.duckduckgo.pir.internal.scripts.models.PirSuccessResponse.NavigateResponse
import com.duckduckgo.pir.internal.scripts.models.ProfileQuery
import com.duckduckgo.pir.internal.scripts.models.asActionType
import com.duckduckgo.pir.internal.store.PirRepository
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
     * Forcefully stops / aborts a runner if it is running.
     */
    suspend fun stop()
}

internal class RealPirActionsRunner(
    private val dispatcherProvider: DispatcherProvider,
    private val repository: PirRepository,
    private val pirDetachedWebViewProvider: PirDetachedWebViewProvider,
    private val brokerActionProcessor: BrokerActionProcessor,
    private val context: Context,
    private val pirScriptToLoad: String,
) : PirActionsRunner, ActionResultListener {
    private val timerCoroutineScope: CoroutineScope
        get() = CoroutineScope(SupervisorJob() + dispatcherProvider.io())

    private val commandCoroutineScope: CoroutineScope
        get() = CoroutineScope(SupervisorJob() + dispatcherProvider.io())

    private var detachedWebView: WebView? = null
    private var submittedProfileQuery: ProfileQuery? = null
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

        submittedProfileQuery = profileQuery
        brokersToExecute.clear()
        brokersToExecute.addAll(brokerSteps)

        initializeDetachedWebView()

        return suspendCoroutine { continuation ->
            commandsJob += commandCoroutineScope.launch {
                commandsFlow.asStateFlow().collect {
                    handleCommand(it, continuation)
                }
            }
        }
    }

    private suspend fun initializeDetachedWebView() {
        withContext(dispatcherProvider.main()) {
            /**
             * 1. Create detached WebView
             * 2. Prepare the detached WebView - load script and load the dbp url
             * 3. Execute all steps for each Broker
             * 4. Complete!
             */

            logcat { "PIR-RUNNER (${this@RealPirActionsRunner}): ${Thread.currentThread().name} Brokers to execute $brokersToExecute" }
            logcat { "PIR-RUNNER (${this@RealPirActionsRunner}): ${Thread.currentThread().name} Brokers size: ${brokersToExecute.size}" }
            detachedWebView = pirDetachedWebViewProvider.getInstance(context, pirScriptToLoad) {
                logcat { "PIR-RUNNER ($this): finished loading $it and latest action ${commandsFlow.value}" }

                // A completed initial scan means we are ready to run the scan for the brokers
                if (it == DBP_INITIAL_URL) {
                    nextCommand(
                        HandleBroker(
                            commandsFlow.value.state.copy(
                                currentBrokerIndex = 0,
                                currentActionIndex = 0,
                            ),
                        ),
                    )
                } else if (commandsFlow.value is LoadUrl) {
                    // If the current action is still navigate, it means we just finished loading and we can proceed to next action.
                    // Sometimes the loaded url gets redirected to another url (could be different domain too) so we can't really check here.
                    logcat { "PIR-RUNNER (${this@RealPirActionsRunner}): Completed loading for ${commandsFlow.value}" }
                    nextCommand(
                        ExecuteBrokerAction(
                            commandsFlow.value.state.copy(
                                currentActionIndex = commandsFlow.value.state.currentActionIndex + 1,
                            ),
                        ),
                    )
                } else {
                    logcat { "PIR-RUNNER (${this@RealPirActionsRunner}): Ignoring $it as next action has been pushed" }
                }
            }.also {
                brokerActionProcessor.register(it, this@RealPirActionsRunner)
            }

            detachedWebView!!.loadUrl(DBP_INITIAL_URL)
        }
    }

    private fun handleCommand(
        command: Command,
        continuationResult: Continuation<Result<Unit>>,
    ) {
        logcat { "PIR-RUNNER ($this): Handle command: $command" }
        when (command) {
            Idle -> {} // Do nothing
            is HandleBroker -> handleBrokerAction(command.state)
            is ExecuteBrokerAction -> executeBrokerAction(command.state)
            is CompleteExecution -> {
                continuationResult.resume(Result.success(Unit))
                cleanUpRunner()
            }
            // TODO add loading timeout
            is LoadUrl -> commandCoroutineScope.launch(dispatcherProvider.main()) {
                detachedWebView!!.loadUrl(command.urlToLoad)
            }
        }
    }

    private fun nextCommand(command: Command) {
        commandCoroutineScope.launch {
            commandsFlow.emit(command)
        }
    }

    private fun handleBrokerAction(state: State) {
        if (state.currentBrokerIndex >= brokersToExecute.size) {
            nextCommand(CompleteExecution)
        } else {
            nextCommand(
                ExecuteBrokerAction(
                    commandsFlow.value.state.copy(
                        currentActionIndex = 0,
                    ),
                ),
            )
        }
    }

    private fun executeBrokerAction(state: State) {
        val currentBroker = brokersToExecute[state.currentBrokerIndex]
        if (state.currentActionIndex == currentBroker.actions.size) {
            nextCommand(
                HandleBroker(
                    state.copy(
                        currentBrokerIndex = state.currentBrokerIndex + 1,
                    ),
                ),
            )
        } else {
            val actionToExecute = currentBroker.actions[state.currentActionIndex]

            timerJob.cancel()
            timerJob += timerCoroutineScope.launch {
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

            brokerActionProcessor.pushAction(submittedProfileQuery!!, actionToExecute)
        }
    }

    private fun cleanUpRunner() {
        timerJob.cancel()
        nextCommand(Idle)
        commandsJob.cancel()
        commandCoroutineScope.launch(dispatcherProvider.main()) {
            detachedWebView?.destroy()
        }
    }

    override suspend fun stop() {
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
                when (pirSuccessResponse) {
                    is NavigateResponse -> {
                        repository.saveNavigateResult(
                            currentBroker.brokerName ?: "",
                            pirSuccessResponse,
                        )
                        nextCommand(
                            LoadUrl(
                                urlToLoad = pirSuccessResponse.response.url,
                                state = lastState,
                            ),
                        )
                    }

                    is ExtractedResponse -> {
                        runBlocking {
                            repository.saveExtractProfileResult(
                                currentBroker.brokerName ?: "",
                                pirSuccessResponse,
                            )
                        }
                        nextCommand(
                            ExecuteBrokerAction(
                                state = lastState.copy(
                                    currentActionIndex = lastState.currentActionIndex + 1,
                                ),
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
                repository.saveErrorResult(
                    brokerName = currentBroker.brokerName ?: "",
                    actionType = currentAction.asActionType(),
                    error = pirErrorReponse,
                )
                // If error happens we skip to next Broker as next steps will not make sense
                nextCommand(
                    HandleBroker(
                        state = lastState.copy(
                            currentBrokerIndex = lastState.currentBrokerIndex + 1,
                        ),
                    ),
                )
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
        ) : Command(state)

        data class LoadUrl(
            override val state: State,
            val urlToLoad: String,
        ) : Command(State())

        data object CompleteExecution : Command(State())
    }

    data class State(
        val currentBrokerIndex: Int = 0,
        val currentActionIndex: Int = 0,
    )

    companion object {
        private const val DBP_INITIAL_URL = "dbp://blank"
    }
}
