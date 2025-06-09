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
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.pir.internal.common.BrokerStepsParser.BrokerStep
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
import com.duckduckgo.pir.internal.common.PirJob.RunType
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.Event.CaptchaInfoReceived
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.Event.CaptchaServiceFailed
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.Event.EmailConfirmationLinkReceived
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.Event.EmailFailed
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.Event.EmailReceived
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.Event.ExecuteNextBrokerAction
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.Event.JsActionFailed
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.Event.JsActionSuccess
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.Event.JsErrorReceived
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.Event.LoadUrlComplete
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.Event.LoadUrlFailed
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.Event.RetryAwaitCaptchaSolution
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.Event.RetryGetCaptchaSolution
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.Event.Started
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.SideEffect
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.SideEffect.AwaitCaptchaSolution
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.SideEffect.AwaitEmailConfirmation
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.SideEffect.CompleteExecution
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.SideEffect.EvaluateJs
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.SideEffect.GetCaptchaSolution
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.SideEffect.GetEmailForProfile
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.SideEffect.LoadUrl
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.SideEffect.None
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.SideEffect.PushJsAction
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngineFactory
import com.duckduckgo.pir.internal.scripts.BrokerActionProcessor
import com.duckduckgo.pir.internal.scripts.BrokerActionProcessor.ActionResultListener
import com.duckduckgo.pir.internal.scripts.models.PirError
import com.duckduckgo.pir.internal.scripts.models.PirScriptRequestData
import com.duckduckgo.pir.internal.scripts.models.PirSuccessResponse
import com.duckduckgo.pir.internal.scripts.models.ProfileQuery
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
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

