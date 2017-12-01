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
import com.duckduckgo.app.trackerdetection.Client.ClientName
import com.duckduckgo.app.trackerdetection.model.NetworkTrackers
import com.duckduckgo.app.trackerdetection.model.ResourceType
import timber.log.Timber
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackerDetector @Inject constructor(private val networkTrackers: NetworkTrackers) {

    private val clients = CopyOnWriteArrayList<Client>()

    fun addClient(client: Client) {
        clients.add(client)
    }

    fun hasClient(name: ClientName): Boolean {
        return clients.any { it.name == name }
    }

    fun shouldBlock(url: String, documentUrl: String, resourceType: ResourceType): Boolean {

        if (firstParty(url, documentUrl)) {
            Timber.v("$url is a first party url")
            return false
        }

        val matches = clients.any { it.matches(url, documentUrl, resourceType) }
        val matchText = if (matches) "WAS" else "was not"
        Timber.v("$documentUrl resource $url $matchText identified as a tracker")

        return matches

    }

    private fun firstParty(firstUrl: String, secondUrl: String): Boolean {
        return sameOrSubdomain(firstUrl, secondUrl) || sameOrSubdomain(secondUrl, firstUrl) || sameNetwork(firstUrl, secondUrl)
    }

    private fun sameNetwork(firstUrl: String, secondUrl: String): Boolean {
        return networkTrackers.network(firstUrl) != null && networkTrackers.network(firstUrl) == networkTrackers.network(secondUrl)
    }
}