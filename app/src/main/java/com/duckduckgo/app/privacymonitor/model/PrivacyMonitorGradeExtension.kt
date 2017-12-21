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

package com.duckduckgo.app.privacymonitor.model

import com.duckduckgo.app.privacymonitor.HttpsStatus
import com.duckduckgo.app.privacymonitor.PrivacyMonitor


val PrivacyMonitor.score: Int
    get() {
        var score = baseScore
        score += Math.ceil(trackerCount / 10.0).toInt()
        if (majorNetworkCount > 0) {
            score += 1
        }
        if (hasObscureTracker) {
            score += 1
        }
        return score
    }

val PrivacyMonitor.potentialScore: Int
    get() = baseScore


private val PrivacyMonitor.baseScore: Int
    get() {
        var score = 1
        score += Math.ceil((memberNetwork?.percentageOfPages ?: 0) / 10.0).toInt()
        score += termsOfService.gradingScore
        if (https != HttpsStatus.SECURE) {
            score += 1
        }
        return score
    }

val PrivacyMonitor.improvedScore: Int
    get() {
        if (allTrackersBlocked) {
            return potentialScore
        }
        return score
    }

@PrivacyGrade.Companion.Grade
val PrivacyMonitor.grade: Long
    get() = calculateGrade(score)

@PrivacyGrade.Companion.Grade
val PrivacyMonitor.improvedGrade: Long
    get() = calculateGrade(improvedScore)


@PrivacyGrade.Companion.Grade
private fun calculateGrade(score: Int): Long {
    return when {
        score <= 0 -> PrivacyGrade.A
        score == 1 -> PrivacyGrade.B
        score == 2 -> PrivacyGrade.C
        else -> PrivacyGrade.D
    }
}

val TermsOfService.gradingScore: Int
    get() {
        when (classification) {
            "A" -> return -1
            "B" -> return 0
            "C" -> return 0
            "D" -> return 1
            "E" -> return 2
        }
        when {
            score < 0 -> return -1
            score > 0 -> return 1
        }
        return 0
    }
