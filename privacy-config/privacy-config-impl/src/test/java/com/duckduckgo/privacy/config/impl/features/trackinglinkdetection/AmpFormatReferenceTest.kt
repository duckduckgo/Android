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

package com.duckduckgo.privacy.config.impl.features.trackinglinkdetection

import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.duckduckgo.privacy.config.api.TrackingLinkDetector
import com.duckduckgo.privacy.config.api.TrackingLinkException
import com.duckduckgo.privacy.config.impl.FileUtilities
import com.duckduckgo.privacy.config.impl.features.unprotectedtemporary.UnprotectedTemporary
import com.duckduckgo.privacy.config.store.features.trackinglinkdetection.TrackingLinkDetectionRepository
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import junit.framework.TestCase.assertEquals
import org.json.JSONObject
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(ParameterizedRobolectricTestRunner::class)
class AmpFormatReferenceTest(private val testCase: TestCase) {

    lateinit var testee: TrackingLinkDetector

    private val mockRepository: TrackingLinkDetectionRepository = mock()
    private val mockUnprotectedTemporary: UnprotectedTemporary = mock()
    private val mockFeatureToggle: FeatureToggle = mock()

    @Before
    fun setup() {
        mockAmpLinks()
        testee = RealTrackingLinkDetector(mockRepository, mockFeatureToggle, mockUnprotectedTemporary)
        whenever(mockUnprotectedTemporary.isAnException(any())).thenReturn(false)
        whenever(mockFeatureToggle.isFeatureEnabled(PrivacyFeatureName.TrackingLinkDetectionFeatureName(), true)).thenReturn(true)
    }

    companion object {
        private val moshi = Moshi.Builder().build()
        private val adapter: JsonAdapter<AmpFormatTest> = moshi.adapter(AmpFormatTest::class.java)

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "Test case: {index} - {0}")
        fun testData(): List<TestCase> {
            var ampFormatTest: AmpFormatTest? = null
            val jsonObject: JSONObject = FileUtilities.getJsonObjectFromFile("reference_tests/tracking_link_detection_matching_tests.json")

            jsonObject.keys().forEach {
                if (it == "ampFormats") {
                    ampFormatTest = adapter.fromJson(jsonObject.get(it).toString())
                }
            }
            return ampFormatTest?.tests ?: emptyList()
        }
    }

    @Test
    fun whenSomething() {
        val extractedUrl = testee.extractCanonicalFromTrackingLink(testCase.ampUrl)
        assertEquals(testCase.expectURL, extractedUrl)
    }

    private fun mockAmpLinks() {
        val jsonAdapter: JsonAdapter<TrackingLinkDetectionFeature> = moshi.adapter(TrackingLinkDetectionFeature::class.java)
        val exceptions = CopyOnWriteArrayList<TrackingLinkException>()
        val ampLinkFormats = CopyOnWriteArrayList<Regex>()
        val jsonObject: JSONObject = FileUtilities.getJsonObjectFromFile("reference_tests/tracking_link_detection_reference.json")

        jsonObject.keys().forEach {
            val trackingLinkDetectionFeature: TrackingLinkDetectionFeature? = jsonAdapter.fromJson(jsonObject.get(it).toString())
            exceptions.addAll(trackingLinkDetectionFeature!!.exceptions)
            ampLinkFormats.addAll(trackingLinkDetectionFeature.settings.linkFormats.map { it.toRegex(RegexOption.IGNORE_CASE) })
        }
        whenever(mockRepository.exceptions).thenReturn(exceptions)
        whenever(mockRepository.ampLinkFormats).thenReturn(ampLinkFormats)
    }

    data class TestCase(
        val name: String,
        val ampUrl: String,
        val expectURL: String,
        val exceptPlatforms: List<String>
    )

    data class AmpFormatTest(
        val name: String,
        val desc: String,
        val referenceConfig: String,
        val tests: List<TestCase>
    )
}
