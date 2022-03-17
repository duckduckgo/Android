/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.privacy.config.impl.features.amplinks

import com.duckduckgo.app.FileUtilities
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.duckduckgo.privacy.config.api.AmpLinks
import com.duckduckgo.privacy.config.api.AmpLinkException
import com.duckduckgo.privacy.config.impl.features.unprotectedtemporary.UnprotectedTemporary
import com.duckduckgo.privacy.config.store.features.amplinks.AmpLinksRepository
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import junit.framework.TestCase.*
import org.json.JSONObject
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.ParameterizedRobolectricTestRunner
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(ParameterizedRobolectricTestRunner::class)
class AmpKeywordReferenceTest(private val testCase: TestCase) {

    lateinit var testee: AmpLinks

    private val mockRepository: AmpLinksRepository = mock()
    private val mockUnprotectedTemporary: UnprotectedTemporary = mock()
    private val mockFeatureToggle: FeatureToggle = mock()

    @Before
    fun setup() {
        mockAmpLinks()
        testee = RealAmpLinks(mockRepository, mockFeatureToggle, mockUnprotectedTemporary)
        whenever(mockUnprotectedTemporary.isAnException(any())).thenReturn(false)
        whenever(mockFeatureToggle.isFeatureEnabled(PrivacyFeatureName.AmpLinksFeatureName(), true)).thenReturn(true)
    }

    companion object {
        private val moshi = Moshi.Builder().build()
        val adapter: JsonAdapter<ReferenceTest> = moshi.adapter(ReferenceTest::class.java)

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "Test case: {index} - {0}")
        fun testData(): List<TestCase> {
            val test = adapter.fromJson(
                FileUtilities.loadText(
                    AmpKeywordReferenceTest::class.java.classLoader!!,
                    "reference_tests/amplinks/amp_links_matching_tests.json"
                )
            )
            return test?.ampKeywords?.tests ?: emptyList()
        }
    }

    @Test
    fun whenReferenceTestRunsItReturnsTheExpectedResult() {
        val extractedUrl = testee.extractCanonicalFromAmpLink(testCase.ampURL)
        if (extractedUrl != null) {
            assertTrue(testCase.expectAmpDetected)
        } else {
            assertFalse(testCase.expectAmpDetected)
        }
    }

    private fun mockAmpLinks() {
        val jsonAdapter: JsonAdapter<AmpLinksFeature> = moshi.adapter(AmpLinksFeature::class.java)
        val exceptions = CopyOnWriteArrayList<AmpLinkException>()
        val ampLinkKeywords = CopyOnWriteArrayList<String>()
        val jsonObject: JSONObject = FileUtilities.getJsonObjectFromFile(
            AmpKeywordReferenceTest::class.java.classLoader!!,
            "reference_tests/amplinks/amp_links_reference.json"
        )

        jsonObject.keys().forEach {
            val ampLinksFeature: AmpLinksFeature? = jsonAdapter.fromJson(jsonObject.get(it).toString())
            exceptions.addAll(ampLinksFeature!!.exceptions)
            ampLinkKeywords.addAll(ampLinksFeature.settings.keywords)
        }
        whenever(mockRepository.exceptions).thenReturn(exceptions)
        whenever(mockRepository.ampLinkFormats).thenReturn(CopyOnWriteArrayList())
        whenever(mockRepository.ampKeywords).thenReturn(ampLinkKeywords)
    }

    data class TestCase(
        val name: String,
        val ampURL: String,
        val expectAmpDetected: Boolean,
        val exceptPlatforms: List<String>
    )

    data class AmpKeywordTest(
        val name: String,
        val desc: String,
        val referenceConfig: String,
        val tests: List<TestCase>
    )

    data class ReferenceTest(
        val ampKeywords: AmpKeywordTest
    )
}
