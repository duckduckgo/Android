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

import android.net.Uri
import androidx.annotation.VisibleForTesting
import com.duckduckgo.adclick.api.AdClickManager
import com.duckduckgo.app.browser.UriString.Companion.removePort
import com.duckduckgo.app.browser.UriString.Companion.sameOrSubdomainPair
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.app.trackerdetection.Client.ClientType.BLOCKING
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.app.trackerdetection.model.TrackerStatus
import com.duckduckgo.app.trackerdetection.model.TrackerType
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.ContentBlocking
import com.duckduckgo.privacy.config.api.TrackerAllowlist
import com.duckduckgo.tracker.detection.api.TrackerDetector
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import logcat.LogPriority.VERBOSE
import logcat.logcat
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject

@ContributesBinding(AppScope::class, boundType = TrackerDetector::class)
@ContributesBinding(AppScope::class, boundType = TrackerDetectorClientProvider::class)
@SingleInstanceIn(AppScope::class)
class TrackerDetectorImpl @Inject constructor(
    private val entityLookup: EntityLookup,
    private val userAllowListRepository: UserAllowListRepository,
    private val contentBlocking: ContentBlocking,
    private val trackerAllowlist: TrackerAllowlist,
    private val adClickManager: AdClickManager,
) : TrackerDetector, TrackerDetectorClientProvider {

    private val clients = CopyOnWriteArrayList<Client>()

    /**
     * We're only interested in [BLOCKING] clients. Recomputed only when [addClient] runs. Evaluation reads
     * this directly instead of filtering [clients] on every request.
     */
    @Volatile
    private var blockingClients: List<Client> = emptyList()

    /**
     * Adds a new client. If the client's name matches an existing client, old client is replaced
     */
    override fun addClient(client: Client) {
        clients.removeAll { client.name == it.name }
        clients.add(client)
        blockingClients = clients.filter { it.name.type == BLOCKING }
    }

    override fun evaluate(
        url: Uri,
        documentUrl: Uri,
        checkFirstParty: Boolean,
        requestHeaders: Map<String, String>,
    ): TrackingEvent? {
        val cleanedUrl = removePortFromUrl(url)
        val urlString = url.toString()
        val documentUrlString = documentUrl.toString()

        if (checkFirstParty && firstParty(cleanedUrl, documentUrl)) {
            logcat(VERBOSE) { "$url is a first party url" }
            return null
        }

        val result = blockingClients
            .firstNotNullOfOrNull { it.matches(cleanedUrl, documentUrl, requestHeaders) } ?: Client.Result.NO_MATCH

        val urlNetwork = entityLookup.entityForUrl(url)
        val sameEntity = sameNetwork(urlNetwork, documentUrl)
        val entity = if (result.entityName != null) entityLookup.entityForName(result.entityName) else urlNetwork
        val isDocumentInAllowedList = userAllowListRepository.isDocumentAllowListed(documentUrl)

        return evaluate(documentUrlString, urlString, result, sameEntity, isDocumentInAllowedList, entity)
    }

    override fun evaluate(
        url: String,
        documentUrl: Uri,
        checkFirstParty: Boolean,
        requestHeaders: Map<String, String>,
    ): TrackingEvent? {
        val cleanedUrl = removePort(url)
        val documentUrlString = documentUrl.toString()

        if (checkFirstParty && firstParty(documentUrl, cleanedUrl)) {
            logcat(VERBOSE) { "$url is a first party url" }
            return null
        }

        val result = blockingClients
            .firstNotNullOfOrNull { it.matches(cleanedUrl, documentUrl, requestHeaders) } ?: Client.Result.NO_MATCH

        val urlNetwork = entityLookup.entityForUrl(url)
        val sameEntity = sameNetwork(urlNetwork, documentUrl)
        val entity = if (result.entityName != null) entityLookup.entityForName(result.entityName) else urlNetwork
        val isDocumentInAllowedList = userAllowListRepository.isDocumentAllowListed(documentUrl)

        return evaluate(documentUrlString, url, result, sameEntity, isDocumentInAllowedList, entity)
    }

    private fun evaluate(
        documentUrlString: String,
        urlString: String,
        result: Client.Result,
        sameEntity: Boolean,
        isDocumentInAllowedList: Boolean,
        entity: Entity?,
    ): TrackingEvent {
        val isInAdClickAllowList = adClickManager.isExemption(documentUrlString, urlString)
        val isInTrackerAllowList = trackerAllowlist.isAnException(documentUrlString, urlString)
        val isATrackerAllowed = result.isATracker && !result.matches
        val shouldBlock = result.matches &&
            !sameEntity &&
            !isInTrackerAllowList &&
            !isInAdClickAllowList &&
            !contentBlocking.isAnException(documentUrlString)

        val status = when {
            sameEntity -> TrackerStatus.SAME_ENTITY_ALLOWED
            isDocumentInAllowedList -> TrackerStatus.USER_ALLOWED
            shouldBlock -> TrackerStatus.BLOCKED
            isInAdClickAllowList -> TrackerStatus.AD_ALLOWED
            isInTrackerAllowList || isATrackerAllowed -> TrackerStatus.SITE_BREAKAGE_ALLOWED
            else -> TrackerStatus.ALLOWED
        }

        val type = if (isInAdClickAllowList) TrackerType.AD else TrackerType.OTHER

        logcat(VERBOSE) { "$documentUrlString resource $urlString WAS identified as a tracker and status=$status" }

        return TrackingEvent(documentUrlString, urlString, result.categories, entity, result.surrogate, status, type)
    }

    private fun removePortFromUrl(uri: Uri): Uri {
        return if (uri.port != -1) {
            uri.buildUpon()
                .authority(uri.host)
                .build()
        } else {
            uri
        }
    }

    private fun firstParty(
        firstUrl: Uri,
        secondUrl: String,
    ): Boolean =
        sameOrSubdomainPair(firstUrl, secondUrl)

    private fun firstParty(
        firstUrl: Uri,
        secondUrl: Uri,
    ): Boolean =
        sameOrSubdomainPair(firstUrl, secondUrl)

    private fun sameNetwork(
        urlNetwork: Entity?,
        documentUrl: Uri,
    ): Boolean {
        if (urlNetwork == null) return false
        val documentNetwork = entityLookup.entityForUrl(documentUrl) ?: return false
        return urlNetwork.name == documentNetwork.name
    }

    @VisibleForTesting
    val clientCount
        get() = clients.count()
}

private fun UserAllowListRepository.isDocumentAllowListed(document: Uri?): Boolean {
    document?.host?.let {
        return isDomainInUserAllowList(it)
    }
    return false
}
