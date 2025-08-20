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
import com.duckduckgo.pir.impl.store.PirRepository
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.logcat

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
        val activeBrokers = repository.getAllActiveBrokerObjects().associateBy { it.name }
        val mirrorSites = repository.getAllMirrorSites().filter { it.removedAt == 0L }
        val brokerOptOuts = repository.getAllBrokerOptOuts().associate { it.brokerName to it.optOutUrl }

        val mappedBrokers = activeBrokers.values.map {
            PirWebMessageResponse.GetDataBrokersResponse.DataBroker(
                url = it.url,
                name = it.name,
                parentURL = it.parent,
                optOutUrl = brokerOptOuts[it.name],
            )
        }
        val mappedMirrorSites = mirrorSites.map {
            PirWebMessageResponse.GetDataBrokersResponse.DataBroker(
                url = it.url,
                name = it.name,
                parentURL = activeBrokers[it.parentSite]?.url,
                optOutUrl = brokerOptOuts[it.parentSite],
            )
        }

        return (mappedBrokers + mappedMirrorSites).distinct()
    }
}
