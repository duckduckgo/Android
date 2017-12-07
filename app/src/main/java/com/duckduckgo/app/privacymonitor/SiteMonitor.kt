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

package com.duckduckgo.app.privacymonitor

import android.net.Uri
import com.duckduckgo.app.global.isHttps
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import java.util.concurrent.CopyOnWriteArrayList

class SiteMonitor constructor(override val url: String) : PrivacyMonitor {

    override var hasHttpResources = false

    private val trackingEvents = CopyOnWriteArrayList<TrackingEvent>()

    override val uri: Uri?
        get() = Uri.parse(url)

    override val https: HttpsStatus
        get() = httpsStatus()

    private fun httpsStatus(): HttpsStatus {

        val uri = uri ?: return HttpsStatus.NONE

        if (uri.isHttps) {
            return if (hasHttpResources) HttpsStatus.MIXED else HttpsStatus.SECURE
        }

        return HttpsStatus.NONE
    }

    override val trackerCount: Int
        get() = trackingEvents.size

    override val networkCount: Int
        get() = trackingEvents
                .mapNotNull { it.trackerNetwork }
                .distinct()
                .count()

    override val majorNetworkCount: Int
        get() = trackingEvents
                .filter { it.trackerNetwork?.isMajor ?: false }
                .mapNotNull { it.trackerNetwork?.name }
                .distinct()
                .count()

    override fun trackerDetected(event: TrackingEvent) {
        trackingEvents.add(event)
    }
}