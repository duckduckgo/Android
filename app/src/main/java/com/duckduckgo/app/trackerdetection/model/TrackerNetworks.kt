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

package com.duckduckgo.app.trackerdetection.model

import androidx.annotation.WorkerThread
import androidx.core.net.toUri
import com.duckduckgo.app.entities.EntityMapping
import com.duckduckgo.app.global.UriString.Companion.sameOrSubdomain
import com.duckduckgo.app.global.uri.removeOneSubdomain
import com.duckduckgo.app.privacy.store.PrevalenceStore
import com.duckduckgo.app.trackerdetection.db.TrackerDataDao
import java.io.Serializable

interface TrackerNetworks {

    fun updateTrackers(trackers: List<DisconnectTracker>)
    fun network(url: String): TrackerNetwork?

}

class TrackerNetworksImpl(private val prevalenceStore: PrevalenceStore, private val entityMapping: EntityMapping) : TrackerNetworks,
    Serializable {

    private var trackers: List<DisconnectTracker> = emptyList()

    override fun updateTrackers(trackers: List<DisconnectTracker>) {
        this.trackers = trackers
    }

    @WorkerThread
    override fun network(url: String): TrackerNetwork? {

        val entity = entityMapping.entityForUrl(url) ?: return null
        val tracker = trackers.find { sameOrSubdomain(url, it.url) }

        val prevalence = prevalenceStore.findPrevalenceOf(entity.entityName)

        return TrackerNetwork(
            name = entity.entityName,
            category = tracker?.category,
            isMajor = (prevalence ?: 0.0) > MAJOR_NETWORK_PREVALENCE
        )
    }

    companion object {

        const val MAJOR_NETWORK_PREVALENCE = 7.0

    }
}

class TrackerNetworksDirectDbLookup(
    private val prevalenceStore: PrevalenceStore,
    private val entityMapping: EntityMapping,
    private val trackerDataDao: TrackerDataDao
) : TrackerNetworks {

    override fun updateTrackers(trackers: List<DisconnectTracker>) {
        // nothing to do here
    }

    @WorkerThread
    override fun network(url: String): TrackerNetwork? {

        val entity = entityMapping.entityForUrl(url) ?: return null
        val tracker = recursivelyFindMatchingTracker(url)
        val prevalence = prevalenceStore.findPrevalenceOf(entity.entityName)

        return TrackerNetwork(
            name = entity.entityName,
            category = tracker?.category,
            isMajor = (prevalence ?: 0.0) > TrackerNetworksImpl.MAJOR_NETWORK_PREVALENCE
        )
    }

    private fun recursivelyFindMatchingTracker(url: String): DisconnectTracker? {
        val uri = url.toUri()
        val host = uri.host ?: return null

        // try searching for exact domain
        val direct = lookUpTrackerInDatabase(host)
        if (direct != null) {
            return direct
        }

        // remove the first subdomain, and try again
        val parentDomain = uri.removeOneSubdomain() ?: return null
        return recursivelyFindMatchingTracker(parentDomain)
    }

    @WorkerThread
    private fun lookUpTrackerInDatabase(url: String): DisconnectTracker? {
        return trackerDataDao.get(url)
    }
}

