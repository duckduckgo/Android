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

package com.duckduckgo.privacy.config.impl.referencetests.gpc

import com.duckduckgo.app.FileUtilities
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.privacy.config.api.Gpc
import com.duckduckgo.privacy.config.api.GpcException
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.duckduckgo.privacy.config.impl.features.gpc.GpcFeature
import com.duckduckgo.privacy.config.impl.features.gpc.RealGpc
import com.duckduckgo.privacy.config.impl.features.unprotectedtemporary.RealUnprotectedTemporary
import com.duckduckgo.privacy.config.impl.models.JsonPrivacyConfig
import com.duckduckgo.privacy.config.impl.network.JSONObjectAdapter
import com.duckduckgo.privacy.config.store.GpcExceptionEntity
import com.duckduckgo.privacy.config.store.features.gpc.GpcRepository
import com.duckduckgo.privacy.config.store.features.unprotectedtemporary.UnprotectedTemporaryRepository
import com.duckduckgo.privacy.config.store.toGpcException
import com.duckduckgo.privacy.config.store.toUnprotectedTemporaryException
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

@RunWith(ParameterizedRobolectricTestRunner::class)
class GpcHeaderReferenceTest(private val testCase: TestCase) {

    private val mockUnprotectedTemporaryRepository: UnprotectedTemporaryRepository = mock()
    private val mockGpcRepository: GpcRepository = mock()
    private val mockFeatureToggle: FeatureToggle = mock()
    lateinit var gpc: Gpc

    companion object {
        private val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()
        val adapter: JsonAdapter<ReferenceTest> = moshi.adapter(ReferenceTest::class.java)

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "Test case: {index} - {0}")
        fun testData(): List<TestCase> {
            val referenceTest = adapter.fromJson(
                FileUtilities.loadText(
                    GpcHeaderReferenceTest::class.java.classLoader!!,
                    "reference_tests/gpc/tests.json"
                )
            )
            return referenceTest?.gpcHeader?.tests?.filterNot { it.exceptPlatforms.contains("android-browser") } ?: emptyList()
        }
    }

    @Before
    fun setup() {
        whenever(mockGpcRepository.isGpcEnabled()).thenReturn(testCase.gpcUserSettingOn)
        mockGpcPrivacyConfig()
        gpc = RealGpc(mockFeatureToggle, mockGpcRepository, RealUnprotectedTemporary(mockUnprotectedTemporaryRepository))
    }

    @Test
    fun whenReferenceTestRunsItReturnsTheExpectedResult() {
        val gpcHeader = gpc.getHeaders(testCase.requestURL)[RealGpc.GPC_HEADER]
        val gpcHeaderExists = gpcHeader != null
        assertEquals(testCase.expectGPCHeader, gpcHeaderExists)
        if (gpcHeaderExists) {
            assertEquals(testCase.expectGPCHeaderValue, gpcHeader)
        }
    }

    private fun mockGpcPrivacyConfig() {
        val gpcExceptions = mutableListOf<GpcException>()
        val jsonAdapter: JsonAdapter<JsonPrivacyConfig> = moshi.adapter(JsonPrivacyConfig::class.java)
        val config: JsonPrivacyConfig? = jsonAdapter.fromJson(
            FileUtilities.loadText(
                javaClass.classLoader!!,
                "reference_tests/gpc/config_reference.json"
            )
        )
        val gpcAdapter: JsonAdapter<GpcFeature> = moshi.adapter(GpcFeature::class.java)
        val gpcFeature: GpcFeature? = gpcAdapter.fromJson(config?.features?.get("gpc").toString())

        gpcFeature?.exceptions?.map {
            gpcExceptions.add(GpcExceptionEntity(it.domain).toGpcException())
        }

        val isEnabled = gpcFeature?.state == "enabled"
        val exceptionsUnprotectedTemporary = CopyOnWriteArrayList(
            config?.unprotectedTemporary?.map { it.toUnprotectedTemporaryException() } ?: emptyList()
        )

        whenever(mockFeatureToggle.isFeatureEnabled(PrivacyFeatureName.GpcFeatureName.value, isEnabled)).thenReturn(isEnabled)
        whenever(mockGpcRepository.exceptions).thenReturn(CopyOnWriteArrayList(gpcExceptions))
        whenever(mockUnprotectedTemporaryRepository.exceptions).thenReturn(exceptionsUnprotectedTemporary)
    }

    data class TestCase(
        val name: String,
        val siteURL: String,
        val requestURL: String,
        val requestType: String,
        val gpcUserSettingOn: Boolean,
        val expectGPCHeader: Boolean,
        val expectGPCHeaderValue: String,
        val exceptPlatforms: List<String>
    )

    data class GpcHeaderTest(
        val name: String,
        val desc: String,
        val tests: List<TestCase>
    )

    data class ReferenceTest(
        val gpcHeader: GpcHeaderTest
    )
}
