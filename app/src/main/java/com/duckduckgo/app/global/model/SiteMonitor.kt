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
import com.duckduckgo.app.global.baseHost
import com.duckduckgo.app.global.isHttps
import com.duckduckgo.app.privacy.model.*
import com.duckduckgo.app.privacy.store.PrevalenceStore
import com.duckduckgo.app.trackerdetection.model.TrackerNetwork
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import java.util.concurrent.CopyOnWriteArrayList

class SiteMonitor(
    override val url: String,
    override val privacyPractices: PrivacyPractices.Practices,
    override val memberNetwork: TrackerNetwork? = null,
    val prevalenceStore: PrevalenceStore
) : Site {

    private val gradeCalculator = Grade()

    init {
        gradeCalculator.https = uri?.isHttps ?: false
        gradeCalculator.httpsAutoUpgrade = gradeCalculator.https // not support yet, don't penalise sites for now
        gradeCalculator.privacyScore = privacyPractices.score

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

    override val majorNetworkCount: Int
        get() = trackingEvents.distinctBy { it.trackerNetwork?.name }.count { it.trackerNetwork?.isMajor ?: false }

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
        return when (grade) {
            Grade.Grading.A -> PrivacyGrade.A
            Grade.Grading.B_PLUS -> PrivacyGrade.B_PLUS
            Grade.Grading.B -> PrivacyGrade.B
            Grade.Grading.C_PLUS -> PrivacyGrade.C_PLUS
            Grade.Grading.C -> PrivacyGrade.C
            Grade.Grading.D -> PrivacyGrade.D
            Grade.Grading.D_MINUS -> PrivacyGrade.D
        }
    }

}