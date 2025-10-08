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

import com.duckduckgo.adclick.impl.AdClickAttribution
import com.duckduckgo.adclick.impl.RealAdClickAttribution
import com.duckduckgo.adclick.impl.remoteconfig.AdClickAttributionAllowlist
import com.duckduckgo.adclick.impl.remoteconfig.AdClickAttributionFeature
import com.duckduckgo.adclick.impl.remoteconfig.AdClickAttributionFeatureModel
import com.duckduckgo.adclick.impl.remoteconfig.AdClickAttributionRepository
import com.duckduckgo.adclick.impl.store.AdClickAttributionAllowlistEntity
import com.duckduckgo.adclick.impl.store.AdClickAttributionDetectionEntity
import com.duckduckgo.common.test.FileUtilities
import com.duckduckgo.feature.toggles.api.Toggle
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
class AdClickAttributionAllowlistReferenceTest(private val testCase: TestCase) {

    lateinit var testee: AdClickAttribution

    private val mockRepository: AdClickAttributionRepository = mock()
    private val mockAdClickAttributionFeature: AdClickAttributionFeature = mock()
    private val mockToggle: Toggle = mock()

    @Before
    fun setup() {
        whenever(mockAdClickAttributionFeature.self()).thenReturn(mockToggle)
        whenever(mockAdClickAttributionFeature.self().isEnabled()).thenReturn(true)
        whenever(mockRepository.detections).thenReturn(listOf(AdClickAttributionDetectionEntity(1, "enabled", "enabled")))
        mockAdClickLinkFormats()
        testee = RealAdClickAttribution(mockRepository, mockAdClickAttributionFeature)
    }

    companion object {
        private val moshi = Moshi.Builder().build()
        private val adapter: JsonAdapter<ReferenceTest> = moshi.adapter(ReferenceTest::class.java)

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "Test case: {index} - {0}")
        fun testData(): List<TestCase> {
            val test = adapter.fromJson(
                FileUtilities.loadText(
                    AdClickAttributionAllowlistReferenceTest::class.java.classLoader!!,
                    "reference_tests/adclickattribution/ad_click_attribution_matching_tests.json",
                ),
            )
            return test?.adClickAllowlist?.tests ?: emptyList()
        }
    }

    @Test
    fun whenReferenceTestRunsItReturnsTheExpectedResult() {
        val result = testee.isAllowed(testCase.url)
        assertEquals(testCase.isAllowed, result)
    }

    private fun mockAdClickLinkFormats() {
        val jsonAdapter: JsonAdapter<AdClickAttributionFeatureModel> = moshi.adapter(AdClickAttributionFeatureModel::class.java)
        val adClickAllowlist = CopyOnWriteArrayList<AdClickAttributionAllowlistEntity>()
        val jsonObject: JSONObject = FileUtilities.getJsonObjectFromFile(
            AdClickAttributionAllowlistReferenceTest::class.java.classLoader!!,
            "reference_tests/adclickattribution/ad_click_attribution_reference.json",
        )

        val allowList: List<AdClickAttributionAllowlist>? = jsonAdapter.fromJson(jsonObject.toString())?.allowlist
        allowList?.let { list ->
            adClickAllowlist.addAll(
                list.map {
                    AdClickAttributionAllowlistEntity(blocklistEntry = it.blocklistEntry.orEmpty(), host = it.host.orEmpty())
                },
            )
        }
        whenever(mockRepository.allowList).thenReturn(adClickAllowlist)
    }

    data class TestCase(
        val name: String,
        val url: String,
        val isAllowed: Boolean,
    )

    data class AdClickAllowlistTest(
        val name: String,
        val desc: String,
        val tests: List<TestCase>,
    )

    data class ReferenceTest(
        val adClickAllowlist: AdClickAllowlistTest,
    )
}
