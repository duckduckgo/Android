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

import com.duckduckgo.app.privacy.model.PrivacyPractices.Summary.*
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class TermsOfServicePracticesTest(private val testCase: TermsOfServicePracticesTestCase) {

    @Test
    fun test() {
        assertEquals(testCase.expectedPractices, testCase.terms.practices)
    }

    companion object {

        @JvmStatic
        @Parameterized.Parameters
        fun data(): Array<TermsOfServicePracticesTestCase> {
            return arrayOf(
                // score and reasons are ignored
                TermsOfServicePracticesTestCase(
                    GOOD, TermsOfService(classification = "A", score = 0)),
                TermsOfServicePracticesTestCase(
                    MIXED, TermsOfService(classification = "B", score = 0)),
                TermsOfServicePracticesTestCase(
                    POOR, TermsOfService(classification = "C", score = 0)),
                TermsOfServicePracticesTestCase(
                    POOR, TermsOfService(classification = "D", score = 0)),
                TermsOfServicePracticesTestCase(
                    GOOD, TermsOfService(classification = "A", score = 1)),
                TermsOfServicePracticesTestCase(
                    MIXED, TermsOfService(classification = "B", score = -1)),
                TermsOfServicePracticesTestCase(
                    POOR,
                    TermsOfService(
                        classification = "C", score = 0, goodPrivacyTerms = listOf("good"))),
                TermsOfServicePracticesTestCase(
                    POOR,
                    TermsOfService(
                        classification = "D", score = 0, badPrivacyTerms = listOf("bad"))),

                // class and score are ignored
                TermsOfServicePracticesTestCase(
                    MIXED,
                    TermsOfService(
                        score = 0,
                        goodPrivacyTerms = listOf("good"),
                        badPrivacyTerms = listOf("bad"))),

                // class and reasons are ignored
                TermsOfServicePracticesTestCase(GOOD, TermsOfService(score = -1)),
                TermsOfServicePracticesTestCase(GOOD, TermsOfService(score = -10)),
                TermsOfServicePracticesTestCase(GOOD, TermsOfService(score = -100)),
                TermsOfServicePracticesTestCase(GOOD, TermsOfService(score = -1000)),

                // class is ignored, must be at least one reason of either kind
                TermsOfServicePracticesTestCase(
                    MIXED, TermsOfService(score = 0, goodPrivacyTerms = listOf("good"))),
                TermsOfServicePracticesTestCase(
                    MIXED, TermsOfService(score = 0, badPrivacyTerms = listOf("bad"))),

                // class and reasons are ignored
                TermsOfServicePracticesTestCase(POOR, TermsOfService(score = 1)),
                TermsOfServicePracticesTestCase(POOR, TermsOfService(score = 10)),
                TermsOfServicePracticesTestCase(POOR, TermsOfService(score = 100)),
                TermsOfServicePracticesTestCase(POOR, TermsOfService(score = 1000)))
        }
    }
}

data class TermsOfServicePracticesTestCase(
    val expectedPractices: PrivacyPractices.Summary,
    val terms: TermsOfService
)
