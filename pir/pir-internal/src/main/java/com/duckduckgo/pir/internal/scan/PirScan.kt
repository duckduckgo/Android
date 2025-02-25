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

package com.duckduckgo.pir.internal.scan

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebView
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ServiceScope
import com.duckduckgo.pir.internal.component.PirDetachedWebViewProvider
import com.duckduckgo.pir.internal.scripts.BrokerActionProcessor
import com.duckduckgo.pir.internal.scripts.BrokerActionProcessor.ActionResultListener
import com.duckduckgo.pir.internal.scripts.PirCssScriptLoader
import com.duckduckgo.pir.internal.scripts.models.BrokerAction
import com.duckduckgo.pir.internal.scripts.models.PirErrorReponse
import com.duckduckgo.pir.internal.scripts.models.PirSuccessResponse
import com.duckduckgo.pir.internal.scripts.models.PirSuccessResponse.ExtractedResponse
import com.duckduckgo.pir.internal.scripts.models.PirSuccessResponse.NavigateResponse
import com.duckduckgo.pir.internal.scripts.models.asActionType
import com.duckduckgo.pir.internal.store.PirRepository
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import logcat.logcat

interface PirScan {
    fun execute(
        brokers: List<String>,
        context: Context,
        onScanComplete: () -> Unit,
    )

    fun executeAllBrokers(
        context: Context,
        onScanComplete: () -> Unit,
    )

    fun stop()
}

@ContributesBinding(
    scope = ServiceScope::class,
    boundType = PirScan::class,
)
@ContributesBinding(
    scope = ServiceScope::class,
    boundType = ActionResultListener::class,
)
class RealPirScan @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val pirDetachedWebViewProvider: PirDetachedWebViewProvider,
    private val repository: PirRepository,
    private val brokerStepsParser: BrokerStepsParser,
    private val brokerActionProcessor: BrokerActionProcessor,
    private val pirCssScriptLoader: PirCssScriptLoader,
) : PirScan, ActionResultListener {
    private val pirUserProfile = PirUserProfile(firstName = "")
    private var detachedWebView: WebView? = null
    private var currentAction: BrokerAction? = null
    private var currentActionIndex: Int = 0
    private var currentBrokerIndex: Int = 0
    private var currentBrokerActions: List<BrokerAction> = emptyList()
    private var currentBroker: String? = null
    private var mainUrlLoaded: String? = null
    private var _onScanComplete: () -> Unit = {}
    private var _brokers: List<String> = emptyList()

    @SuppressLint("RequiresFeature")
    override fun execute(
        brokers: List<String>,
        context: Context,
        onScanComplete: () -> Unit,
    ) {
        /**
         * 1. Create detached webview
         * 2. Get all info for broker
         * 3. Get all scan steps for broker
         * 4. Execute steps
         * 5. Complete!
         */
        runBlocking {
            repository.deleteAllResults()
        }

        _onScanComplete = onScanComplete
        logcat { "PIR-SCAN: RealPirScan initiate webview" }
        val script = runBlocking {
            pirCssScriptLoader.getScript()
        }
        _brokers = brokers
        detachedWebView = pirDetachedWebViewProvider.getInstance(context, script) {
            logcat { "PIR-SCAN: finished loading $it and mainUrlLoaded: $mainUrlLoaded" }
            if (it == DBP_INITIAL_URL) {
                logcat { "PIR-SCAN: Run scan for these brokers: $brokers" }
                initiateBrokerScan(brokers[0])
            } else {
                mainUrlLoaded = null
                proceedToNext()
            }
        }.apply {
            brokerActionProcessor.register(this@apply, this@RealPirScan)
        }

        // Initial load needed to load script
        mainUrlLoaded = DBP_INITIAL_URL
        detachedWebView!!.loadUrl(DBP_INITIAL_URL)
    }

    private fun resetValues() {
        detachedWebView = null
        currentAction = null
        currentActionIndex = 0
        currentBrokerIndex = 0
        currentBrokerActions = emptyList()
        currentBroker = null
        mainUrlLoaded = null
    }

    private fun proceedToNext() {
        logcat { "PIR-SCAN: proceedToNext" }
        currentActionIndex += 1
        if (currentBrokerActions.size == currentActionIndex) {
            executeNextBroker()
        } else {
            currentAction = currentBrokerActions[currentActionIndex]
            logcat { "PIR-SCAN: do next action: $currentAction" }
            // runBlocking(dispatcherProvider.main()) {
            detachedWebView?.let { brokerActionProcessor.pushAction(pirUserProfile, currentAction!!, it) }
            // }
        }
    }

    private fun executeNextBroker() {
        if (currentBrokerIndex >= _brokers.size - 1) {
            resetValues()
            _onScanComplete()
        } else {
            logcat { "PIR-SCAN: do next action: $currentAction" }
            currentBrokerIndex += 1
            initiateBrokerScan(_brokers[currentBrokerIndex])
        }
    }

    override fun onSuccess(pirSuccessResponse: PirSuccessResponse) {
        runBlocking(dispatcherProvider.main()) {
            logcat { "PIR-SCAN: onSuccess: $pirSuccessResponse" }
            when (pirSuccessResponse) {
                is NavigateResponse -> {
                    repository.saveNavigateResult(currentBroker ?: "", pirSuccessResponse)
                    mainUrlLoaded = pirSuccessResponse.response.url
                    detachedWebView?.loadUrl(mainUrlLoaded!!)
                }

                is ExtractedResponse -> {
                    repository.saveExtractProfileResult(currentBroker ?: "", pirSuccessResponse)
                    proceedToNext()
                }

                else -> {
                    logcat { "PIR-SCAN: Do nothing for $pirSuccessResponse" }
                }
            }
        }
    }

    override fun onError(pirErrorReponse: PirErrorReponse) {
        logcat { "PIR-SCAN: onError: $pirErrorReponse" }
        runBlocking {
            repository.saveErrorResult(
                brokerName = currentBroker ?: "",
                actionType = currentAction?.asActionType() ?: "",
                error = pirErrorReponse,
            )
            proceedToNext()
        }
    }

    private fun initiateBrokerScan(broker: String) {
        runBlocking {
            logcat { "PIR-SCAN: RealPirScan starting steps for $broker" }
            currentBroker = broker
            repository.getBrokerScanSteps(broker)?.run {
                brokerStepsParser.parseStep(this)
            }?.also {
                currentActionIndex = 0
                currentBrokerActions = it.actions
                currentAction = it.actions[0]
                logcat { "PIR-SCAN: Broker steps ${it.actions}" }
                detachedWebView?.let { webview ->
                    if (currentAction != null) {
                        // runBlocking(dispatcherProvider.main()) {
                        brokerActionProcessor.pushAction(pirUserProfile, currentAction!!, webview)
                        // }
                    }
                }
            }
        }
    }

    override fun executeAllBrokers(
        context: Context,
        onScanComplete: () -> Unit,
    ) {
        val problematic = listOf("AdvancedBackgroundChecks")
        val brokers = runBlocking {
            repository.getAllBrokersForScan().filter {
                !problematic.contains(it)
            }
        }
        execute(brokers, context, onScanComplete)
    }

    override fun stop() {
        detachedWebView?.destroy()
    }

    companion object {
        private const val DBP_INITIAL_URL = "dbp://blank"
    }
}
