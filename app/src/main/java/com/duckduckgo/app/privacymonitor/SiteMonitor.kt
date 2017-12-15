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
import com.duckduckgo.app.global.hasIpHost
import com.duckduckgo.app.global.isHttps
import com.duckduckgo.app.privacymonitor.model.TermsOfService
import com.duckduckgo.app.trackerdetection.model.TrackerNetwork
import com.duckduckgo.app.trackerdetection.model.TrackerNetworks
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import java.util.concurrent.CopyOnWriteArrayList

class SiteMonitor(override val url: String,
                  override val termsOfService: TermsOfService,
                  private val trackerNetworks: TrackerNetworks) : PrivacyMonitor {

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

    override val memberNetwork: TrackerNetwork?
        get() = trackerNetworks.network(url)

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

    override val hasObscureTracker: Boolean
        get() = trackingEvents.any { Uri.parse(it.trackerUrl).hasIpHost }


    override val allTrackersBlocked: Boolean
        get() = trackingEvents.none { !it.blocked }

    override fun trackerDetected(event: TrackingEvent) {
        trackingEvents.add(event)
    }
}