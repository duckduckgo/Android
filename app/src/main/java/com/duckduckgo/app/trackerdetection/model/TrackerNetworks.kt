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
        var majorNetworks = arrayOf(
                TrackerNetwork(name = "Google", url = "google.com", percentageOfPages = 84, isMajor = true),
                TrackerNetwork(name = "Facebook", url = "facebook.com", percentageOfPages = 36, isMajor = true),
                TrackerNetwork(name = "Twitter", url = "twitter.com", percentageOfPages = 16, isMajor = true),
                TrackerNetwork(name = "Amazon.com", url = "amazon.com", percentageOfPages = 14, isMajor = true),
                TrackerNetwork(name = "AppNexus", url = "appnexus.com", percentageOfPages = 10, isMajor = true),
                TrackerNetwork(name = "Oracle", url = "oracle.com", percentageOfPages = 10, isMajor = true),
                TrackerNetwork(name = "MediaMath", url = "mediamath.com", percentageOfPages = 9, isMajor = true),
                TrackerNetwork(name = "Yahoo!", url = "yahoo.com", percentageOfPages = 9, isMajor = true),
                TrackerNetwork(name = "StackPath", url = "stackpath.com", percentageOfPages = 7, isMajor = true),
                TrackerNetwork(name = "Automattic", url = "automattic.com", percentageOfPages = 7, isMajor = true)
        )
    }

    private var data: List<DisconnectTracker> = ArrayList()

    fun updateData(trackers: List<DisconnectTracker>) {
        this.data = trackers
    }

    fun network(url: String): TrackerNetwork? {
        val disconnectEntry = data.find { sameOrSubdomain(url, it.url) || sameOrSubdomain(url, it.networkUrl) } ?: return null
        val majorEntry = majorNetwork(disconnectEntry.networkName)
        return TrackerNetwork(
                name = disconnectEntry.networkName,
                url = disconnectEntry.networkUrl,
                category = disconnectEntry.category,
                percentageOfPages = majorEntry?.percentageOfPages,
                isMajor = majorEntry != null)
    }

    private fun majorNetwork(networkName: String): TrackerNetwork? {
        return majorNetworks.find { it.name.equals(networkName, true) }
    }
}

