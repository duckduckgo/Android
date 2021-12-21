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
import androidx.core.net.toUri
import com.duckduckgo.app.global.isHttps
import com.duckduckgo.app.global.model.Site.SiteGrades
import com.duckduckgo.app.global.model.SiteFactory.SitePrivacyData
import com.duckduckgo.app.privacy.model.Grade
import com.duckduckgo.app.privacy.model.HttpsStatus
import com.duckduckgo.app.privacy.model.PrivacyGrade
import com.duckduckgo.app.privacy.model.PrivacyPractices
import com.duckduckgo.app.surrogates.SurrogateResponse
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import java.util.concurrent.CopyOnWriteArrayList

class SiteMonitor(
    url: String,
    override var title: String?,
    override var upgradedHttps: Boolean = false
) : Site {

    override var url: String = url
        set(value) {
            field = value
            _uri = field.toUri()
        }

    /** A read-only uri version of the url. To update, update the url */
    override val uri: Uri?
        get() = _uri

    /** Backing field to ensure uri cannot be publicly written */
    private var _uri: Uri? = url.toUri()

    override val https: HttpsStatus
        get() = httpsStatus()

    override var hasHttpResources = false

    override var privacyPractices: PrivacyPractices.Practices = PrivacyPractices.UNKNOWN

    override var entity: Entity? = null

    override val trackingEvents = CopyOnWriteArrayList<TrackingEvent>()

    override val surrogates = CopyOnWriteArrayList<SurrogateResponse>()

    override val trackerCount: Int
        get() = trackingEvents.size

    override val majorNetworkCount: Int
        get() = trackingEvents.distinctBy { it.entity?.name }.count { it.entity?.isMajor ?: false }

    override val allTrackersBlocked: Boolean
        get() = trackingEvents.none { !it.blocked }

    private val gradeCalculator: Grade

    init {
        val isHttps = https != HttpsStatus.NONE

        // httpsAutoUpgrade is not supported yet; for now, keep it equal to isHttps and don't
        // penalise sites
        gradeCalculator = Grade(https = isHttps, httpsAutoUpgrade = isHttps)
    }

    override fun updatePrivacyData(sitePrivacyData: SitePrivacyData) {
        this.privacyPractices = sitePrivacyData.practices
        this.entity = sitePrivacyData.entity
        gradeCalculator.updateData(privacyPractices.score, entity)
    }

    private fun httpsStatus(): HttpsStatus {

        val uri = uri ?: return HttpsStatus.NONE

        if (uri.isHttps) {
            return if (hasHttpResources) HttpsStatus.MIXED else HttpsStatus.SECURE
        }

        return HttpsStatus.NONE
    }

    override fun surrogateDetected(surrogate: SurrogateResponse) {
        surrogates.add(surrogate)
    }

    override fun trackerDetected(event: TrackingEvent) {
        trackingEvents.add(event)

        val entity = event.entity ?: return
        if (event.blocked) {
            gradeCalculator.addEntityBlocked(entity)
        } else {
            gradeCalculator.addEntityNotBlocked(entity)
        }
    }

    override fun calculateGrades(): SiteGrades {
        val scores = gradeCalculator.calculateScore()
        val privacyGradeOriginal = privacyGrade(scores)
        val privacyGradeImproved = privacyGradeImproved(scores)
        return SiteGrades(privacyGradeOriginal, privacyGradeImproved)
    }

    private fun privacyGrade(scores: Grade.Scores): PrivacyGrade {
        return when (scores) {
            Grade.Scores.ScoresUnavailable -> PrivacyGrade.UNKNOWN
            is Grade.Scores.ScoresAvailable -> privacyGrade(scores.site.grade)
        }
    }

    private fun privacyGradeImproved(scores: Grade.Scores): PrivacyGrade {
        return when (scores) {
            Grade.Scores.ScoresUnavailable -> PrivacyGrade.UNKNOWN
            is Grade.Scores.ScoresAvailable -> privacyGrade(scores.enhanced.grade)
        }
    }

    private fun privacyGrade(grade: Grade.Grading): PrivacyGrade {
        return when (grade) {
            Grade.Grading.A -> PrivacyGrade.A
            Grade.Grading.B_PLUS -> PrivacyGrade.B_PLUS
            Grade.Grading.B -> PrivacyGrade.B
            Grade.Grading.C_PLUS -> PrivacyGrade.C_PLUS
            Grade.Grading.C -> PrivacyGrade.C
            Grade.Grading.D -> PrivacyGrade.D
            Grade.Grading.D_MINUS -> PrivacyGrade.D
            Grade.Grading.UNKNOWN -> PrivacyGrade.UNKNOWN
        }
    }
}
