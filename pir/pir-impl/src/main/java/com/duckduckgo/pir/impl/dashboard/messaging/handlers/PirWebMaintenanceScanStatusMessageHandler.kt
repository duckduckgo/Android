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

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.pir.impl.dashboard.messaging.PirDashboardWebMessages
import com.duckduckgo.pir.impl.dashboard.messaging.model.PirWebMessageResponse
import com.duckduckgo.pir.impl.dashboard.messaging.model.PirWebMessageResponse.GetDataBrokersResponse.DataBroker
import com.duckduckgo.pir.impl.dashboard.messaging.model.PirWebMessageResponse.MaintenanceScanStatusResponse.ScanDetail
import com.duckduckgo.pir.impl.dashboard.messaging.model.PirWebMessageResponse.MaintenanceScanStatusResponse.ScanHistory
import com.duckduckgo.pir.impl.dashboard.messaging.model.PirWebMessageResponse.MaintenanceScanStatusResponse.ScanSchedule
import com.duckduckgo.pir.impl.dashboard.messaging.model.PirWebMessageResponse.ScannedBroker
import com.duckduckgo.pir.impl.dashboard.state.PirDashboardMaintenanceScanDataProvider
import com.duckduckgo.pir.impl.dashboard.state.PirDashboardMaintenanceScanDataProvider.DashboardBrokerMatch
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.logcat
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@ContributesMultibinding(
    scope = ActivityScope::class,
    boundType = PirWebJsMessageHandler::class,
)
class PirWebMaintenanceScanStatusMessageHandler @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val statusProvider: PirDashboardMaintenanceScanDataProvider,
) : PirWebJsMessageHandler() {

    override val message: PirDashboardWebMessages = PirDashboardWebMessages.MAINTENANCE_SCAN_STATUS

    override fun process(
        jsMessage: JsMessage,
        jsMessaging: JsMessaging,
        jsMessageCallback: JsMessageCallback?,
    ) {
        logcat { "PIR-WEB: PirWebMaintenanceScanStatusMessageHandler: process $jsMessage" }
        appCoroutineScope.launch(dispatcherProvider.io()) {
            jsMessaging.sendResponse(
                jsMessage = jsMessage,
                response = PirWebMessageResponse.MaintenanceScanStatusResponse(
                    inProgressOptOuts = getInProgressOptOuts(),
                    completedOptOuts = getCompletedOptOuts(),
                    scanSchedule = getScanSchedule(),
                    scanHistory = getScanHistory(),
                ),
            )
        }
    }

    private suspend fun getScanHistory(): ScanHistory {
        return ScanHistory(
            sitesScanned = statusProvider.getScannedBrokerCount(),
        )
    }

    private suspend fun getScanSchedule(): ScanSchedule {
        val lastScanDetails = statusProvider.getLastScanDetails()
        val nextScanDetails = statusProvider.getNextScanDetails()
        return ScanSchedule(
            lastScan = ScanDetail(
                date = lastScanDetails.dateInMillis.convertToSeconds(),
                dataBrokers = lastScanDetails.brokerMatches.map { it.toScannedBroker() },
            ),
            nextScan = ScanDetail(
                date = nextScanDetails.dateInMillis.convertToSeconds(),
                dataBrokers = nextScanDetails.brokerMatches.map { it.toScannedBroker() },
            ),
        )
    }

    private fun DashboardBrokerMatch.toScannedBroker(): ScannedBroker {
        return ScannedBroker(
            name = broker.name,
            url = broker.url,
            optOutUrl = broker.optOutUrl,
            parentURL = broker.parentUrl,
            date = this.dateInMillis.convertToSeconds(),
        )
    }

    private suspend fun getCompletedOptOuts(): List<PirWebMessageResponse.ScanResult> {
        return statusProvider.getRemovedOptOuts().map {
            PirWebMessageResponse.ScanResult(
                dataBroker = DataBroker(
                    name = it.result.broker.name,
                    url = it.result.broker.url,
                    optOutUrl = it.result.broker.optOutUrl,
                    parentURL = it.result.broker.parentUrl,
                ),
                name = it.result.extractedProfile.name,
                addresses = it.result.extractedProfile.addresses.map { address ->
                    PirWebMessageResponse.ScanResult.ScanResultAddress(
                        city = address.city,
                        state = address.state,
                    )
                },
                alternativeNames = it.result.extractedProfile.alternativeNames,
                relatives = it.result.extractedProfile.relatives,
                foundDate = it.result.extractedProfile.dateAddedInMillis.convertToSeconds(),
                optOutSubmittedDate = it.result.optOutSubmittedDateInMillis?.convertToSeconds(),
                estimatedRemovalDate = it.result.estimatedRemovalDateInMillis?.convertToSeconds(),
                removedDate = it.result.optOutRemovedDateInMillis?.convertToSeconds(),
                hasMatchingRecordOnParentBroker = it.result.hasMatchingRecordOnParentBroker,
                matches = it.matches,
            )
        }
    }

    private suspend fun getInProgressOptOuts(): List<PirWebMessageResponse.ScanResult> {
        return statusProvider.getInProgressOptOuts().map {
            PirWebMessageResponse.ScanResult(
                dataBroker = DataBroker(
                    name = it.broker.name,
                    url = it.broker.url,
                    optOutUrl = it.broker.optOutUrl,
                    parentURL = it.broker.parentUrl,
                ),
                name = it.extractedProfile.name,
                addresses = it.extractedProfile.addresses.map { address ->
                    PirWebMessageResponse.ScanResult.ScanResultAddress(
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
}
