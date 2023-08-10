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

import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import androidx.tracing.Trace
import com.duckduckgo.adclick.api.AdClickManager
import com.duckduckgo.app.global.UriString.Companion.sameOrSubdomain
import com.duckduckgo.app.privacy.db.UserAllowListDao
import com.duckduckgo.app.trackerdetection.Client.ClientType.BLOCKING
import com.duckduckgo.app.trackerdetection.db.WebTrackerBlocked
import com.duckduckgo.app.trackerdetection.db.WebTrackersBlockedDao
import com.duckduckgo.app.trackerdetection.model.TrackerStatus
import com.duckduckgo.app.trackerdetection.model.TrackerType
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.ContentBlocking
import com.duckduckgo.privacy.config.api.TrackerAllowlist
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import timber.log.Timber
import kotlin.random.Random

interface TrackerDetector {
    fun addClient(client: Client)
    fun evaluate(
        url: String,
        documentUrl: String,
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
        url: String,
        documentUrl: String,
        checkFirstParty: Boolean,
        requestHeaders: Map<String, String>,
    ): TrackingEvent? {
        val traceCookie = Random(System.currentTimeMillis()).nextInt()
        Trace.beginAsyncSection("TRACKER_DETECTOR_EVALUATE", traceCookie)
        if (checkFirstParty && firstParty(url, documentUrl)) {
            Timber.v("$url is a first party url")
            Trace.endAsyncSection("TRACKER_DETECTOR_EVALUATE", traceCookie)
            return null
        }

        val result = clients
            .filter { it.name.type == BLOCKING }
            .firstNotNullOfOrNull { it.matches(url, documentUrl, requestHeaders) } ?: Client.Result(matches = false, isATracker = false)

        val sameEntity = sameNetworkName(url, documentUrl)
        val entity = if (result.entityName != null) entityLookup.entityForName(result.entityName) else entityLookup.entityForUrl(url)
        val isSiteAContentBlockingException = contentBlocking.isAnException(documentUrl)
        val isDocumentInAllowedList = userAllowListDao.isDocumentAllowListed(documentUrl)
        val isInAdClickAllowList = adClickManager.isExemption(documentUrl, url)
        val isInTrackerAllowList = trackerAllowlist.isAnException(documentUrl, url)
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
            webTrackersBlockedDao.insert(WebTrackerBlocked(trackerUrl = url, trackerCompany = trackerCompany))
        }

        Timber.v("$documentUrl resource $url WAS identified as a tracker and status=$status")

        val trackingEventResult = TrackingEvent(documentUrl, url, result.categories, entity, result.surrogate, status, type)
        Trace.endAsyncSection("TRACKER_DETECTOR_EVALUATE", traceCookie)
        return trackingEventResult
    }

    private fun firstParty(
        firstUrl: String,
        secondUrl: String,
    ): Boolean =
        sameOrSubdomain(firstUrl, secondUrl) || sameOrSubdomain(secondUrl, firstUrl)

    private fun sameNetworkName(
        url: String,
        documentUrl: String,
    ): Boolean {
        val firstNetwork = entityLookup.entityForUrl(url) ?: return false
        val secondNetwork = entityLookup.entityForUrl(documentUrl) ?: return false
        return firstNetwork.name == secondNetwork.name
    }

    @VisibleForTesting
    val clientCount
        get() = clients.count()
}

private fun UserAllowListDao.isDocumentAllowListed(document: String?): Boolean {
    document?.toUri()?.host?.let {
        return contains(it)
    }
    return false
}
