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

import com.duckduckgo.app.privacy.model.PrivacyPractices.Summary.*
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class TermsOfServiceTest(val testCase: TermsOfServiceTestCase) {

    @Test
    fun test() {

        testCase.expectedScore?.let {
            assertEquals(it, testCase.terms.derivedScore)
        }

        testCase.expectedPractices?.let {
            assertEquals(it, testCase.terms.practices)
        }

    }

    companion object {

        @JvmStatic
        @Parameterized.Parameters
        fun data(): Array<TermsOfServiceTestCase> {
            return arrayOf(
                // scores

                TermsOfServiceTestCase(expectedScore = 0, terms = TermsOfService(classification = "A", score = 0)),
                TermsOfServiceTestCase(expectedScore = 1, terms = TermsOfService(classification = "B", score = 0)),
                TermsOfServiceTestCase(expectedScore = 5, terms = TermsOfService(classification = null, score = 0)),
                TermsOfServiceTestCase(expectedScore = 7, terms = TermsOfService(classification = "C", score = 0)),
                TermsOfServiceTestCase(expectedScore = 7, terms = TermsOfService(classification = null, score = 101)),
                TermsOfServiceTestCase(expectedScore = 10, terms = TermsOfService(classification = "D", score = 0)),
                TermsOfServiceTestCase(expectedScore = 10, terms = TermsOfService(classification = null, score = 151)),

                // practices

                // score and reasons are ignored
                TermsOfServiceTestCase(expectedPractices = GOOD, terms = TermsOfService(classification = "A", score = 0)),
                TermsOfServiceTestCase(expectedPractices = MIXED, terms = TermsOfService(classification = "B", score = 0)),
                TermsOfServiceTestCase(expectedPractices = POOR, terms = TermsOfService(classification = "C", score = 0)),
                TermsOfServiceTestCase(expectedPractices = POOR, terms = TermsOfService(classification = "D", score = 0)),

                TermsOfServiceTestCase(expectedPractices = GOOD, terms = TermsOfService(classification = "A", score = 1)),
                TermsOfServiceTestCase(expectedPractices = MIXED, terms = TermsOfService(classification = "B", score = -1)),
                TermsOfServiceTestCase(expectedPractices = POOR, terms = TermsOfService(classification = "C", score = 0, goodPrivacyTerms = listOf("good"))),
                TermsOfServiceTestCase(expectedPractices = POOR, terms = TermsOfService(classification = "D", score = 0, badPrivacyTerms = listOf("bad"))),


                // class and score are ignored
                TermsOfServiceTestCase(expectedPractices = MIXED, terms = TermsOfService(score = 0, goodPrivacyTerms = listOf("good"), badPrivacyTerms = listOf("bad"))),

                // class and reasons are ignored
                TermsOfServiceTestCase(expectedPractices = GOOD, terms = TermsOfService(score = -1)),
                TermsOfServiceTestCase(expectedPractices = GOOD, terms = TermsOfService(score = -10)),
                TermsOfServiceTestCase(expectedPractices = GOOD, terms = TermsOfService(score = -100)),
                TermsOfServiceTestCase(expectedPractices = GOOD, terms = TermsOfService(score = -1000)),

                // class is ignored, must be at least one reason of either kind
                TermsOfServiceTestCase(expectedPractices = MIXED, terms = TermsOfService(score = 0, goodPrivacyTerms = listOf("good"))),
                TermsOfServiceTestCase(expectedPractices = MIXED, terms = TermsOfService(score = 0, badPrivacyTerms = listOf("bad"))),

                // class and reasons are ignored
                TermsOfServiceTestCase(expectedPractices = POOR, terms = TermsOfService(score = 1)),
                TermsOfServiceTestCase(expectedPractices = POOR, terms = TermsOfService(score = 10)),
                TermsOfServiceTestCase(expectedPractices = POOR, terms = TermsOfService(score = 100)),
                TermsOfServiceTestCase(expectedPractices = POOR, terms = TermsOfService(score = 1000))

            )
        }

    }

}

data class TermsOfServiceTestCase(val expectedPractices: PrivacyPractices.Summary? = null, val expectedScore: Int? = null, val terms: TermsOfService)