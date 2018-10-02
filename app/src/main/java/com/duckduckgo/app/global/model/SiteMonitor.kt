/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.global.model

import android.net.Uri
import com.duckduckgo.app.global.UriString.Companion.host
import com.duckduckgo.app.global.baseHost
import com.duckduckgo.app.global.hasIpHost
import com.duckduckgo.app.global.isHttps
import com.duckduckgo.app.privacy.model.Grade
import com.duckduckgo.app.privacy.model.HttpsStatus
import com.duckduckgo.app.privacy.model.PrivacyGrade
import com.duckduckgo.app.privacy.model.TermsOfService
import com.duckduckgo.app.privacy.store.PrevalenceStore
import com.duckduckgo.app.trackerdetection.model.TrackerNetwork
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import java.util.concurrent.CopyOnWriteArrayList

class SiteMonitor(
    override val url: String,
    override val termsOfService: TermsOfService,
    override val memberNetwork: TrackerNetwork? = null,
    val prevalenceStore: PrevalenceStore
) : Site {

    private val gradeCalculator = Grade()

    init {
        gradeCalculator.https = uri?.isHttps ?: false
        gradeCalculator.httpsAutoUpgrade = gradeCalculator.https // not support yet, don't penalise sites for now
        gradeCalculator.privacyScore = termsOfService.score // TODO

        memberNetwork?.let {
            gradeCalculator.setParentEntityAndPrevalence(it.name, prevalenceStore.findPrevalenceOf(it.name))
        }
    }

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
        get() = trackingEvents.distinctBy { it.trackerNetwork?.url }.count { it.trackerNetwork?.isMajor ?: false }

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

        val entity = event.entity
        val prevalence = prevalenceStore.findPrevalenceOf(entity)
        if (event.blocked) {
            gradeCalculator.addEntityBlocked(entity, prevalence)
        } else {
            gradeCalculator.addEntityNotBlocked(entity, prevalence)
        }
    }

    override val grade: PrivacyGrade
        get() = privacyGrade(gradeCalculator.scores.site.grade)

    override val improvedGrade: PrivacyGrade
        get() = privacyGrade(gradeCalculator.scores.enhanced.grade)

    private fun privacyGrade(grade: Grade.Grading): PrivacyGrade {
        // TODO privacy grade needs to support B+ and C+
        when(grade) {
            Grade.Grading.A -> return PrivacyGrade.A
            Grade.Grading.B_PLUS -> return PrivacyGrade.B
            Grade.Grading.B -> return PrivacyGrade.B
            Grade.Grading.C_PLUS -> return PrivacyGrade.C
            Grade.Grading.C -> return PrivacyGrade.C
            Grade.Grading.D -> return PrivacyGrade.D
            Grade.Grading.D_MINUS -> return PrivacyGrade.D
        }
    }

}