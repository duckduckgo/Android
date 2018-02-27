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
import com.duckduckgo.app.global.baseHost
import com.duckduckgo.app.global.hasIpHost
import com.duckduckgo.app.global.isHttps
import com.duckduckgo.app.privacymonitor.model.HttpsStatus
import com.duckduckgo.app.privacymonitor.model.TermsOfService
import com.duckduckgo.app.trackerdetection.model.TrackerNetwork
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import java.util.concurrent.CopyOnWriteArrayList

class SiteMonitor(override val url: String,
                  override val termsOfService: TermsOfService,
                  override val memberNetwork: TrackerNetwork? = null) : PrivacyMonitor {

    override val uri: Uri?
        get() = Uri.parse(url)

    override var title: String? = null

    override val https: HttpsStatus
        get() = httpsStatus()

    override var hasHttpResources = false

    override val trackingEvents = CopyOnWriteArrayList<TrackingEvent>()

    override val trackerCount: Int
        get() = trackingEvents.size

    override val distinctTrackersByNetwork: Map<String, List<TrackingEvent>>
        get() {
            val networks = HashMap<String, MutableList<TrackingEvent>>().toMutableMap()
            for (event: TrackingEvent in trackingEvents.distinctBy { Uri.parse(it.trackerUrl).baseHost }) {
                val network = event.trackerNetwork?.name ?: Uri.parse(event.trackerUrl).baseHost ?: event.trackerUrl
                val events = networks[network] ?: ArrayList()
                events.add(event)
                networks[network] = events
            }
            return networks
        }

    override val networkCount: Int
        get() = distinctTrackersByNetwork.count()

    override val majorNetworkCount: Int
        get() = trackingEvents.distinctBy { it.trackerNetwork?.url }.count { it.trackerNetwork?.isMajor ?: false}

    override val hasTrackerFromMajorNetwork: Boolean
        get() = trackingEvents.any { it.trackerNetwork?.isMajor ?: false }

    override val hasObscureTracker: Boolean
        get() = trackingEvents.any { Uri.parse(it.trackerUrl).hasIpHost }


    override val allTrackersBlocked: Boolean
        get() = trackingEvents.none { !it.blocked }

    private fun httpsStatus(): HttpsStatus {

        val uri = uri ?: return HttpsStatus.NONE

        if (uri.isHttps) {
            return if (hasHttpResources) HttpsStatus.MIXED else HttpsStatus.SECURE
        }

        return HttpsStatus.NONE
    }

    override fun trackerDetected(event: TrackingEvent) {
        trackingEvents.add(event)
    }
}