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

package com.duckduckgo.adclick.impl.referencetests

import com.duckduckgo.adclick.api.AdClickFeatureName
import com.duckduckgo.adclick.impl.AdClickAttribution
import com.duckduckgo.adclick.impl.AdClickAttributionFeature
import com.duckduckgo.adclick.impl.AdClickAttributionLinkFormat
import com.duckduckgo.adclick.impl.RealAdClickAttribution
import com.duckduckgo.adclick.store.AdClickAttributionDetectionEntity
import com.duckduckgo.adclick.store.AdClickAttributionLinkFormatEntity
import com.duckduckgo.adclick.store.AdClickAttributionRepository
import com.duckduckgo.app.FileUtilities
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import junit.framework.TestCase.assertEquals
import org.json.JSONObject
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.ParameterizedRobolectricTestRunner
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(ParameterizedRobolectricTestRunner::class)
class AdClickAttributionLinkFormatsReferenceTest(private val testCase: TestCase) {

    lateinit var testee: AdClickAttribution

    private val mockRepository: AdClickAttributionRepository = mock()
    private val mockFeatureToggle: FeatureToggle = mock()

    @Before
    fun setup() {
        mockAdClickLinkFormats()
        testee = RealAdClickAttribution(mockRepository, mockFeatureToggle)
        whenever(mockFeatureToggle.isFeatureEnabled(AdClickFeatureName.AdClickAttributionFeatureName.value, true)).thenReturn(true)
        whenever(mockRepository.detections).thenReturn(listOf(AdClickAttributionDetectionEntity(1, "enabled", "enabled")))
    }

    companion object {
        private val moshi = Moshi.Builder().build()
        private val adapter: JsonAdapter<ReferenceTest> = moshi.adapter(ReferenceTest::class.java)

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "Test case: {index} - {0}")
        fun testData(): List<TestCase> {
            val test = adapter.fromJson(
                FileUtilities.loadText(
                    AdClickAttributionLinkFormatsReferenceTest::class.java.classLoader!!,
                    "reference_tests/adclickattribution/ad_click_attribution_matching_tests.json"
                )
            )
            return test?.adClickLinkFormats?.tests ?: emptyList()
        }
    }

    @Test
    fun whenReferenceTestRunsItReturnsTheExpectedResult() {
        val result = testee.isAdClick(testCase.url)
        assertEquals(testCase.isAdClick, result.first)
    }

    private fun mockAdClickLinkFormats() {
        val jsonAdapter: JsonAdapter<AdClickAttributionFeature> = moshi.adapter(AdClickAttributionFeature::class.java)
        val adClickLinkFormats = CopyOnWriteArrayList<AdClickAttributionLinkFormatEntity>()
        val jsonObject: JSONObject = FileUtilities.getJsonObjectFromFile(
            AdClickAttributionLinkFormatsReferenceTest::class.java.classLoader!!,
            "reference_tests/adclickattribution/ad_click_attribution_reference.json"
        )

        val linkFormatList: List<AdClickAttributionLinkFormat>? = jsonAdapter.fromJson(jsonObject.toString())?.settings?.linkFormats
        linkFormatList?.let { list ->
            adClickLinkFormats.addAll(
                list.map {
                    AdClickAttributionLinkFormatEntity(
                        url = it.url,
                        adDomainParameterName = it.adDomainParameterName.orEmpty()
                    )
                }
            )
        }
        whenever(mockRepository.linkFormats).thenReturn(adClickLinkFormats)
    }

    data class TestCase(
        val name: String,
        val url: String,
        val isAdClick: Boolean,
    )

    data class AdClickLinkFormatTest(
        val name: String,
        val desc: String,
        val tests: List<TestCase>
    )

    data class ReferenceTest(
        val adClickLinkFormats: AdClickLinkFormatTest
    )
}
