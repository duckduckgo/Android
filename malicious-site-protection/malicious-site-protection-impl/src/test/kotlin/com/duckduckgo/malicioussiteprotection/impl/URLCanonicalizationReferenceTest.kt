/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.malicioussiteprotection.impl

import androidx.core.net.toUri
import com.duckduckgo.common.test.FileUtilities
import com.duckduckgo.malicioussiteprotection.impl.domain.RealUrlCanonicalization
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import org.robolectric.ParameterizedRobolectricTestRunner

@RunWith(ParameterizedRobolectricTestRunner::class)
class URLCanonicalizationReferenceTest(private val testCase: TestCase) {

    companion object {
        private val moshi = Moshi.Builder().build()
        private val adapter: JsonAdapter<ReferenceTest> = moshi.adapter(ReferenceTest::class.java)
        private val mockMaliciousSiteProtectionRCFeature: MaliciousSiteProtectionRCFeature = mock()

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "Test case: {index} - {0}")
        fun testData(): List<TestCase> {
            val test = adapter.fromJson(
                FileUtilities.loadText(
                    URLCanonicalizationReferenceTest::class.java.classLoader!!,
                    "reference_tests/canonicalization/canonicalization_tests.json",
                ),
            )
            return test?.urlTests?.tests?.filterNot { it.exceptPlatforms.contains("android-browser") } ?: emptyList()
        }
    }

    private lateinit var testee: RealUrlCanonicalization

    @get:org.junit.Rule
    var coroutineRule = com.duckduckgo.common.test.CoroutineTestRule()

    @Before
    fun setup() {
        whenever(mockMaliciousSiteProtectionRCFeature.stripWWWPrefix()).thenReturn(true)
        testee = RealUrlCanonicalization(mockMaliciousSiteProtectionRCFeature)
    }

    @Test
    fun whenReferenceTestRunsItReturnsTheExpectedResult() = runTest {
        assertEquals(testCase.expectURL, testee.canonicalizeUrl(testCase.siteURL.toUri()).toString())
    }

    data class TestCase(
        val name: String,
        val siteURL: String,
        val expectURL: String,
        val exceptPlatforms: List<String>,
    )

    data class UrlCanonicalizationTest(
        val name: String,
        val desc: String,
        val tests: List<TestCase>,
    )

    data class ReferenceTest(
        val urlTests: UrlCanonicalizationTest,
    )
}
