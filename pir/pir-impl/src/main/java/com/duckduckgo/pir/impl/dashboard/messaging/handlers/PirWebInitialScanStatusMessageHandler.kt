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

package com.duckduckgo.pir.impl.dashboard.messaging.handlers

import android.content.Context
import android.content.Intent
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.pir.impl.dashboard.messaging.PirDashboardWebMessages
import com.duckduckgo.pir.impl.dashboard.messaging.model.PirWebMessageResponse
import com.duckduckgo.pir.impl.dashboard.messaging.model.PirWebMessageResponse.GetDataBrokersResponse.DataBroker
import com.duckduckgo.pir.impl.dashboard.messaging.model.PirWebMessageResponse.ScanResult
import com.duckduckgo.pir.impl.dashboard.messaging.model.PirWebMessageResponse.ScanResult.ScanResultAddress
import com.duckduckgo.pir.impl.dashboard.messaging.model.PirWebMessageResponse.ScannedBroker
import com.duckduckgo.pir.impl.dashboard.state.PirDashboardInitialScanStateProvider
import com.duckduckgo.pir.impl.scan.PirForegroundScanService
import com.duckduckgo.pir.impl.store.PirRepository
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority
import logcat.logcat
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * Handles the initial scan status message from Web which is used to retrieve the status of the initial scan.
 */
@ContributesMultibinding(
    scope = ActivityScope::class,
    boundType = PirWebJsMessageHandler::class,
)
class PirWebInitialScanStatusMessageHandler @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val stateProvider: PirDashboardInitialScanStateProvider,
    private val pirRepository: PirRepository,
    private val context: Context,
) : PirWebJsMessageHandler() {

    override val message = PirDashboardWebMessages.INITIAL_SCAN_STATUS
    private val checkedForResumeScan: AtomicBoolean = AtomicBoolean(false)

    override fun process(
        jsMessage: JsMessage,
        jsMessaging: JsMessaging,
        jsMessageCallback: JsMessageCallback?,
    ) {
        logcat { "PIR-WEB: InitialScanStatusMessageHandler: process $jsMessage" }

        appCoroutineScope.launch(dispatcherProvider.io()) {
            if (!canRunScan()) {
                jsMessaging.sendResponse(
                    jsMessage = jsMessage,
                    response = PirWebMessageResponse.InitialScanResponse.EMPTY,
                )
                return@launch
            }

            // Check once per PirDashboardWebViewActivity launch if we need to resume a scan that was interrupted (e.g., by app kill)
            if (!checkedForResumeScan.getAndSet(true)) {
                resumeInitialScanIfNeeded()
            }

            jsMessaging.sendResponse(
                jsMessage = jsMessage,
                response = PirWebMessageResponse.InitialScanResponse(
                    resultsFound = getResultsFound(),
                    scanProgress = PirWebMessageResponse.InitialScanResponse.ScanProgress(
                        currentScans = stateProvider.getFullyCompletedBrokersTotal(),
                        totalScans = stateProvider.getActiveBrokersAndMirrorSitesTotal(),
                        scannedBrokers = getScannedBrokers(),
                    ),
                ),
            )
        }
    }

    private suspend fun canRunScan(): Boolean {
        return pirRepository.getValidUserProfileQueries().isNotEmpty()
    }

    /**
     * Resumes the initial foreground scan if it was interrupted (e.g., by app kill)
     * and there are remaining brokers to scan.
     */
    private suspend fun resumeInitialScanIfNeeded() {
        if (!stateProvider.shouldRestartInitialScan()) {
            logcat { "PIR-WEB: No need to resume scan, it's either not started or already completed" }
            return
        }

        // Start the foreground scan service to resume the scan on remaining brokers
        val intent = Intent(context, PirForegroundScanService::class.java)
        try {
            context.startForegroundService(intent)
            logcat { "PIR-WEB: Successfully resumed foreground scan service" }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "PIR-WEB: Failed to start foreground scan service: ${e.message}" }
        }
    }

    private suspend fun getResultsFound(): List<ScanResult> {
        return stateProvider.getScanResults().map {
            ScanResult(
                id = it.extractedProfile.dbId,
                dataBroker = DataBroker(
                    name = it.broker.name,
                    url = it.broker.url,
                    optOutUrl = it.broker.optOutUrl,
                    parentURL = it.broker.parentUrl,
                ),
                name = it.extractedProfile.name,
                addresses = it.extractedProfile.addresses.map { address ->
                    ScanResultAddress(
                        city = address.city,
                        state = address.state,
                    )
                },
                alternativeNames = it.extractedProfile.alternativeNames,
                relatives = it.extractedProfile.relatives,
                foundDate = it.extractedProfile.dateAddedInMillis.convertToSeconds(),
                optOutSubmittedDate = it.optOutSubmittedDateInMillis?.convertToSeconds(),
                estimatedRemovalDate = it.estimatedRemovalDateInMillis?.convertToSeconds(),
                removedDate = it.optOutRemovedDateInMillis?.convertToSeconds(),
                hasMatchingRecordOnParentBroker = it.hasMatchingRecordOnParentBroker,
            )
        }
    }

    private fun Long.convertToSeconds(): Long {
        return TimeUnit.MILLISECONDS.toSeconds(this)
    }

    private suspend fun getScannedBrokers(): List<ScannedBroker> {
        return stateProvider.getAllScannedBrokersStatus().map {
            ScannedBroker(
                name = it.broker.name,
                url = it.broker.url,
                optOutUrl = it.broker.optOutUrl,
                parentURL = it.broker.parentUrl,
                status = it.status.statusName,
            )
        }
    }
}
