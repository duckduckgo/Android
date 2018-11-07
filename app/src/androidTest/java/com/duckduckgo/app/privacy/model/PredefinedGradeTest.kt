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

import com.duckduckgo.app.FileUtilities
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import timber.log.Timber

@RunWith(Parameterized::class)
class PredefinedGradeTest(private val testCase: GradeTestCase) {

    @Test
    fun predefinedGradeTests() {
        val grade = Grade()

        grade.https = testCase.input.https
        grade.httpsAutoUpgrade = testCase.input.httpsAutoUpgrade
        grade.privacyScore = testCase.input.privacyScore

        grade.setParentEntityAndPrevalence(testCase.input.parentEntity, testCase.input.parentTrackerPrevalence)

        for (tracker in testCase.input.trackers) {

            if (tracker.blocked) {
                grade.addEntityBlocked(tracker.parentEntity, tracker.prevalence)
            } else {
                grade.addEntityNotBlocked(tracker.parentEntity, tracker.prevalence)
            }

        }

        Timber.d("testCase ${testCase.url}")
        assertEquals(testCase.url, testCase.expected.site, grade.scores.site)
    }

    companion object {

        @JvmStatic
        @Parameterized.Parameters
        fun data(): Array<GradeTestCase> {
            val json = FileUtilities.loadText("privacy-grade/test/data/grade-cases.json")
            val moshi = Moshi.Builder().build()
            val jsonAdapter = moshi.adapter<Array<GradeTestCase>>(Array<GradeTestCase>::class.java)
            return jsonAdapter.fromJson(json)
        }

    }

}

class GradeTestCase(val expected: Grade.Scores, val input: Input, val url: String) {

    class Input(
        val https: Boolean,
        val httpsAutoUpgrade: Boolean,
        val parentEntity: String?,
        val parentTrackerPrevalence: Double?,
        val privacyScore: Int?,
        val trackers: Array<Tracker>
    )

    class Tracker(val blocked: Boolean, val parentEntity: String, val prevalence: Double?)

}
