/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.privacy.config.impl.features.trackingparameters

import com.duckduckgo.app.FileUtilities
import com.duckduckgo.app.userwhitelist.api.UserWhiteListRepository
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.duckduckgo.privacy.config.api.TrackingParameterException
import com.duckduckgo.privacy.config.api.TrackingParameters
import com.duckduckgo.privacy.config.impl.features.unprotectedtemporary.UnprotectedTemporary
import com.duckduckgo.privacy.config.store.features.trackingparameters.TrackingParametersRepository
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
class TrackingParameterReferenceTest(private val testCase: TestCase) {

    lateinit var testee: TrackingParameters

    private val mockRepository: TrackingParametersRepository = mock()
    private val mockUnprotectedTemporary: UnprotectedTemporary = mock()
    private val mockFeatureToggle: FeatureToggle = mock()
    private val mockUserWhiteListRepository: UserWhiteListRepository = mock()

    @Before
    fun setup() {
        mockTrackingParameters()
        testee = RealTrackingParameters(mockRepository, mockFeatureToggle, mockUnprotectedTemporary, mockUserWhiteListRepository)
        whenever(mockUnprotectedTemporary.isAnException(any())).thenReturn(false)
        whenever(mockFeatureToggle.isFeatureEnabled(PrivacyFeatureName.TrackingParametersFeatureName, true)).thenReturn(true)
    }

    companion object {
        private val moshi = Moshi.Builder().build()
        val adapter: JsonAdapter<ReferenceTest> = moshi.adapter(ReferenceTest::class.java)

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "Test case: {index} - {0}")
        fun testData(): List<TestCase> {
            val test = adapter.fromJson(
                FileUtilities.loadText(
                    TrackingParameterReferenceTest::class.java.classLoader!!,
                    "reference_tests/trackingparameters/tracking_parameters_matching_tests.json"
                )
            )
            return test?.trackingParameters?.tests ?: emptyList()
        }
    }

    @Test
    fun whenReferenceTestRunsItReturnsTheExpectedResult() {
        val cleanedUrl = testee.cleanTrackingParameters(testCase.testURL)
        if (cleanedUrl != null) {
            assertEquals(testCase.expectURL, cleanedUrl)
        } else {
            assertEquals(testCase.expectURL, testCase.testURL)
        }
    }

    private fun mockTrackingParameters() {
        val jsonAdapter: JsonAdapter<TrackingParametersFeature> = moshi.adapter(TrackingParametersFeature::class.java)
        val exceptions = CopyOnWriteArrayList<TrackingParameterException>()
        val trackingParameters = CopyOnWriteArrayList<Regex>()
        val jsonObject: JSONObject = FileUtilities.getJsonObjectFromFile(
            TrackingParameterReferenceTest::class.java.classLoader!!,
            "reference_tests/trackingparameters/tracking_parameters_reference.json"
        )

        jsonObject.keys().forEach { key ->
            val trackingParametersFeature: TrackingParametersFeature? = jsonAdapter.fromJson(jsonObject.get(key).toString())
            exceptions.addAll(trackingParametersFeature!!.exceptions)
            trackingParameters.addAll(trackingParametersFeature.settings.parameters.map { it.toRegex(RegexOption.IGNORE_CASE) })
        }
        whenever(mockRepository.exceptions).thenReturn(exceptions)
        whenever(mockRepository.parameters).thenReturn(trackingParameters)
    }

    data class TestCase(
        val name: String,
        val testURL: String,
        val expectURL: String,
        val exceptPlatforms: List<String>
    )

    data class TrackingParameterTest(
        val name: String,
        val desc: String,
        val referenceConfig: String,
        val tests: List<TestCase>
    )

    data class ReferenceTest(
        val trackingParameters: TrackingParameterTest
    )
}
