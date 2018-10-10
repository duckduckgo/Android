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

package com.duckduckgo.app.privacy.model

import com.duckduckgo.app.privacy.model.Grade.Grading.*
import com.squareup.moshi.Json

class Grade() {

    enum class Grading {

        A,
        @Json(name = "B+")
        B_PLUS,
        B,
        @Json(name = "C+")
        C_PLUS,
        C,
        D,
        @Json(name = "D-")
        D_MINUS

    }


    data class Score(
        val grade: Grading,
        val score: Int,
        val httpsScore: Int,
        val trackerScore: Int,
        val privacyScore: Int
    ) {
    }

    data class Scores(
        val site: Score,
        val enhanced: Score
    ) {
    }

    var https: Boolean = false
    var httpsAutoUpgrade: Boolean = false
    var privacyScore: Int? = null

    val scores: Grade.Scores get() = calculate()

    private var entitiesNotBlocked: MutableMap<String, Double> = mutableMapOf()
    private var entitiesBlocked: MutableMap<String, Double> = mutableMapOf()

    private fun calculate(): Grade.Scores {

        // HTTPS
        var siteHttpsScore = 0
        var enhancedHttpsScore = 0

        if (httpsAutoUpgrade) {
            siteHttpsScore = 0
            enhancedHttpsScore = 0
        } else if (https) {
            siteHttpsScore = 3
            enhancedHttpsScore = 0
        } else {
            siteHttpsScore = 10
            enhancedHttpsScore = 10
        }

        // PRIVACY
        val privacyScore = Math.min(privacyScore ?: unknownPrivacyScore, maxPrivacyScore)

        // TRACKERS
        val enhancedTrackerScore = trackerScore(entitiesNotBlocked)
        val siteTrackerScore = trackerScore(entitiesBlocked) + enhancedTrackerScore

        // TOTALS
        val siteTotalScore = siteHttpsScore + siteTrackerScore + privacyScore
        val enhancedTotalScore = enhancedHttpsScore + enhancedTrackerScore + privacyScore

        // GRADES
        val siteGrade = gradeForScore(siteTotalScore)
        val enhancedGrade = gradeForScore(enhancedTotalScore)

        val site = Score(
            grade = siteGrade,
            httpsScore = siteHttpsScore,
            privacyScore = privacyScore,
            score = siteTotalScore,
            trackerScore = siteTrackerScore
        )

        val enhanced = Score(
            grade = enhancedGrade,
            httpsScore = enhancedHttpsScore,
            privacyScore = privacyScore,
            score = enhancedTotalScore,
            trackerScore = enhancedTrackerScore
        )

        return Scores(site = site, enhanced = enhanced)
    }

    private fun gradeForScore(score: Int): Grading {
        when (score) {
            in Int.MIN_VALUE..1 -> return A
            in 2..3 -> return B_PLUS
            in 4..9 -> return B
            in 10..13 -> return C_PLUS
            in 14..19 -> return C
            in 20..29 -> return D
        }

        return D_MINUS
    }

    private fun trackerScore(entities: Map<String, Double>): Int {
        return entities.entries.fold(0) { acc, entry ->
            acc + scoreFromPrevalence(entry.value)
        }
    }

    private fun scoreFromPrevalence(prevalence: Double): Int {

        if (prevalence <= 0.0) {
            return 0
        }

        when (prevalence) {

            in 0.0..0.1 -> return 1
            in 0.1..1.0 -> return 2
            in 1.0..5.0 -> return 3
            in 5.0..10.0 -> return 4
            in 10.0..15.0 -> return 5
            in 15.0..20.0 -> return 6
            in 20.0..30.0 -> return 7
            in 30.0..45.0 -> return 8
            in 45.0..66.0 -> return 9

        }

        return 10
    }

    fun setParentEntityAndPrevalence(parentEntity: String?, prevalence: Double?) {
        parentEntity ?: return
        addEntityNotBlocked(parentEntity, prevalence)
    }

    fun addEntityNotBlocked(entity: String, prevalence: Double?) {
        prevalence ?: return
        entitiesNotBlocked[entity] = prevalence
    }

    fun addEntityBlocked(entity: String, prevalence: Double?) {
        prevalence ?: return
        entitiesBlocked[entity] = prevalence
    }

    companion object {

        val unknownPrivacyScore = 2
        val maxPrivacyScore = 10

    }

}
