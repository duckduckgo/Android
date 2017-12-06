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
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class NetworkTrackers @Inject constructor() {

    data class MajorNetwork(val name: String,
                            val domain: String,
                            val percentageOfPahges: Int)

    companion object {
        private var majorNetworks = arrayOf(
                MajorNetwork("google", "google.com", 84),
                MajorNetwork("facebook", "facebook.com", 36),
                MajorNetwork("twitter", "twitter.com", 16),
                MajorNetwork("amazon.com", "amazon.com", 14),
                MajorNetwork("appnexus", "appnexus.com", 10),
                MajorNetwork("oracle", "oracle.com", 10),
                MajorNetwork("mediamath", "mediamath.com", 9),
                MajorNetwork("yahoo!", "yahoo.com", 9),
                MajorNetwork("maxcdn", "maxcdn.com", 7),
                MajorNetwork("automattic", "automattic.com", 7)
        )
    }

    private var trackers: List<DisconnectTracker> = ArrayList()

    fun updateData(trackers: List<DisconnectTracker>) {
        this.trackers = trackers
    }

    fun network(url: String): String? {
        val network = trackers.find { sameOrSubdomain(url, it.url) || sameOrSubdomain(url, it.networkUrl) }
        return network?.networkName
    }

    fun majorNetwork(url: String): String? {
        val majorNetwork = majorNetworks.find { network(url).equals(it.name, true) }
        return majorNetwork?.name
    }

}

