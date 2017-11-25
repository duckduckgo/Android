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

package com.duckduckgo.app.sitemonitor

import com.duckduckgo.app.trackerdetection.model.NetworkTrackers
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import java.io.Serializable

class SiteMonitor constructor(private val networkTrackers: NetworkTrackers) : Serializable {

    var url: String? = null

    private var trackingEvents: MutableList<TrackingEvent> = ArrayList()

    val trackerNetworkCount: Int
        get() = trackingEvents
                .map { networkTrackers.network(it.trackerUrl) }
                .filterNotNull()
                .distinct()
                .count()

    val trackerCount: Int
        get() = trackingEvents.size

    fun trackerDetected(event: TrackingEvent) {
        trackingEvents.add(event)
    }

}