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
import java.io.Serializable
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class TrackerNetworks @Inject constructor() : Serializable {

    companion object {
        private var majorNetworks = arrayOf(
                TrackerNetwork(name = "google", url = "google.com", percentageOfPages = 84, isMajor = true),
                TrackerNetwork(name = "facebook", url = "facebook.com", percentageOfPages = 36, isMajor = true),
                TrackerNetwork(name = "twitter", url = "twitter.com", percentageOfPages = 16, isMajor = true),
                TrackerNetwork(name = "amazon.com", url = "amazon.com", percentageOfPages = 14, isMajor = true),
                TrackerNetwork(name = "appnexus", url = "appnexus.com", percentageOfPages = 10, isMajor = true),
                TrackerNetwork(name = "oracle", url = "oracle.com", percentageOfPages = 10, isMajor = true),
                TrackerNetwork(name = "mediamath", url = "mediamath.com", percentageOfPages = 9, isMajor = true),
                TrackerNetwork(name = "yahoo", url = "yahoo.com", percentageOfPages = 9, isMajor = true),
                TrackerNetwork(name = "stackpath", url = "stackpath.com", percentageOfPages = 7, isMajor = true),
                TrackerNetwork(name = "automattic", url = "automattic.com", percentageOfPages = 7, isMajor = true)
        )
    }

    private var data: List<DisconnectTracker> = ArrayList()

    fun updateData(trackers: List<DisconnectTracker>) {
        this.data = trackers
    }

    fun network(url: String): TrackerNetwork? {
        val disconnect = data.find { sameOrSubdomain(url, it.url) || sameOrSubdomain(url, it.networkUrl) } ?: return null
        val major = majorNetwork(disconnect.networkName)
        val combined = TrackerNetwork(
                name = disconnect.networkName,
                url = disconnect.networkUrl,
                category = disconnect.category,
                percentageOfPages = major?.percentageOfPages,
                isMajor = major != null)
        return combined
    }

    private fun majorNetwork(networkName: String): TrackerNetwork? {
        return majorNetworks.find { it.name.equals(networkName, true) }
    }
}

