/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.referral

import com.duckduckgo.app.referral.ParsedReferrerResult.ReferrerFound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryParamReferrerParserTest {

    private lateinit var testee: QueryParamReferrerParser

    @Test
    fun whenReferrerDoesNotContainTargetThenNoReferrerFound() {
        testee = QueryParamReferrerParser("target")
        verifyReferrerNotFound(testee.parse("ABC"))
    }

    @Test
    fun whenReferrerContainsTargetAndLongSuffixThenShortenedReferrerFound() {
        testee = QueryParamReferrerParser("target")
        val result = testee.parse("targetABC")
        verifyReferrerFound("AB", result)
    }

    @Test
    fun whenReferrerContainsTargetAndTwoCharSuffixThenReferrerFound() {
        testee = QueryParamReferrerParser("target")
        val result = testee.parse("targetXY")
        verifyReferrerFound("XY", result)
    }

    @Test
    fun whenReferrerContainsTargetAndOneCharSuffixThenNoReferrerFound() {
        testee = QueryParamReferrerParser("target")
        val result = testee.parse("targetX")
        verifyReferrerNotFound(result)
    }

    @Test
    fun whenReferrerContainsTargetButNoSuffixThenNoReferrerFound() {
        testee = QueryParamReferrerParser("target")
        val result = testee.parse("targetX")
        verifyReferrerNotFound(result)
    }

    @Test
    fun whenReferrerIsEmptyThenNoReferrerFound() {
        testee = QueryParamReferrerParser("target")
        verifyReferrerNotFound(testee.parse(""))
    }

    @Test
    fun whenReferrerContainsTargetAsFirstParamThenReferrerFound() {
        testee = QueryParamReferrerParser("target")
        val result = testee.parse("key1=targetAB&key2=foo&key3=bar")
        verifyReferrerFound("AB", result)
    }

    @Test
    fun whenReferrerContainsTargetAsLastParamThenReferrerFound() {
        testee = QueryParamReferrerParser("target")
        val result = testee.parse("key1=foo&key2=bar&key3=targetAB")
        verifyReferrerFound("AB", result)
    }

    @Test
    fun whenReferrerContainsTargetWithDifferentCaseThenNoReferrerFound() {
        testee = QueryParamReferrerParser("target")
        verifyReferrerNotFound(testee.parse("TARGETAB"))
    }

    private fun verifyReferrerFound(expectedReferrer: String, result: ParsedReferrerResult) {
        assertTrue(result is ReferrerFound)
        val value = (result as ReferrerFound).campaignSuffix
        assertEquals(expectedReferrer, value)
    }

    private fun verifyReferrerNotFound(result: ParsedReferrerResult) {
        assertTrue(result is ParsedReferrerResult.ReferrerNotFound)
    }
}