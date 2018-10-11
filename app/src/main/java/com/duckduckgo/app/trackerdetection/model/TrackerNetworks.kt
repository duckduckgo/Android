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

import com.duckduckgo.app.entities.EntityMapping
import com.duckduckgo.app.entities.db.EntityListEntity
import com.duckduckgo.app.global.UriString.Companion.sameOrSubdomain
import com.duckduckgo.app.privacy.store.PrevalenceStore
import java.io.Serializable
import javax.inject.Inject
import javax.inject.Singleton

interface TrackerNetworks {

    fun updateTrackers(trackers: List<DisconnectTracker>)
    fun network(url: String): TrackerNetwork?

}

@Singleton
class TrackerNetworksImpl @Inject constructor(val prevalenceStore: PrevalenceStore, val entityMapping: EntityMapping) : TrackerNetworks, Serializable {

    private var trackers: List<DisconnectTracker> = emptyList()

    override fun updateTrackers(trackers: List<DisconnectTracker>) {
        this.trackers = trackers
    }

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

