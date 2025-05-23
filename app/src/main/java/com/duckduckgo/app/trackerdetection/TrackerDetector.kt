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
import com.duckduckgo.app.browser.UriString.Companion.sameOrSubdomainPair
import com.duckduckgo.app.privacy.db.UserAllowListDao
import com.duckduckgo.app.trackerdetection.Client.ClientType.BLOCKING
import com.duckduckgo.app.trackerdetection.db.WebTrackerBlocked
import com.duckduckgo.app.trackerdetection.db.WebTrackersBlockedDao
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.app.trackerdetection.model.TrackerStatus
import com.duckduckgo.app.trackerdetection.model.TrackerType
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.ContentBlocking
import com.duckduckgo.privacy.config.api.TrackerAllowlist
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import logcat.LogPriority.VERBOSE
import logcat.logcat

interface TrackerDetector {
    fun addClient(client: Client)

    fun evaluate(
        url: Uri,
        documentUrl: Uri,
        checkFirstParty: Boolean = true,
        requestHeaders: Map<String, String>,
    ): TrackingEvent?

    fun evaluate(
        url: String,
        documentUrl: Uri,
        checkFirstParty: Boolean = true,
        requestHeaders: Map<String, String>,
    ): TrackingEvent?
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class TrackerDetectorImpl @Inject constructor(
    private val entityLookup: EntityLookup,
    private val userAllowListDao: UserAllowListDao,
    private val contentBlocking: ContentBlocking,
    private val trackerAllowlist: TrackerAllowlist,
    private val webTrackersBlockedDao: WebTrackersBlockedDao,
    private val adClickManager: AdClickManager,
) : TrackerDetector {

    private val clients = CopyOnWriteArrayList<Client>()

    /**
     * Adds a new client. If the client's name matches an existing client, old client is replaced
     */
    override fun addClient(client: Client) {
        clients.removeAll { client.name == it.name }
        clients.add(client)
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

        val result = clients
            .filter { it.name.type == BLOCKING }
            .firstNotNullOfOrNull { it.matches(cleanedUrl, documentUrl, requestHeaders) } ?: Client.Result(matches = false, isATracker = false)

        val sameEntity = sameNetworkName(url, documentUrl)
        val entity = if (result.entityName != null) entityLookup.entityForName(result.entityName) else entityLookup.entityForUrl(url)
        val isDocumentInAllowedList = userAllowListDao.isDocumentAllowListed(documentUrl)

        return evaluate(documentUrlString, urlString, result, sameEntity, isDocumentInAllowedList, entity)
    }

    override fun evaluate(
        url: String,
        documentUrl: Uri,
        checkFirstParty: Boolean,
        requestHeaders: Map<String, String>,
    ): TrackingEvent? {
        val cleanedUrl = removePortFromUrl(url)
        val documentUrlString = documentUrl.toString()

        if (checkFirstParty && firstParty(documentUrl, cleanedUrl)) {
            logcat(VERBOSE) { "$url is a first party url" }
            return null
        }

        val result = clients
            .filter { it.name.type == BLOCKING }
            .firstNotNullOfOrNull { it.matches(cleanedUrl, documentUrl, requestHeaders) } ?: Client.Result(matches = false, isATracker = false)

        val sameEntity = sameNetworkName(documentUrl, url)
        val entity = if (result.entityName != null) entityLookup.entityForName(result.entityName) else entityLookup.entityForUrl(url)
        val isDocumentInAllowedList = userAllowListDao.isDocumentAllowListed(documentUrl)

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
        val isSiteAContentBlockingException = contentBlocking.isAnException(documentUrlString)
        val isInAdClickAllowList = adClickManager.isExemption(documentUrlString, urlString)
        val isInTrackerAllowList = trackerAllowlist.isAnException(documentUrlString, urlString)
        val isATrackerAllowed = result.isATracker && !result.matches
        val shouldBlock = result.matches && !isSiteAContentBlockingException && !isInTrackerAllowList && !isInAdClickAllowList && !sameEntity

        val status = when {
            sameEntity -> TrackerStatus.SAME_ENTITY_ALLOWED
            isDocumentInAllowedList -> TrackerStatus.USER_ALLOWED
            shouldBlock -> TrackerStatus.BLOCKED
            isInAdClickAllowList -> TrackerStatus.AD_ALLOWED
            isInTrackerAllowList || isATrackerAllowed -> TrackerStatus.SITE_BREAKAGE_ALLOWED
            else -> TrackerStatus.ALLOWED
        }

        val type = if (isInAdClickAllowList) TrackerType.AD else TrackerType.OTHER

        if (status == TrackerStatus.BLOCKED) {
            val trackerCompany = entity?.displayName ?: "Undefined"
            webTrackersBlockedDao.insert(WebTrackerBlocked(trackerUrl = urlString, trackerCompany = trackerCompany))
        }

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

    private fun removePortFromUrl(url: String): String {
        return try {
            val uri = Uri.parse(url)
            URI(uri.scheme, uri.host, uri.path, uri.fragment).toString()
        } catch (e: Exception) {
            url
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

    private fun sameNetworkName(
        first: Uri,
        second: String,
    ): Boolean {
        val firstNetwork = entityLookup.entityForUrl(first) ?: return false
        val secondNetwork = entityLookup.entityForUrl(second) ?: return false
        return firstNetwork.name == secondNetwork.name
    }

    private fun sameNetworkName(
        url: Uri,
        documentUrl: Uri,
    ): Boolean {
        val firstNetwork = entityLookup.entityForUrl(url) ?: return false
        val secondNetwork = entityLookup.entityForUrl(documentUrl) ?: return false
        return firstNetwork.name == secondNetwork.name
    }

    @VisibleForTesting
    val clientCount
        get() = clients.count()
}

private fun UserAllowListDao.isDocumentAllowListed(document: Uri?): Boolean {
    document?.host?.let {
        return contains(it)
    }
    return false
}
