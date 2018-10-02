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

import com.duckduckgo.app.global.UriString.Companion.sameOrSubdomain
import com.duckduckgo.app.privacy.store.PrevalenceStore
import java.io.Serializable
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class TrackerNetworks @Inject constructor(val prevalenceStore: PrevalenceStore) : Serializable {

    private var data: List<DisconnectTracker> = ArrayList()

    fun updateData(trackers: List<DisconnectTracker>) {
        this.data = trackers
    }

    fun network(url: String): TrackerNetwork? {
        val disconnectEntry = data.find { sameOrSubdomain(url, it.url) || sameOrSubdomain(url, it.networkUrl) } ?: return null
        val prevalence = prevalenceStore.findPrevalenceOf(disconnectEntry.networkName)

        return TrackerNetwork(
            name = disconnectEntry.networkName,
            url = disconnectEntry.networkUrl,
            category = disconnectEntry.category,
            isMajor = (prevalence ?: 0.0) > MAJOR_NETWORK_PREVALENCE
        )
    }

    companion object {

        const val MAJOR_NETWORK_PREVALENCE = 7.0

    }

}

