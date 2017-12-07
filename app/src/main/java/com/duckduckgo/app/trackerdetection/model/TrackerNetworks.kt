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
                TrackerNetwork("google", "google.com", 84, true),
                TrackerNetwork("facebook", "facebook.com", 36, true),
                TrackerNetwork("twitter", "twitter.com", 16, true),
                TrackerNetwork("amazon.com", "amazon.com", 14, true),
                TrackerNetwork("appnexus", "appnexus.com", 10, true),
                TrackerNetwork("oracle", "oracle.com", 10, true),
                TrackerNetwork("mediamath", "mediamath.com", 9, true),
                TrackerNetwork("yahoo", "yahoo.com", 9, true),
                TrackerNetwork("maxcdn", "maxcdn.com", 7, true),
                TrackerNetwork("automattic", "automattic.com", 7, true)
        )
    }

    private var data: List<DisconnectTracker> = ArrayList()

    fun updateData(trackers: List<DisconnectTracker>) {
        this.data = trackers
    }

    fun network(url: String): TrackerNetwork? {
        val entry = data.find { sameOrSubdomain(url, it.url) || sameOrSubdomain(url, it.networkUrl) } ?: return null
        return majorNetwork(entry.networkName) ?: TrackerNetwork(name = entry.networkName, url = entry.networkUrl)
    }

    private fun majorNetwork(networkName: String): TrackerNetwork? {
        return majorNetworks.find { it.name.equals(networkName, true) }
    }
}

