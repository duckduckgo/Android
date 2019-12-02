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
import com.duckduckgo.app.statistics.pixels.Pixel
import com.nhaarman.mockitokotlin2.mock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryParamReferrerParserTest {

    private val pixel: Pixel = mock()

    private val testee: QueryParamReferrerParser = QueryParamReferrerParser(pixel)

    @Test
    fun whenReferrerDoesNotContainTargetThenNoReferrerFound() {
        verifyReferrerNotFound(testee.parse("ABC"))
    }

    @Test
    fun whenReferrerContainsTargetAndLongSuffixThenShortenedReferrerFound() {
        val result = testee.parse("DDGRAABC")
        verifyReferrerFound("AB", result)
    }

    @Test
    fun whenReferrerContainsTargetAndTwoCharSuffixThenReferrerFound() {
        val result = testee.parse("DDGRAXY")
        verifyReferrerFound("XY", result)
    }

    @Test
    fun whenReferrerContainsTargetAndOneCharSuffixThenNoReferrerFound() {
        val result = testee.parse("DDGRAX")
        verifyReferrerNotFound(result)
    }

    @Test
    fun whenReferrerContainsTargetButNoSuffixThenNoReferrerFound() {
        val result = testee.parse("DDGRAX")
        verifyReferrerNotFound(result)
    }

    @Test
    fun whenReferrerIsEmptyThenNoReferrerFound() {
        verifyReferrerNotFound(testee.parse(""))
    }

    @Test
    fun whenReferrerContainsTargetAsFirstParamThenReferrerFound() {
        val result = testee.parse("key1=DDGRAAB&key2=foo&key3=bar")
        verifyReferrerFound("AB", result)
    }

    @Test
    fun whenReferrerContainsTargetAsLastParamThenReferrerFound() {
        val result = testee.parse("key1=foo&key2=bar&key3=DDGRAAB")
        verifyReferrerFound("AB", result)
    }

    @Test
    fun whenReferrerContainsTargetWithDifferentCaseThenNoReferrerFound() {
        verifyReferrerNotFound(testee.parse("ddgraAB"))
    }

    @Test
    fun whenTypeAReferrerNotFoundButTypeBFoundThenReferrerFound() {
        verifyReferrerFound("AB", testee.parse("key1=foo&key2=bar&key3=DDGRBAB"))
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