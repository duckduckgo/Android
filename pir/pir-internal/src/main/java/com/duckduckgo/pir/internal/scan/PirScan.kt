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
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.internal.component.PirDetachedWebViewProvider
import com.duckduckgo.pir.internal.scripts.BrokerActionProcessor
import com.duckduckgo.pir.internal.scripts.BrokerActionProcessor.ActionResultListener
import com.duckduckgo.pir.internal.scripts.PirCssScriptLoader
import com.duckduckgo.pir.internal.scripts.models.BrokerAction
import com.duckduckgo.pir.internal.scripts.models.BrokerAction.Navigate
import com.duckduckgo.pir.internal.scripts.models.PirErrorReponse
import com.duckduckgo.pir.internal.scripts.models.PirSuccessResponse
import com.duckduckgo.pir.internal.scripts.models.PirSuccessResponse.ExtractedResponse
import com.duckduckgo.pir.internal.scripts.models.PirSuccessResponse.NavigateResponse
import com.duckduckgo.pir.internal.scripts.models.ProfileQuery
import com.duckduckgo.pir.internal.scripts.models.asActionType
import com.duckduckgo.pir.internal.store.PirRepository
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import logcat.logcat

interface PirScan {
    /**
     * This method can be used to execute pir scan for a given list of [brokers] names.
     *
     * @param brokers List of broker names
     * @param context Context in which we want to create the detached WebView from
     * @param onScanComplete callback method called when the scan is completed.
     */
    fun execute(
        brokers: List<String>,
        context: Context,
        onScanComplete: () -> Unit,
    )

    /**
     * This method can be used to execute pir scan for all active brokers (from json).
     *
     * @param context Context in which we want to create the detached WebView from
     * @param onScanComplete callback method called when the scan is completed.
     */
    fun executeAllBrokers(
        context: Context,
        onScanComplete: () -> Unit,
    )

    /**
     * This method takes care of stopping the scan and cleaning up resources used.
     */
    fun stop()
}

@ContributesBinding(
    scope = AppScope::class,
    boundType = PirScan::class,
)
@ContributesBinding(
    scope = AppScope::class,
    boundType = ActionResultListener::class,
)
@SingleInstanceIn(AppScope::class)
class RealPirScan @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val pirDetachedWebViewProvider: PirDetachedWebViewProvider,
    private val repository: PirRepository,
    private val brokerStepsParser: BrokerStepsParser,
    private val brokerActionProcessor: BrokerActionProcessor,
    private val pirCssScriptLoader: PirCssScriptLoader,
) : PirScan, ActionResultListener {
    private var profileQuery: ProfileQuery = ProfileQuery(
        firstName = "William",
        lastName = "Smith",
        city = "Chicago",
        state = "IL",
        addresses = listOf(),
        birthYear = 1993,
        fullName = "William Smith",
        age = 34,
        deprecated = false,
    )

    private var detachedWebView: WebView? = null
    private var currentAction: BrokerAction? = null
    private var currentActionIndex: Int = 0
    private var currentBrokerIndex: Int = 0
    private var currentBrokerActions: List<BrokerAction> = emptyList()
    private var currentBroker: String? = null
    private var mainUrlLoaded: String? = null
    private var brokerUrlCompletedLoading: Boolean = false
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
         * 2. Prepare the detached webview - load script and load the dbp url
         * 3. For every broker:
         *      - Get all info for broker
         *      - Get all scan steps for broker
         *      - Execute steps
         * 4. Complete!
         */
        runBlocking {
            repository.deleteAllResults()
            repository.getUserProfiles().also {
                if (it.isNotEmpty()) {
                    // Temporarily taking the first profile only for the PoC. In the reality, more than 1 should be allowed.
                    val storedProfile = it[0]
                    profileQuery = ProfileQuery(
                        firstName = storedProfile.userName.firstName,
                        lastName = storedProfile.userName.lastName,
                        city = storedProfile.addresses.city,
                        state = storedProfile.addresses.state,
                        addresses = listOf(),
                        birthYear = storedProfile.birthYear,
                        fullName = storedProfile.userName.middleName?.run {
                            "${storedProfile.userName.firstName} $this ${storedProfile.userName.lastName}"
                        } ?: "${storedProfile.userName.firstName} ${storedProfile.userName.lastName}",
                        age = storedProfile.age,
                        deprecated = false,
                    )
                }
            }
        }

        _onScanComplete = onScanComplete
        val script = runBlocking {
            pirCssScriptLoader.getScript()
        }

        logcat { "PIR-SCAN: Running scan on profile: $profileQuery" }
        _brokers = brokers
        detachedWebView = pirDetachedWebViewProvider.getInstance(context, script) { url ->
            handleLoadingFinished(url)
        }.apply {
            brokerActionProcessor.register(this@apply, this@RealPirScan)
        }

        // Initial load needed to load script
        mainUrlLoaded = DBP_INITIAL_URL
        detachedWebView!!.loadUrl(DBP_INITIAL_URL)
    }

    private fun handleLoadingFinished(url: String?) {
        logcat { "PIR-SCAN: finished loading $url and mainUrlLoaded: $mainUrlLoaded" }
        // A completed initial scan means we are ready to run the scan for the brokers
        if (url == DBP_INITIAL_URL) {
            logcat { "PIR-SCAN: Run scan for these brokers: $_brokers" }
            initiateBrokerScan(_brokers[0])
        } else if (currentAction is Navigate) {
            // If the current action is still navigate, it means we just finished loading and we can proceed to next action.
            // Sometimes the loaded url gets redirected to another url (could be different domain too) so we can't really check here.
            logcat { "PIR-SCAN: Completed loading for $mainUrlLoaded" }
            brokerUrlCompletedLoading = true
            proceedToNext()
        } else {
            logcat { "PIR-SCAN: Ignoring $url as extraction action has been pushed for the navigate action's loaded url." }
        }
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
        currentActionIndex += 1
        // Actions for the current broker is completed! Proceed to next.
        if (currentBrokerActions.size == currentActionIndex) {
            executeNextBroker()
        } else {
            // Else execute next action
            currentAction = currentBrokerActions[currentActionIndex]
            logcat { "PIR-SCAN: do next action: $currentAction" }
            detachedWebView?.let { brokerActionProcessor.pushAction(profileQuery, currentAction!!, it) }
        }
    }

    private fun executeNextBroker() {
        currentBrokerIndex += 1
        // All brokers have been executed!
        if (currentBrokerIndex >= _brokers.size) {
            resetValues()
            _onScanComplete()
        } else {
            logcat { "PIR-SCAN: Execute next broker." }
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

                    logcat { "PIR-SCAN: Loading real url: $mainUrlLoaded" }
                    brokerUrlCompletedLoading = false
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
                        brokerActionProcessor.pushAction(profileQuery, currentAction!!, webview)
                    }
                }
            }
        }
    }

    override fun executeAllBrokers(
        context: Context,
        onScanComplete: () -> Unit,
    ) {
        val brokers = runBlocking {
            repository.getAllBrokersForScan()
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
