/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.trackerdetection

import com.duckduckgo.app.global.UriString.Companion.sameOrSubdomain
import com.duckduckgo.app.global.performance.measureExecution
import com.duckduckgo.app.privacy.store.PrivacySettingsStore
import com.duckduckgo.app.trackerdetection.model.ResourceType
import com.duckduckgo.app.trackerdetection.model.TrackerNetworks
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import timber.log.Timber
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Named

interface TrackerDetector {
    fun addClient(client: Client)
    fun evaluate(url: String, documentUrl: String, resourceType: ResourceType): TrackingEvent?
}

class TrackerDetectorImpl(
    @Named("oldTrackerNetworks") private val oldNetworkTrackers: TrackerNetworks,
    @Named("newTrackerNetworks") private val newNetworkTrackers: TrackerNetworks,
    private val settings: PrivacySettingsStore
) : TrackerDetector {

    private val clients = CopyOnWriteArrayList<Client>()

    /**
     * Adds a new client. If the client's name matches an existing client, old client is replaced
     */
    override fun addClient(client: Client) {
        clients.removeAll { client.name == it.name }
        clients.add(client)
    }

    override fun evaluate(url: String, documentUrl: String, resourceType: ResourceType): TrackingEvent? {

        // mean=4, worst=57
        val whitelisted = measureExecution("whitelisted") {
            clients.any { it.name.type == Client.ClientType.WHITELIST && it.matches(url, documentUrl, resourceType) }
        }
        if (whitelisted) {
            Timber.v("$documentUrl resource $url is whitelisted")
            return null
        }

        // fast when it IS a first party; slow when not
        // mean=407, worst=1327
        val isFirstParty = measureExecution(logMessage = "firstParty") { firstParty(url, documentUrl) }
        if (isFirstParty) {
            Timber.v("$url is a first party url")
            return null
        }

        // mean = 100-170 (slightly quicker when it does match)
        val matches = measureExecution("matchesBlocker") {
            clients.any {
                it.name.type == Client.ClientType.BLOCKING && it.matches(
                    url,
                    documentUrl,
                    resourceType
                )
            }
        }
        if (matches) {
            Timber.v("$documentUrl resource $url WAS identified as a tracker")
            val networkTrackers = measureExecution("oldNetworkTrackers") { oldNetworkTrackers.network(url) }
            measureExecution("newNetworkTrackers") { newNetworkTrackers.network(url) }
            return TrackingEvent(documentUrl, url, networkTrackers, settings.privacyOn)
        }

        Timber.v("$documentUrl resource $url was not identified as a tracker")
        return null
    }

    private fun firstParty(firstUrl: String, secondUrl: String): Boolean =
        sameOrSubdomain(firstUrl, secondUrl) || sameOrSubdomain(secondUrl, firstUrl) || sameNetworkName(firstUrl, secondUrl)

    private fun sameNetworkName(firstUrl: String, secondUrl: String): Boolean {
        // mean=391,worst=1005

        measureExecution("sameNetworkNameNew") {
            val firstNetwork = newNetworkTrackers.network(firstUrl) ?: return@measureExecution false
            val secondNetwork = newNetworkTrackers.network(secondUrl) ?: return@measureExecution false
            return@measureExecution firstNetwork.name == secondNetwork.name
        }

        return measureExecution("sameNetworkNameOld") {
            val firstNetwork = oldNetworkTrackers.network(firstUrl) ?: return@measureExecution false
            val secondNetwork = oldNetworkTrackers.network(secondUrl) ?: return@measureExecution false
            return@measureExecution firstNetwork.name == secondNetwork.name
        }
    }


    val clientCount get() = clients.count()
}