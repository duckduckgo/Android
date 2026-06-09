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

package com.duckduckgo.privacy.config.impl.referencetests.trackerallowlist

import com.duckduckgo.common.test.FileUtilities
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.duckduckgo.privacy.config.impl.features.trackerallowlist.OptimizeTrackerAllowListRCWrapper
import com.duckduckgo.privacy.config.impl.features.trackerallowlist.RealTrackerAllowlist
import com.duckduckgo.privacy.config.store.TrackerAllowlistEntity
import com.duckduckgo.privacy.config.store.features.trackerallowlist.TrackerAllowlistRepository
import com.duckduckgo.privacy.config.store.features.trackerallowlist.buildRulesByDomain
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.ParameterizedRobolectricTestRunner
import java.lang.reflect.ParameterizedType
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(ParameterizedRobolectricTestRunner::class)
class TrackerAllowlistReferenceTest(private val testCase: TestCase) {

    private val mockTrackerAllowlistRepository: TrackerAllowlistRepository = mock()
    private val mockFeatureToggle: FeatureToggle = mock()
    private val mockPrecompileRegexWrapper: OptimizeTrackerAllowListRCWrapper = mock()

    companion object {
        private val moshi = Moshi.Builder().build()
        val type: ParameterizedType =
            Types.newParameterizedType(List::class.java, TestCase::class.java)
        val adapter: JsonAdapter<List<TestCase>> = moshi.adapter(type)

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "Test case: {index} - {0}")
        fun testData(): List<TestCase> {
            return adapter.fromJson(
                FileUtilities.loadText(
                    TrackerAllowlistReferenceTest::class.java.classLoader!!,
                    "reference_tests/trackerallowlist/tracker_allowlist_matching_tests.json",
                ),
            )
                ?: emptyList()
        }
    }

    @Test
    fun whenIsAnExceptionAnFeatureEnableThenReturnCorrectValues() {
        whenever(
            mockFeatureToggle.isFeatureEnabled(
                PrivacyFeatureName.TrackerAllowlistFeatureName.value,
                true,
            ),
        )
            .thenReturn(true)
        whenever(mockPrecompileRegexWrapper.enabled).thenReturn(false)
        mockAllowlist()

        val testee = RealTrackerAllowlist(mockTrackerAllowlistRepository, mockFeatureToggle, mockPrecompileRegexWrapper)

        assertEquals(testCase.isAllowlisted, testee.isAnException(testCase.site, testCase.request))
    }

    @Test
    fun whenIsAnExceptionAnFeatureEnableAndPrecompileEnabledThenReturnCorrectValues() {
        whenever(
            mockFeatureToggle.isFeatureEnabled(
                PrivacyFeatureName.TrackerAllowlistFeatureName.value,
                true,
            ),
        )
            .thenReturn(true)
        whenever(mockPrecompileRegexWrapper.enabled).thenReturn(true)
        mockAllowlist()

        val testee = RealTrackerAllowlist(mockTrackerAllowlistRepository, mockFeatureToggle, mockPrecompileRegexWrapper)

        assertEquals(testCase.isAllowlisted, testee.isAnException(testCase.site, testCase.request))
    }

    @Test
    fun whenIsAnExceptionAnFeatureDisabledThenReturnCorrectValues() {
        whenever(
            mockFeatureToggle.isFeatureEnabled(
                PrivacyFeatureName.TrackerAllowlistFeatureName.value,
                true,
            ),
        )
            .thenReturn(false)
        whenever(mockPrecompileRegexWrapper.enabled).thenReturn(false)
        mockAllowlist()

        val testee = RealTrackerAllowlist(mockTrackerAllowlistRepository, mockFeatureToggle, mockPrecompileRegexWrapper)

        assertEquals(false, testee.isAnException(testCase.site, testCase.request))
    }

    private fun mockAllowlist() {
        val jsonAdapter: JsonAdapter<TrackerAllowlistEntity> =
            moshi.adapter(TrackerAllowlistEntity::class.java)
        val exceptions = mutableListOf<TrackerAllowlistEntity>()
        val jsonObject: JSONObject =
            FileUtilities.getJsonObjectFromFile(
                javaClass.classLoader!!,
                "reference_tests/trackerallowlist/tracker_allowlist_reference.json",
            )

        jsonObject.keys().forEach {
            val allowlistEntity = jsonAdapter.fromJson(jsonObject.get(it).toString())
            exceptions.add(allowlistEntity!!.copy(domain = it))
        }
        whenever(mockTrackerAllowlistRepository.exceptions).thenReturn(CopyOnWriteArrayList(exceptions))
        whenever(mockTrackerAllowlistRepository.rulesByDomain).thenReturn(buildRulesByDomain(exceptions))
    }

    data class TestCase(
        val description: String,
        val site: String,
        val request: String,
        val isAllowlisted: Boolean,
    )
}
