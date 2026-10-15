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
import com.duckduckgo.pir.impl.checker.PirRunMode
import com.duckduckgo.pir.impl.checker.PirWorkHandler
import com.duckduckgo.pir.impl.checker.runModeOrNull
import com.duckduckgo.pir.impl.dashboard.messaging.PirDashboardWebMessages
import com.duckduckgo.pir.impl.dashboard.messaging.model.PirWebMessageResponse
import com.duckduckgo.pir.impl.freemium.PirFreeScanBrokerFilter
import com.duckduckgo.pir.impl.store.PirRepository
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import logcat.logcat
import javax.inject.Inject

/**
 * Handles the getDataBrokers message from Web which is used
 * to retrieve the list of all data brokers the client has / supports.
 */
@ContributesMultibinding(
    scope = ActivityScope::class,
    boundType = PirWebJsMessageHandler::class,
)
class PirWebGetDataBrokersMessageHandler @Inject constructor(
    private val repository: PirRepository,
    private val dispatcherProvider: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val pirWorkHandler: PirWorkHandler,
    private val pirFreeScanBrokerFilter: PirFreeScanBrokerFilter,
) : PirWebJsMessageHandler() {

    override val message = PirDashboardWebMessages.GET_DATA_BROKERS

    override fun process(
        jsMessage: JsMessage,
        jsMessaging: JsMessaging,
        jsMessageCallback: JsMessageCallback?,
    ) {
        logcat { "PIR-WEB: PirWebGetDataBrokersMessageHandler: process $jsMessage" }

        appCoroutineScope.launch(dispatcherProvider.io()) {
            jsMessaging.sendResponse(
                jsMessage,
                response = PirWebMessageResponse.GetDataBrokersResponse(
                    dataBrokers = getDataBrokers(),
                ),
            )
        }
    }

    private suspend fun getDataBrokers(): List<PirWebMessageResponse.GetDataBrokersResponse.DataBroker> {
        val isFreeScan = pirWorkHandler.canRunPir().firstOrNull().runModeOrNull == PirRunMode.SCAN_ONLY

        val activeBrokerObjects = repository.getAllActiveBrokerObjects().let {
            if (isFreeScan) pirFreeScanBrokerFilter.excludingGatedBrokers(it) else it
        }
        val activeBrokers = activeBrokerObjects.associateBy { it.name }

        // A mirror inherits its parent's scan steps, so a gated broker's mirror is equally unscannable.
        val mirrorSites = repository.getAllMirrorSites().filter {
            it.removedAt == 0L && (!isFreeScan || activeBrokers.containsKey(it.parentSite))
        }
        val brokerOptOutUrls = repository.getAllBrokerOptOutUrls()

        val mappedBrokers = activeBrokers.values.map {
            PirWebMessageResponse.GetDataBrokersResponse.DataBroker(
                url = it.url,
                name = it.name,
                parentURL = it.parent,
                optOutUrl = brokerOptOutUrls[it.name],
            )
        }
        val mappedMirrorSites = mirrorSites.map {
            PirWebMessageResponse.GetDataBrokersResponse.DataBroker(
                url = it.url,
                name = it.name,
                parentURL = activeBrokers[it.parentSite]?.url,
                optOutUrl = brokerOptOutUrls[it.parentSite],
            )
        }

        return (mappedBrokers + mappedMirrorSites).distinct()
    }
}
