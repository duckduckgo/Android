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

import com.duckduckgo.app.privacymonitor.model.TermsOfService.Practices
import org.junit.Assert.assertEquals
import org.junit.Test

class TermsOfServiceTest {

    companion object {
        const val badTerm = "badTerm"
        const val goodTerm = "goodTerm"
    }

    @Test
    fun whenNoDataThenPracticesUnknown() {
        val testee = TermsOfService()
        assertEquals(Practices.UNKNOWN, testee.practices)
    }

    @Test
    fun whenClassificationIsAAndReasonsExistThenPracticesAreGood() {
        val testee = TermsOfService(classification = "A", goodPrivacyTerms = listOf(goodTerm), badPrivacyTerms = listOf(badTerm))
        assertEquals(Practices.GOOD, testee.practices)
    }

    @Test
    fun whenClassificationIsBAndReasonsExistThenPracticesAreMixed() {
        val testee = TermsOfService(classification = "B", goodPrivacyTerms = listOf(goodTerm), badPrivacyTerms = listOf(badTerm))
        assertEquals(Practices.MIXED, testee.practices)
    }

    @Test
    fun whenClassificationIsCAndReasonsExistThenPracticesArePoor() {
        val testee = TermsOfService(classification = "C", goodPrivacyTerms = listOf(goodTerm), badPrivacyTerms = listOf(badTerm))
        assertEquals(Practices.POOR, testee.practices)
    }

    @Test
    fun whenClassificationIsDAndReasonsExistThenPracticesArePoor() {
        val testee = TermsOfService(classification = "D", goodPrivacyTerms = listOf(goodTerm), badPrivacyTerms = listOf(badTerm))
        assertEquals(Practices.POOR, testee.practices)
    }

    @Test
    fun whenClassificationIsEAndReasonsExistThenPracticesArePoor() {
        val testee = TermsOfService(classification = "E", goodPrivacyTerms = listOf(goodTerm), badPrivacyTerms = listOf(badTerm))
        assertEquals(Practices.POOR, testee.practices)
    }

    @Test
    fun whenNoClassificationWithGoodAndBadReasonsThenPracticesAreMixed() {
        val testee = TermsOfService(goodPrivacyTerms = listOf(goodTerm), badPrivacyTerms = listOf(badTerm))
        assertEquals(Practices.MIXED, testee.practices)
    }

    @Test
    fun whenGoodTermsAndScoreLessThanZeroThenPracticesAreGood() {
        val testee = TermsOfService(score = -10, goodPrivacyTerms = listOf(goodTerm))
        assertEquals(Practices.GOOD, testee.practices)
    }

    @Test
    fun whenBadTermsAndScoreLessThanZeroThenPracticesAreGood() {
        val testee = TermsOfService(score = -10, badPrivacyTerms = listOf(badTerm))
        assertEquals(Practices.GOOD, testee.practices)
    }

    @Test
    fun whenGoodTermsAndScoreOfZeroThenPracticesAreMixed() {
        val testee = TermsOfService(score = 0, goodPrivacyTerms = listOf(goodTerm))
        assertEquals(Practices.MIXED, testee.practices)
    }

    @Test
    fun whenBadTermsAndScoreOfZeroThenPracticesAreMixed() {
        val testee = TermsOfService(score = 0, badPrivacyTerms = listOf(badTerm))
        assertEquals(Practices.MIXED, testee.practices)
    }

    @Test
    fun whenGoodTermsAndScoreGreaterThanZeroThenPracticesArePoor() {
        val testee = TermsOfService(score = 10, goodPrivacyTerms = listOf(goodTerm))
        assertEquals(Practices.POOR, testee.practices)
    }

    @Test
    fun whenBadTermsAndScoreGreaterThanZeroThenPracticesArePoor() {
        val testee = TermsOfService(score = 10, badPrivacyTerms = listOf(badTerm))
        assertEquals(Practices.POOR, testee.practices)
    }
}