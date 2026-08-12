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

package com.duckduckgo.pir.impl.common

import android.content.Context
import android.webkit.WebView
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStep
import com.duckduckgo.pir.impl.common.NativeBrokerActionHandler.NativeAction
import com.duckduckgo.pir.impl.common.NativeBrokerActionHandler.NativeAction.GetCaptchaSolutionStatus
import com.duckduckgo.pir.impl.common.NativeBrokerActionHandler.NativeAction.SubmitCaptchaInfo
import com.duckduckgo.pir.impl.common.NativeBrokerActionHandler.NativeActionResult.Failure
import com.duckduckgo.pir.impl.common.NativeBrokerActionHandler.NativeActionResult.Success
import com.duckduckgo.pir.impl.common.NativeBrokerActionHandler.NativeActionResult.Success.NativeSuccessData.CaptchaSolutionStatus
import com.duckduckgo.pir.impl.common.NativeBrokerActionHandler.NativeActionResult.Success.NativeSuccessData.CaptchaSolutionStatus.CaptchaStatus.Ready
import com.duckduckgo.pir.impl.common.NativeBrokerActionHandler.NativeActionResult.Success.NativeSuccessData.CaptchaTransactionIdReceived
import com.duckduckgo.pir.impl.common.NativeBrokerActionHandler.NativeActionResult.Success.NativeSuccessData.Email
import com.duckduckgo.pir.impl.common.PirJob.RunType
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.CaptchaInfoReceived
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.EmailDataReceived
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.EmailReceived
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.ErrorReceived
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.ExecuteBrokerStepAction
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.JsActionSuccess
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.LoadUrlComplete
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.LoadUrlFailed
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.RetryAwaitCaptchaSolution
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.RetryAwaitEmailData
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.RetryGetCaptchaSolution
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.Started
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.SideEffect
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.SideEffect.AwaitCaptchaSolution
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.SideEffect.AwaitEmailData
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.SideEffect.CompleteExecution
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.SideEffect.EvaluateJs
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.SideEffect.GetCaptchaSolution
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.SideEffect.GetEmailForProfile
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.SideEffect.LoadUrl
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.SideEffect.None
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.SideEffect.PushJsAction
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngineFactory
import com.duckduckgo.pir.impl.models.ProfileQuery
import com.duckduckgo.pir.impl.scripts.BrokerActionProcessor
import com.duckduckgo.pir.impl.scripts.BrokerActionProcessor.ActionResultListener
import com.duckduckgo.pir.impl.scripts.models.PirError
import com.duckduckgo.pir.impl.scripts.models.PirError.ActionError.CaptchaServiceError
import com.duckduckgo.pir.impl.scripts.models.PirError.ActionError.CaptchaServiceMaxAttempts
import com.duckduckgo.pir.impl.scripts.models.PirError.ActionError.CaptchaSolutionFailed
import com.duckduckgo.pir.impl.scripts.models.PirError.ActionError.ClientError
import com.duckduckgo.pir.impl.scripts.models.PirError.ActionError.EmailError
import com.duckduckgo.pir.impl.scripts.models.PirError.ActionError.JsActionFailed
import com.duckduckgo.pir.impl.scripts.models.PirError.JsError
import com.duckduckgo.pir.impl.scripts.models.PirError.UnableToLoadBrokerUrl
import com.duckduckgo.pir.impl.scripts.models.PirError.Unknown
import com.duckduckgo.pir.impl.scripts.models.PirScriptRequestData
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import logcat.logcat
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface PirActionsRunner {
    /**
     * Executes [brokerStep] for [profileQuery] on a freshly created detached WebView, which is
     * destroyed when the step completes.
     *
     * @param profileQuery - Profile to be passed along actions in [brokerStep]
     * @param brokerStep - A broker + the actions to be executed for it.
     */
    suspend fun execute(
        profileQuery: ProfileQuery,
        brokerStep: BrokerStep,
    ): Result<Unit>

    /**
     * Executes [brokerStep] for [profileQuery] on the caller-owned [webView], which is configured
     * for the step and left alive afterwards. Debug / visible runs only.
     */
    suspend fun executeOn(
        webView: WebView,
        profileQuery: ProfileQuery,
        brokerStep: BrokerStep,
    ): Result<Unit>

    /**
     * Forcefully stops / aborts a runner if it is running, and destroys the WebView if this
     * runner owns it.
     */
    fun stop()
}