class RealPirActionsRunner @AssistedInject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val pirDetachedWebViewProvider: PirDetachedWebViewProvider,
    private val brokerActionProcessor: BrokerActionProcessor,
    private val nativeBrokerActionHandler: NativeBrokerActionHandler,
    private val engineFactory: PirActionsRunnerStateEngineFactory,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    @Assisted private val runType: RunType,
    @Assisted private val context: Context,
    @Assisted private val pirScriptToLoad: String,
) : PirActionsRunner, ActionResultListener {
    @AssistedFactory
    interface Factory {
        fun create(
            context: Context,
            pirScriptToLoad: String,
            runType: RunType,
        ): RealPirActionsRunner
    }

    private var engine: PirActionsRunnerStateEngine? = null
    private var detachedWebView: WebView? = null

    private var timerJob: ConflatedJob = ConflatedJob()
    private var engineJob: ConflatedJob = ConflatedJob()

    override suspend fun start(
        profileQuery: ProfileQuery,
        brokerSteps: List<BrokerStep>,
    ): Result<Unit> {
        if (brokerSteps.isEmpty()) {
            logcat { "PIR-RUNNER ($this): No broker steps to execute ${Thread.currentThread().name}" }
            return Result.success(Unit)
        }

        withContext(dispatcherProvider.main()) {
            logcat { "PIR-RUNNER (${this@RealPirActionsRunner}): ${Thread.currentThread().name} Brokers to execute $brokerSteps" }
            logcat { "PIR-RUNNER (${this@RealPirActionsRunner}): ${Thread.currentThread().name} Brokers size: ${brokerSteps.size}" }
            detachedWebView = pirDetachedWebViewProvider.createInstance(
                context,
                pirScriptToLoad,
                onPageLoaded = {
                    onLoadingComplete(it)
                },
                onPageLoadFailed = {
                    onLoadingFailed(it)
                },
            )

            brokerActionProcessor.register(detachedWebView!!, this@RealPirActionsRunner)
        }

        engine = engineFactory.create(runType, brokerSteps)
        engine!!.dispatch(
            Started(
                profileQuery = profileQuery,
            ),
        )

        return awaitResult()
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

        withContext(dispatcherProvider.main()) {
            logcat { "PIR-RUNNER (${this@RealPirActionsRunner}): ${Thread.currentThread().name} Brokers to execute $brokerSteps" }
            logcat { "PIR-RUNNER (${this@RealPirActionsRunner}): ${Thread.currentThread().name} Brokers size: ${brokerSteps.size}" }
            detachedWebView = pirDetachedWebViewProvider.setupWebView(
                webView,
                pirScriptToLoad,
                onPageLoaded = {
                    onLoadingComplete(it)
                },
                onPageLoadFailed = {
                    onLoadingFailed(it)
                },
            )

            brokerActionProcessor.register(detachedWebView!!, this@RealPirActionsRunner)
        }

        engine = engineFactory.create(runType, brokerSteps)
        engine!!.dispatch(
            Started(
                profileQuery = profileQuery,
            ),
        )

        return awaitResult()
    }

    private fun onLoadingComplete(url: String?) {
        logcat { "PIR-RUNNER ($this): finished loading $url" }
        if (url == null) {
            return
        }

        engine?.dispatch(
            LoadUrlComplete(
                url = url,
            ),
        )
    }

    private fun onLoadingFailed(url: String?) {
        logcat { "PIR-RUNNER (${this@RealPirActionsRunner}): Recovering from loading $url failure" }
        if (url == null) {
            return
        }
        engine?.dispatch(
            LoadUrlFailed(
                url = url,
            ),
        )
    }

    private suspend fun awaitResult(): Result<Unit> {
        return suspendCancellableCoroutine { continuation ->
            engineJob += coroutineScope.launch {
                engine!!.sideEffect.collect { effect ->
                    if (effect is CompleteExecution) {
                        continuation.resume(Result.success(Unit))
                    } else {
                        handleEffect(effect)
                    }
                }
            }

            continuation.invokeOnCancellation {
                engineJob.cancel()
            }
        }
    }

    private suspend fun handleEffect(effect: SideEffect) {
        logcat { "PIR-RUNNER: Received SideFffect from engine: $effect" }
        when (effect) {
            None, CompleteExecution -> {}
            is LoadUrl -> withContext(dispatcherProvider.main()) {
                detachedWebView!!.loadUrl(effect.url)
            }

            is PushJsAction -> pushJsAction(effect)
            is GetEmailForProfile -> handleGetEmail(effect)
            is GetCaptchaSolution -> handleGetCaptchaSolution(effect)
            is EvaluateJs -> withContext(dispatcherProvider.main()) {
                detachedWebView?.evaluateJavascript(effect.callback, null)
            }

            is AwaitEmailConfirmation -> handleEmailConfirmation(effect)
            is AwaitCaptchaSolution -> handleAwaitCaptchaSolution(effect)
        }
    }

    private suspend fun pushJsAction(effect: PushJsAction) {
        timerJob += coroutineScope.launch(dispatcherProvider.io()) {
            delay(60000) // 1 minute
            // IF this timer completes, then timeout was reached
            kotlin.runCatching {
                onError(
                    PirError.ActionFailed(
                        actionID = effect.actionId,
                        message = "Local timeout",
                    ),
                )
            }
        }

        if (effect.pushDelay != 0L) {
            delay(effect.pushDelay)
        }

        brokerActionProcessor.pushAction(
            action = effect.action,
            requestParamsData = effect.requestParamsData,
        )
    }

    private suspend fun handleAwaitCaptchaSolution(effect: AwaitCaptchaSolution) = withContext(dispatcherProvider.io()) {
        if (effect.transactionID.isEmpty()) {
            onError(
                PirError.CaptchaServiceError(
                    "Unable to solve captcha",
                ),
            )
        } else {
            nativeBrokerActionHandler.pushAction(
                GetCaptchaSolutionStatus(
                    actionId = effect.actionId,
                    transactionID = effect.transactionID,
                ),
            ).run {
                if (this is Success) {
                    when (val status = (this.data as CaptchaSolutionStatus).status) {
                        is Ready -> engine?.dispatch(
                            ExecuteNextBrokerAction(
                                actionRequestData = PirScriptRequestData.SolveCaptcha(
                                    token = status.token,
                                ),
                            ),
                        )

                        else -> {
                            if (effect.attempt == effect.retries) {
                                onError(
                                    PirError.CaptchaServiceError(
                                        "Unable to solve captcha",
                                    ),
                                )
                            } else {
                                delay(effect.pollingIntervalSeconds * 1000L)
                                engine?.dispatch(
                                    RetryAwaitCaptchaSolution(
                                        actionId = effect.actionId,
                                        brokerName = effect.brokerName,
                                        transactionID = effect.transactionID,
                                        attempt = effect.attempt,
                                    ),
                                )
                            }
                        }
                    }
                } else {
                    onError(
                        PirError.CaptchaServiceError(
                            "Unable to solve captcha",
                        ),
                    )
                }
            }
        }
    }

    private suspend fun handleGetCaptchaSolution(effect: GetCaptchaSolution) = withContext(dispatcherProvider.io()) {
        nativeBrokerActionHandler.pushAction(
            SubmitCaptchaInfo(
                actionId = effect.actionId,
                siteKey = effect.responseData!!.siteKey,
                url = effect.responseData.url,
                type = effect.responseData.type,
            ),
        ).also {
            if (it is Success) {
                engine?.dispatch(
                    CaptchaInfoReceived(
                        transactionID = (it.data as CaptchaTransactionIdReceived).transactionID,
                    ),
                )
            } else if (it is Failure && !effect.isRetry && it.retryNativeAction) {
                delay(60_000)
                engine?.dispatch(
                    RetryGetCaptchaSolution(
                        actionId = effect.actionId,
                        responseData = effect.responseData,
                    ),
                )
            } else {
                val result = it as Failure
                onError(
                    PirError.CaptchaServiceError(
                        error = result.message,
                    ),
                )
            }
        }
    }

    private suspend fun handleEmailConfirmation(effect: AwaitEmailConfirmation) = withContext(dispatcherProvider.io()) {
        nativeBrokerActionHandler.pushAction(
            NativeAction.GetEmailStatus(
                actionId = effect.actionId,
                brokerName = effect.brokerName,
                email = effect.extractedProfile.email!!,
                pollingIntervalSeconds = effect.pollingIntervalSeconds,
            ),
        ).also {
            if (it is Success) {
                engine?.dispatch(
                    EmailConfirmationLinkReceived(
                        confirmationLink = (it.data as NativeSuccessData.EmailConfirmation).link,
                    ),
                )
            } else {
                val result = it as Failure
                onError(
                    PirError.EmailError(
                        error = result.message,
                    ),
                )
            }
        }
    }

    private suspend fun handleGetEmail(effect: GetEmailForProfile) = withContext(dispatcherProvider.io()) {
        nativeBrokerActionHandler.pushAction(
            NativeAction.GetEmail(
                actionId = effect.actionId,
                brokerName = effect.brokerName,
            ),
        ).also {
            if (it is Success) {
                engine?.dispatch(
                    EmailReceived(
                        email = (it.data as Email).email,
                    ),
                )
            } else {
                val result = it as Failure
                onError(
                    PirError.EmailError(
                        error = result.message,
                    ),
                )
            }
        }
    }

    private fun cleanUpRunner() {
        if (timerJob.isActive) {
            timerJob.cancel()
        }
        if (engineJob.isActive) {
            engineJob.cancel()
        }
        coroutineScope.launch(dispatcherProvider.main()) {
            detachedWebView?.stopLoading()
            detachedWebView?.loadUrl("about:blank")
            detachedWebView?.evaluateJavascript("window.stop();", null)
            detachedWebView?.destroy()
            detachedWebView = null
            logcat { "PIR-RUNNER ($this): Destroyed webview" }
        }
    }

    override fun stop() {
        logcat { "PIR-RUNNER ($this): Stopping and resetting values" }
        cleanUpRunner()
    }

    override fun onSuccess(pirSuccessResponse: PirSuccessResponse) {
        if (timerJob.isActive) {
            timerJob.cancel()
        }

        engine?.dispatch(
            JsActionSuccess(
                pirSuccessResponse = pirSuccessResponse,
            ),
        )
    }

    override fun onError(pirError: PirError) {
        if (timerJob.isActive) {
            timerJob.cancel()
        }

        when (pirError) {
            is PirError.ActionFailed -> JsActionFailed(
                error = pirError,
            )

            is PirError.CaptchaServiceError -> CaptchaServiceFailed(
                error = pirError,
            )

            is PirError.EmailError -> EmailFailed(
                error = pirError,
            )

            is PirError.JsError -> JsErrorReceived(
                error = pirError,
            )

            else -> null
        }?.also {
            engine?.dispatch(it)
        }
    }
}
