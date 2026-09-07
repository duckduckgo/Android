/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.sync.impl.exchange

import com.duckduckgo.sync.impl.exchange.ExchangeProtocolVersion.Unsupported
import com.duckduckgo.sync.impl.exchange.ExchangeProtocolVersion.V1
import com.duckduckgo.sync.impl.exchange.ExchangeProtocolVersion.V2
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(TestParameterInjector::class)
class ExchangeProtocolVersionTest {

    enum class ParseSuccessCase(
        val rawVersion: String,
        val expected: ExchangeProtocolVersion,
    ) {
        MajorOnlyV1("1", V1(minor = 0)),
        MajorMinorV1("1.0", V1(minor = 0)),
        HigherMinorV1("1.5", V1(minor = 5)),
        MajorOnlyV2("2", V2(minor = 0)),
        MajorMinorV2("2.1", V2(minor = 1)),
        PatchComponentIgnored("2.1.7", V2(minor = 1)),
        UnknownMajor("3", Unsupported("3")),
        UnknownMajorWithMinor("3.2", Unsupported("3.2")),
        ZeroMajor("0", Unsupported("0")),
        DoubleDigitMajor("10.2", Unsupported("10.2")),
    }

    @Test
    fun `parses valid version strings`(
        @TestParameter case: ParseSuccessCase,
    ) {
        assertEquals(case.expected, ExchangeProtocolVersion.parse(case.rawVersion).getOrThrow())
    }

    @Test
    fun `parsing malformed input returns failure`(
        @TestParameter("", "abc", "1.x", "2..1", ".", "1.", " 1") rawVersion: String,
    ) {
        assertTrue(ExchangeProtocolVersion.parse(rawVersion).isFailure)
    }

    enum class OrderingCase(
        val lower: ExchangeProtocolVersion,
        val higher: ExchangeProtocolVersion,
    ) {
        MinorWithinV1(V1(minor = 0), V1(minor = 1)),
        MinorWithinV2(V2(minor = 0), V2(minor = 1)),
        MajorBeatsMinor(V1(minor = 9), V2(minor = 0)),
        SupportedBelowUnsupported(V2(minor = 9), Unsupported("0")),
        UnsupportedByRawVersion(Unsupported("3"), Unsupported("4")),
    }

    @Test
    fun `orders versions`(
        @TestParameter case: OrderingCase,
    ) {
        assertTrue(case.lower < case.higher)
        assertTrue(case.higher > case.lower)
    }

    enum class EqualityCase(
        val first: ExchangeProtocolVersion,
        val second: ExchangeProtocolVersion,
    ) {
        SameV1(V1(minor = 3), V1(minor = 3)),
        SameV2(V2(minor = 0), V2(minor = 0)),
        SameUnsupported(Unsupported("3.2"), Unsupported("3.2")),
    }

    @Test
    fun `equal versions compare as equal`(@TestParameter case: EqualityCase) {
        assertEquals(0, case.first.compareTo(case.second))
        assertEquals(case.first, case.second)
    }

    enum class ToStringCase(
        val version: ExchangeProtocolVersion,
        val expected: String,
    ) {
        ZeroMinorOmitted(V2(minor = 0), "2"),
        NonZeroMinorIncluded(V2(minor = 1), "2.1"),
        UnsupportedKeepsRawVersion(Unsupported("3.2.1"), "3.2.1"),
    }

    @Test
    fun `renders version string`(
        @TestParameter case: ToStringCase,
    ) {
        assertEquals(case.expected, case.version.toString())
    }

    @Test
    fun `parsing the rendered form of a supported version round-trips`(
        @TestParameter("1", "1.5", "2", "2.1") rawVersion: String,
    ) {
        val parsed = ExchangeProtocolVersion.parse(rawVersion).getOrThrow()
        val reParsed = ExchangeProtocolVersion.parse(parsed.toString()).getOrThrow()

        assertEquals(parsed, reParsed)
    }

    @Test
    fun `parseOrUnsupported returns parsed version for valid input`(
        @TestParameter case: ParseSuccessCase,
    ) {
        assertEquals(case.expected, ExchangeProtocolVersion.parseOrUnsupported(case.rawVersion))
    }

    @Test
    fun `parseOrUnsupported falls back to Unsupported with raw version for malformed input`(
        @TestParameter("", "abc", "1.x", "2..1", ".", "1.", " 1") rawVersion: String,
    ) {
        assertEquals(Unsupported(rawVersion), ExchangeProtocolVersion.parseOrUnsupported(rawVersion))
    }

    @Test
    fun `predefined constants carry expected versions`() {
        assertEquals(V1(minor = 0), ExchangeProtocolVersion.V1_0)
        assertEquals(V2(minor = 0), ExchangeProtocolVersion.V2_0)
        assertEquals(V2(minor = 1), ExchangeProtocolVersion.V2_1)
    }
}
