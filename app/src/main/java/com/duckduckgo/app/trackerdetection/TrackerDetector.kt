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

import androidx.core.net.toUri
import com.duckduckgo.app.global.UriString.Companion.sameOrSubdomain
import com.duckduckgo.app.privacy.db.UserWhitelistDao
import com.duckduckgo.app.trackerdetection.db.WebTrackerBlocked
import com.duckduckgo.app.trackerdetection.db.WebTrackersBlockedDao
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import com.duckduckgo.di.scopes.AppObjectGraph
import com.squareup.anvil.annotations.ContributesBinding
import timber.log.Timber
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

interface TrackerDetector {
    fun addClient(client: Client)
    fun evaluate(url: String, documentUrl: String): TrackingEvent?
}

@ContributesBinding(AppObjectGraph::class)
@Singleton
class TrackerDetectorImpl @Inject constructor(
    private val entityLookup: EntityLookup,
    private val userWhitelistDao: UserWhitelistDao,
    private val webTrackersBlockedDao: WebTrackersBlockedDao
) : TrackerDetector {

    private val clients = CopyOnWriteArrayList<Client>()

    /**
     * Adds a new client. If the client's name matches an existing client, old client is replaced
     */
    override fun addClient(client: Client) {
        clients.removeAll { client.name == it.name }
        clients.add(client)
    }

    override fun evaluate(url: String, documentUrl: String): TrackingEvent? {

        if (whitelisted(url, documentUrl)) {
            Timber.v("$documentUrl resource $url is whitelisted")
            return null
        }

        if (firstParty(url, documentUrl)) {
            Timber.v("$url is a first party url")
            return null
        }

        val result = clients
            .filter { it.name.type == Client.ClientType.BLOCKING }
            .mapNotNull { it.matches(url, documentUrl) }
            .firstOrNull { it.matches }

        if (result != null) {
            Timber.v("$documentUrl resource $url WAS identified as a tracker")
            val entity = if (result.entityName != null) entityLookup.entityForName(result.entityName) else null

            val trackerCompany = entity?.displayName ?: "Undefined"
            webTrackersBlockedDao.insert(WebTrackerBlocked(trackerUrl = url, trackerCompany = trackerCompany))
            return TrackingEvent(documentUrl, url, result.categories, entity, !userWhitelistDao.isDocumentWhitelisted(documentUrl))
        }

        Timber.v("$documentUrl resource $url was not identified as a tracker")
        return null
    }

    private fun whitelisted(url: String, documentUrl: String): Boolean {
        return clients.any { it.name.type == Client.ClientType.WHITELIST && it.matches(url, documentUrl).matches }
    }

    private fun firstParty(firstUrl: String, secondUrl: String): Boolean =
        sameOrSubdomain(firstUrl, secondUrl) || sameOrSubdomain(secondUrl, firstUrl) || sameNetworkName(firstUrl, secondUrl)

    private fun sameNetworkName(firstUrl: String, secondUrl: String): Boolean {
        val firstNetwork = entityLookup.entityForUrl(firstUrl) ?: return false
        val secondNetwork = entityLookup.entityForUrl(secondUrl) ?: return false
        return firstNetwork.name == secondNetwork.name
    }

    val clientCount get() = clients.count()
}

private fun UserWhitelistDao.isDocumentWhitelisted(document: String?): Boolean {
    document?.toUri()?.host?.let {
        return contains(it)
    }
    return false
}
