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
import com.duckduckgo.app.privacymonitor.store.PrivacySettingsStore
import com.duckduckgo.app.trackerdetection.model.ResourceType
import com.duckduckgo.app.trackerdetection.model.TrackerNetworks
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import timber.log.Timber
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackerDetector @Inject constructor(private val networkTrackers: TrackerNetworks, private val settings: PrivacySettingsStore) {

    private val clients = CopyOnWriteArrayList<Client>()

    /**
     * Adds a new client. If the client's name matches an existing client, old client is replaced
     */
    fun addClient(client: Client) {
        clients.removeAll { client.name == it.name }
        clients.add(client)
    }

    fun evaluate(url: String, documentUrl: String, resourceType: ResourceType): TrackingEvent? {

        val whitelisted = clients.any { it.name.type == Client.ClientType.WHITELIST && it.matches(url, documentUrl, resourceType) }
        if (whitelisted) {
            Timber.v("$documentUrl resource $url is whitelisted")
            return null
        }

        if (firstParty(url, documentUrl)) {
            Timber.v("$url is a first party url")
            return null
        }

        val matches = clients.any { it.name.type == Client.ClientType.BLOCKING && it.matches(url, documentUrl, resourceType) }
        if (matches) {
            Timber.v("$documentUrl resource $url WAS identified as a tracker")
            return TrackingEvent(documentUrl, url, networkTrackers.network(url), settings.privacyOn)
        }

        Timber.v("$documentUrl resource $url was not identified as a tracker")
        return null
    }

    private fun firstParty(firstUrl: String, secondUrl: String): Boolean =
            sameOrSubdomain(firstUrl, secondUrl) || sameOrSubdomain(secondUrl, firstUrl) || sameNetworkName(firstUrl, secondUrl)

    private fun sameNetworkName(firstUrl: String, secondUrl: String): Boolean =
            networkTrackers.network(firstUrl) != null && networkTrackers.network(firstUrl)?.name == networkTrackers.network(secondUrl)?.name


    val clientCount get() = clients.count()
}