class RealPirActionsRunner @AssistedInject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val pirDetachedWebViewProvider: PirDetachedWebViewProvider,
    private val brokerActionProcessor: BrokerActionProcessor,
    private val nativeBrokerActionHandler: NativeBrokerActionHandler,
    private val emailDataResolver: EmailDataResolver,
    private val engineFactory: PirActionsRunnerStateEngineFactory,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    @Assisted private val runType: RunType,
    @Assisted private val context: Context,
    @Assisted private val pirScriptToLoad: String,
) : PirActionsRunner,
    ActionResultListener {
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
    private var ownsWebView: Boolean = false

    private val runContinuation: AtomicReference<CancellableContinuation<Result<Unit>>?> = AtomicReference(null)

    private var timerJob: ConflatedJob = ConflatedJob()
    private var engineJob: ConflatedJob = ConflatedJob()

    override suspend fun execute(
        profileQuery: ProfileQuery,
        brokerStep: BrokerStep,
    ): Result<Unit> {
        withContext(dispatcherProvider.main()) {
            logcat { "PIR-RUNNER (${this@RealPirActionsRunner}): ${Thread.currentThread().name} Creating detached WebView" }
            detachedWebView =
                pirDetachedWebViewProvider.createInstance(
                    context,
                    pirScriptToLoad,
                    onPageLoaded = {
                        onLoadingComplete(it)
                    },
                    onPageLoadFailed = {
                        onLoadingFailed(it)
                    },
                    onRendererGone = {
                        onRendererGone(it)
                    },
                )
            ownsWebView = true

            brokerActionProcessor.register(detachedWebView!!, this@RealPirActionsRunner)
        }

        return runStep(profileQuery, brokerStep)
    }

    override suspend fun executeOn(
        webView: WebView,
        profileQuery: ProfileQuery,
        brokerStep: BrokerStep,
    ): Result<Unit> {
        withContext(dispatcherProvider.main()) {
            logcat { "PIR-RUNNER (${this@RealPirActionsRunner}): ${Thread.currentThread().name} Adopting caller-owned WebView" }
            detachedWebView =
                pirDetachedWebViewProvider.setupWebView(
                    webView,
                    pirScriptToLoad,
                    onPageLoaded = {
                        onLoadingComplete(it)
                    },
                    onPageLoadFailed = {
                        onLoadingFailed(it)
                    },
                    onRendererGone = {
                        onRendererGone(it)
                    },
                )
            ownsWebView = false

            brokerActionProcessor.register(detachedWebView!!, this@RealPirActionsRunner)
        }

        return runStep(profileQuery, brokerStep)
    }

    private suspend fun runStep(
        profileQuery: ProfileQuery,
        brokerStep: BrokerStep,
    ): Result<Unit> {
        logcat {
            "PIR-RUNNER (${this@RealPirActionsRunner}): ${Thread.currentThread().name} " +
                "profile=$profileQuery broker to execute $brokerStep"
        }

        return try {
            engine = engineFactory.create(runType, brokerStep, profileQuery)
            awaitResult()
        } finally {
            finishStep()
        }
    }

    /**
     * Releases everything tied to the step, destroying the WebView if this runner created it.
     * Cancelling [timerJob] before the next step matters beyond tidiness: a pushed action arms a
     * local timeout that [CompleteExecution] does not cancel, and it would otherwise fire during
     * this runner's following step and fail it with this step's action id.
     *
     * The teardown is [NonCancellable] because it runs from a `finally`: a cancelled step (a stopped
     * scan worker, or a sibling runner failing the distributor's scope) would otherwise never reach
     * the block, and the reference is dropped here, so no later [stop] could destroy the WebView.
     */
    private suspend fun finishStep() {
        timerJob.cancel()
        engineJob.cancel()
        engine?.close()
        engine = null

        // Captured and cleared synchronously so this step's teardown cannot race the next step's
        // WebView creation and transiently hold two WebViews.
        val webView = detachedWebView
        detachedWebView = null
        if (webView == null) return

        withContext(NonCancellable + dispatcherProvider.main()) {
            webView.stopLoading()
            if (ownsWebView) {
                webView.evaluateJavascript("window.stop();", null)
                webView.clearFormData()
                webView.clearHistory()
                webView.clearCache(true)
                webView.destroy()
                logcat { "PIR-RUNNER: Destroyed webview" }
            } else {
                webView.clearFormData()
                webView.clearHistory()
            }
        }
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

    private fun onRendererGone(didCrash: Boolean) {
        val continuation = runContinuation.getAndSet(null)

        if (timerJob.isActive) {
            timerJob.cancel()
        }
        if (engineJob.isActive) {
            engineJob.cancel()
        }

        // A WebView whose renderer died cannot run anything further, so drop it now rather than
        // letting the step teardown touch a dead instance.
        val deadWebView = detachedWebView
        detachedWebView = null
        if (ownsWebView && deadWebView != null) {
            coroutineScope.launch(dispatcherProvider.main()) {
                deadWebView.destroy()
            }
        }

        if (continuation == null || !continuation.isActive) {
            logcat {
                "PIR-RUNNER (${this@RealPirActionsRunner}): renderer process gone, didCrash=$didCrash - stale/no-op, ignoring"
            }
            return
        }

        logcat { "PIR-RUNNER (${this@RealPirActionsRunner}): renderer process gone, didCrash=$didCrash - failing current run" }
        continuation.resumeWithException(PirRendererGoneException(didCrash))
    }

    private suspend fun awaitResult(): Result<Unit> =
        suspendCancellableCoroutine { continuation ->
            runContinuation.set(continuation)
            engineJob +=
                coroutineScope.launch {
                    engine!!.sideEffect.collect { effect ->
                        if (effect is CompleteExecution) {
                            runContinuation.getAndSet(null)?.resume(Result.success(Unit))
                        } else {
                            handleEffect(effect)
                        }
                    }
                }

            // Subscribed above before dispatching, so the outcome does not depend on replay.
            engine!!.dispatch(Started)

            continuation.invokeOnCancellation {
                runContinuation.getAndSet(null)
                engineJob.cancel()
            }
        }

    private suspend fun handleEffect(effect: SideEffect) {
        logcat { "PIR-RUNNER: Received SideFffect from engine: $effect" }
        when (effect) {
            None, CompleteExecution -> {}
            is LoadUrl ->
                withContext(dispatcherProvider.main()) {
                    detachedWebView?.loadUrl(effect.url)
                }

            is PushJsAction -> pushJsAction(effect)
            is GetEmailForProfile -> handleGetEmail(effect)
            is GetCaptchaSolution -> handleGetCaptchaSolution(effect)
            is EvaluateJs ->
                withContext(dispatcherProvider.main()) {
                    detachedWebView?.evaluateJavascript(effect.callback, null)
                }

            is AwaitCaptchaSolution -> handleAwaitCaptchaSolution(effect)
            is AwaitEmailData -> handleAwaitEmailData(effect)
        }
    }

    private suspend fun pushJsAction(effect: PushJsAction) {
        timerJob +=
            coroutineScope.launch(dispatcherProvider.io()) {
                delay(60000) // 1 minute
                // IF this timer completes, then timeout was reached
                kotlin.runCatching {
                    onError(
                        JsActionFailed(
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

    private suspend fun handleAwaitCaptchaSolution(effect: AwaitCaptchaSolution) =
        withContext(dispatcherProvider.io()) {
            if (effect.transactionID.isEmpty()) {
                onError(
                    ClientError(
                        actionID = effect.actionId,
                        message = "Invalid state: No transaction ID for captcha",
                    ),
                )
            } else {
                nativeBrokerActionHandler
                    .pushAction(
                        GetCaptchaSolutionStatus(
                            actionId = effect.actionId,
                            transactionID = effect.transactionID,
                        ),
                    ).run {
                        if (this is Success) {
                            when (val status = (this.data as CaptchaSolutionStatus).status) {
                                is Ready ->
                                    engine?.dispatch(
                                        ExecuteBrokerStepAction(
                                            actionRequestData =
                                            PirScriptRequestData.SolveCaptcha(
                                                token = status.token,
                                            ),
                                        ),
                                    )

                                else -> {
                                    if (effect.attempt == effect.retries) {
                                        onError(
                                            CaptchaServiceMaxAttempts(
                                                actionID = effect.actionId,
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
                            val failure = this as Failure
                            onError(
                                failure.error,
                            )
                        }
                    }
            }
        }

    private suspend fun handleAwaitEmailData(effect: AwaitEmailData) =
        withContext(dispatcherProvider.io()) {
            if (effect.emailAddress.isEmpty() || effect.attemptId.isEmpty()) {
                onError(
                    ClientError(
                        actionID = effect.actionId,
                        message = "Invalid state: missing email address or attempt id for email data poll",
                    ),
                )
                return@withContext
            }

            val maxAttempts = if (effect.pollingIntervalSeconds > 0) {
                effect.maxTimeoutSeconds / effect.pollingIntervalSeconds
            } else {
                0
            }

            when (val result = emailDataResolver.poll(effect.emailAddress, effect.attemptId)) {
                is EmailDataResolver.EmailDataResolverResult.Success -> {
                    if (effect.extractFields.all { result.extractedData.containsKey(it) }) {
                        engine?.dispatch(
                            EmailDataReceived(
                                emailExtractedData = result.extractedData,
                            ),
                        )
                    } else {
                        onError(
                            ClientError(
                                actionID = effect.actionId,
                                message = "Email data ready but missing required fields: ${effect.extractFields - result.extractedData.keys}",
                            ),
                        )
                    }
                }

                is EmailDataResolver.EmailDataResolverResult.Pending -> {
                    if (effect.attempt >= maxAttempts) {
                        onError(
                            ClientError(
                                actionID = effect.actionId,
                                message = "Email data poll timeout after ${effect.maxTimeoutSeconds}s",
                            ),
                        )
                    } else {
                        delay(effect.pollingIntervalSeconds * 1000L)
                        engine?.dispatch(
                            RetryAwaitEmailData(
                                actionId = effect.actionId,
                                brokerName = effect.brokerName,
                                emailAddress = effect.emailAddress,
                                attemptId = effect.attemptId,
                                extractFields = effect.extractFields,
                                pollingIntervalSeconds = effect.pollingIntervalSeconds,
                                maxTimeoutSeconds = effect.maxTimeoutSeconds,
                                attempt = effect.attempt,
                            ),
                        )
                    }
                }

                is EmailDataResolver.EmailDataResolverResult.Failure -> {
                    onError(
                        ClientError(
                            actionID = effect.actionId,
                            message = result.message,
                        ),
                    )
                }
            }
        }

    private suspend fun handleGetCaptchaSolution(effect: GetCaptchaSolution) =
        withContext(dispatcherProvider.io()) {
            nativeBrokerActionHandler
                .pushAction(
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
                            result.error,
                        )
                    }
                }
        }

    private suspend fun handleGetEmail(effect: GetEmailForProfile) =
        withContext(dispatcherProvider.io()) {
            nativeBrokerActionHandler
                .pushAction(
                    NativeAction.GetEmail(
                        actionId = effect.actionId,
                        brokerName = effect.brokerName,
                    ),
                ).also {
                    if (it is Success) {
                        engine?.dispatch(
                            EmailReceived(
                                generatedEmailData = (it.data as Email).generatedEmailData,
                            ),
                        )
                    } else {
                        val result = it as Failure
                        onError(
                            result.error,
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
        runContinuation.getAndSet(null)
        engine?.close()
        engine = null

        // Captured and cleared synchronously so teardown cannot race a later execute().
        val webView = detachedWebView
        detachedWebView = null
        if (!ownsWebView || webView == null) return

        coroutineScope.launch(dispatcherProvider.main()) {
            webView.stopLoading()
            webView.evaluateJavascript("window.stop();", null)
            webView.clearFormData()
            webView.clearHistory()
            webView.clearCache(true)
            webView.destroy()
            logcat { "PIR-RUNNER: Destroyed webview" }
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
            is JsActionFailed ->
                Event.BrokerActionFailed(
                    error = pirError,
                    allowRetry = true, // Failure is in js execution so we could retry.
                )

            is CaptchaSolutionFailed, is CaptchaServiceMaxAttempts ->
                Event.BrokerActionFailed(
                    error = pirError,
                    allowRetry = false, // We already handled retries internally, this point we already bail.
                )

            is JsError, is CaptchaServiceError, is EmailError, is ClientError ->
                ErrorReceived(
                    error = pirError,
                )

            is Unknown, UnableToLoadBrokerUrl -> null
        }?.also {
            engine?.dispatch(it)
        }
    }
}
