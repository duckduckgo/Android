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
import com.duckduckgo.app.privacy.model.PredefinedGradeDataJsonConverter.GradeTestCase
import com.duckduckgo.app.privacy.model.PredefinedGradeDataJsonConverter.JsonGradeTestCase
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import timber.log.Timber

@RunWith(Parameterized::class)
class PredefinedGradeAvailableTest(private val testCase: GradeTestCase) {

    @Test
    fun predefinedGradeTests() {

        val grade = Grade(testCase.input.https, testCase.input.httpsAutoUpgrade)
        grade.updateData(testCase.input.privacyScore, testCase.input.parentEntity)

        for (tracker in testCase.input.trackers) {
            if (tracker.blocked) {
                grade.addEntityBlocked(tracker.parentEntity)
            } else {
                grade.addEntityNotBlocked(tracker.parentEntity)
            }
        }

        Timber.d("testCase ${testCase.url}")
        val actualScore = (grade.calculateScore() as Grade.Scores.ScoresAvailable)
        assertEquals(testCase.url, testCase.expected.site.score, actualScore.site.score)
    }

    companion object {

        @JvmStatic
        @Parameterized.Parameters
        fun data(): Array<GradeTestCase> {
            val json = FileUtilities.loadText(PredefinedGradeAvailableTest::class.java.classLoader!!, "privacy-grade/test/data/grade-cases.json")
            val moshi = Moshi.Builder().build()
            val jsonAdapter = moshi.adapter<Array<JsonGradeTestCase>>(Array<JsonGradeTestCase>::class.java)
            return jsonAdapter.fromJson(json)!!
                .map { it.toGradeTestCase() }
                .toTypedArray()
        }
    }
}
