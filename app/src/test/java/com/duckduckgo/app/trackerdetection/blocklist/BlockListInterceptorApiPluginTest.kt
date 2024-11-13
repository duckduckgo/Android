/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.trackerdetection.blocklist

import android.annotation.SuppressLint
import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.app.trackerdetection.api.TDS_BASE_URL
import com.duckduckgo.app.trackerdetection.api.TDS_PATH
import com.duckduckgo.app.trackerdetection.blocklist.BlockList.Cohorts.CONTROL
import com.duckduckgo.app.trackerdetection.blocklist.BlockList.Cohorts.TREATMENT
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.api.FakeChain
import com.duckduckgo.feature.toggles.api.FakeToggleStore
import com.duckduckgo.feature.toggles.api.FeatureToggles
import com.duckduckgo.feature.toggles.api.FeatureTogglesInventory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultValue
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.feature.toggles.impl.RealFeatureTogglesInventory
import com.squareup.moshi.Moshi
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@SuppressLint("DenyListedApi")
class BlockListInterceptorApiPluginTest {

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private lateinit var testBlockListFeature: TestBlockListFeature
    private lateinit var inventory: FeatureTogglesInventory
    private lateinit var interceptor: BlockListInterceptorApiPlugin
    private val moshi = Moshi.Builder().build()

    private data class Config(
        val treatmentUrl: String? = null,
        val controlUrl: String? = null,
        val nextUrl: String? = null,
    )
    private val configAdapter = moshi.adapter(Config::class.java)

    @Before
    fun setup() {
        testBlockListFeature = FeatureToggles.Builder(
            FakeToggleStore(),
            featureName = "blockList",
        ).build().create(TestBlockListFeature::class.java)

        inventory = RealFeatureTogglesInventory(
            setOf(
                FakeFeatureTogglesInventory(
                    features = listOf(
                        testBlockListFeature.tdsNextExperimentTest(),
                        testBlockListFeature.tdsNextExperimentAnotherTest(),
                    ),
                ),
            ),
            coroutineRule.testDispatcherProvider,
        )

        interceptor = BlockListInterceptorApiPlugin(inventory, moshi)
    }

    @Test
    fun `when multiple experiments enabled, use the first one`() {
        testBlockListFeature.tdsNextExperimentTest().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                settings = configAdapter.toJson(
                    Config(treatmentUrl = "treatmentUrl", controlUrl = "controlUrl"),
                ),
                cohorts = listOf(
                    State.Cohort(name = CONTROL.cohortName, weight = 0),
                    State.Cohort(name = TREATMENT.cohortName, weight = 1),
                ),
            ),
        )
        testBlockListFeature.tdsNextExperimentAnotherTest().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                settings = configAdapter.toJson(
                    Config(treatmentUrl = "anotherTreatmentUrl", controlUrl = "anotherControlUrl"),
                ),
                cohorts = listOf(
                    State.Cohort(name = CONTROL.cohortName, weight = 0),
                    State.Cohort(name = TREATMENT.cohortName, weight = 1),
                ),
            ),
        )
        val url = getUrl(TDS_PATH)
        val result = interceptor.intercept(FakeChain(url))

        assertEquals(getUrl("treatmentUrl"), result.request.url.toString())
    }

    @Test
    fun `when cohort is treatment use treatment URL`() {
        testBlockListFeature.tdsNextExperimentAnotherTest().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                settings = configAdapter.toJson(
                    Config(treatmentUrl = "anotherTreatmentUrl", controlUrl = "anotherControlUrl"),
                ),
                cohorts = listOf(
                    State.Cohort(name = CONTROL.cohortName, weight = 0),
                    State.Cohort(name = TREATMENT.cohortName, weight = 1),
                ),
            ),
        )
        val url = getUrl(TDS_PATH)
        val result = interceptor.intercept(FakeChain(url))

        assertEquals(getUrl("anotherTreatmentUrl"), result.request.url.toString())
    }

    @Test
    fun `when cohort is control use control URL`() {
        testBlockListFeature.tdsNextExperimentAnotherTest().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                settings = configAdapter.toJson(
                    Config(treatmentUrl = "anotherTreatmentUrl", controlUrl = "anotherControlUrl"),
                ),
                cohorts = listOf(
                    State.Cohort(name = CONTROL.cohortName, weight = 1),
                    State.Cohort(name = TREATMENT.cohortName, weight = 0),
                ),
            ),
        )
        val url = getUrl(TDS_PATH)
        val result = interceptor.intercept(FakeChain(url))

        assertEquals(getUrl("anotherControlUrl"), result.request.url.toString())
    }

    @Test
    fun `when feature is for next URL rollout then use next url`() {
        testBlockListFeature.tdsNextExperimentAnotherTest().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                settings = configAdapter.toJson(
                    Config(nextUrl = "nextUrl"),
                ),
            ),
        )
        val url = getUrl(TDS_PATH)
        val result = interceptor.intercept(FakeChain(url))

        assertEquals(getUrl("nextUrl"), result.request.url.toString())
    }

    @Test
    fun `when no experiments enabled, use default path`() {
        val url = getUrl(TDS_PATH)
        val result = interceptor.intercept(FakeChain(url))
        assertEquals(getUrl(TDS_PATH), result.request.url.toString())
    }

    @Test
    fun `when feature name doesn't match prefix, it is ignored`() {
        testBlockListFeature.nonMatchingFeatureName().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                settings = configAdapter.toJson(
                    Config(treatmentUrl = "anotherTreatmentUrl", controlUrl = "anotherControlUrl"),
                ),
                cohorts = listOf(
                    State.Cohort(name = CONTROL.cohortName, weight = 1),
                    State.Cohort(name = TREATMENT.cohortName, weight = 0),
                ),
            ),
        )
        val url = getUrl(TDS_PATH)
        val result = interceptor.intercept(FakeChain(url))

        assertEquals(getUrl(TDS_PATH), result.request.url.toString())
    }

    private fun getUrl(path: String): String = "$TDS_BASE_URL$path"
}

class FakeFeatureTogglesInventory(private val features: List<Toggle>) : FeatureTogglesInventory {
    override suspend fun getAll(): List<Toggle> {
        return features
    }
}

abstract class TriggerTestScope private constructor()

@ContributesRemoteFeature(
    scope = TriggerTestScope::class,
    featureName = "blockList",
)
interface TestBlockListFeature {
    @DefaultValue(false)
    fun self(): Toggle

    @Toggle.DefaultValue(false)
    fun tdsNextExperimentTest(): Toggle

    @Toggle.DefaultValue(false)
    fun tdsNextExperimentAnotherTest(): Toggle

    @Toggle.DefaultValue(false)
    fun nonMatchingFeatureName(): Toggle
}
