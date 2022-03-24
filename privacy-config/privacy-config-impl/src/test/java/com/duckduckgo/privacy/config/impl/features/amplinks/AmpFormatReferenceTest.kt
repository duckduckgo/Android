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
import com.duckduckgo.privacy.config.api.AmpLinkType
import com.duckduckgo.privacy.config.impl.features.unprotectedtemporary.UnprotectedTemporary
import com.duckduckgo.privacy.config.store.features.amplinks.AmpLinksRepository
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import junit.framework.TestCase.assertEquals
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
class AmpFormatReferenceTest(private val testCase: TestCase) {

    lateinit var testee: AmpLinks

    private val mockRepository: AmpLinksRepository = mock()
    private val mockUnprotectedTemporary: UnprotectedTemporary = mock()
    private val mockFeatureToggle: FeatureToggle = mock()

    @Before
    fun setup() {
        mockAmpLinks()
        testee = RealAmpLinks(mockRepository, mockFeatureToggle, mockUnprotectedTemporary)
        whenever(mockUnprotectedTemporary.isAnException(any())).thenReturn(false)
        whenever(mockFeatureToggle.isFeatureEnabled(PrivacyFeatureName.AmpLinksFeatureName, true)).thenReturn(true)
    }

    companion object {
        private val moshi = Moshi.Builder().build()
        val adapter: JsonAdapter<ReferenceTest> = moshi.adapter(ReferenceTest::class.java)

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "Test case: {index} - {0}")
        fun testData(): List<TestCase> {
            val test = adapter.fromJson(
                FileUtilities.loadText(
                    AmpFormatReferenceTest::class.java.classLoader!!,
                    "reference_tests/amplinks/amp_links_matching_tests.json"
                )
            )
            return test?.ampFormats?.tests ?: emptyList()
        }
    }

    @Test
    fun whenReferenceTestRunsItReturnsTheExpectedResult() {
        testCase.exceptPlatforms
        val extractedUrl = testee.extractCanonicalFromAmpLink(testCase.ampURL)
        if (extractedUrl != null) {
            assertEquals(testCase.expectURL, (extractedUrl as AmpLinkType.ExtractedAmpLink).extractedUrl)
        } else {
            assertEquals(testCase.expectURL, "")
        }
    }

    private fun mockAmpLinks() {
        val jsonAdapter: JsonAdapter<AmpLinksFeature> = moshi.adapter(AmpLinksFeature::class.java)
        val exceptions = CopyOnWriteArrayList<AmpLinkException>()
        val ampLinkFormats = CopyOnWriteArrayList<Regex>()
        val jsonObject: JSONObject = FileUtilities.getJsonObjectFromFile(
            AmpFormatReferenceTest::class.java.classLoader!!,
            "reference_tests/amplinks/amp_links_reference.json"
        )

        jsonObject.keys().forEach { key ->
            val ampLinksFeature: AmpLinksFeature? = jsonAdapter.fromJson(jsonObject.get(key).toString())
            exceptions.addAll(ampLinksFeature!!.exceptions)
            ampLinkFormats.addAll(ampLinksFeature.settings.linkFormats.map { it.toRegex(RegexOption.IGNORE_CASE) })
        }
        whenever(mockRepository.exceptions).thenReturn(exceptions)
        whenever(mockRepository.ampLinkFormats).thenReturn(ampLinkFormats)
    }

    data class TestCase(
        val name: String,
        val ampURL: String,
        val expectURL: String,
        val exceptPlatforms: List<String>
    )

    data class AmpFormatTest(
        val name: String,
        val desc: String,
        val referenceConfig: String,
        val tests: List<TestCase>
    )

    data class ReferenceTest(
        val ampFormats: AmpFormatTest
    )
}
