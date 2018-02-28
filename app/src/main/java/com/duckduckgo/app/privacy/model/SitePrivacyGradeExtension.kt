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

package com.duckduckgo.app.privacy.model

import com.duckduckgo.app.global.model.Site
import timber.log.Timber


val Site.score: Int
    get() {
        var score = baseScore
        score += trackerCount / 10
        if (hasTrackerFromMajorNetwork) {
            score += 1
        }
        if (hasObscureTracker) {
            score += 1
        }
        if (score == 0 && termsOfService.classification != "A") {
            score = 1
        }
        Timber.v("""Calculating score {
            memberMajorNetworkPercentage: ${memberNetwork?.percentageOfPages}
            https: $https
            termsScore: ${termsOfService.gradingScore}
            trackerCount: $trackerCount
            hasTrackerFromMajorNetwork: $hasTrackerFromMajorNetwork
            hasObscureTracker: $hasObscureTracker
            score: $score
            """)
        return score
    }

val Site.potentialScore: Int
    get() {
        var score = baseScore
        if (score == 0 && termsOfService.classification != "A") {
            score = 1
        }
        return score
    }


private val Site.baseScore: Int
    get() {
        var score = 1
        score += Math.ceil((memberNetwork?.percentageOfPages ?: 0) / 10.0).toInt()
        score += termsOfService.gradingScore
        if (https != HttpsStatus.SECURE) {
            score += 1
        }
        return score
    }

val Site.improvedScore: Int
    get() {
        if (allTrackersBlocked) {
            return potentialScore
        }
        return score
    }

val Site.grade: PrivacyGrade
    get() = PrivacyGrade.gradeForScore(score)

val Site.improvedGrade: PrivacyGrade
    get() = PrivacyGrade.gradeForScore(improvedScore)

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
