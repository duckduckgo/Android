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

package com.duckduckgo.mobile.android.vpn.blocklist

import android.annotation.SuppressLint
import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.mobile.android.vpn.blocklist.BlockList.Cohorts.CONTROL
import com.duckduckgo.mobile.android.vpn.blocklist.BlockList.Cohorts.TREATMENT
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.api.FakeChain
import com.duckduckgo.common.test.api.InMemorySharedPreferences
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.DaggerSet
import com.duckduckgo.feature.toggles.api.FakeToggleStore
import com.duckduckgo.feature.toggles.api.FeatureToggles
import com.duckduckgo.feature.toggles.api.FeatureTogglesInventory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultValue
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixelNames
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.pixels.RealDeviceShieldPixels
import com.squareup.moshi.Moshi
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.eq
import javax.inject.Inject
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever


// TODO: currently copied from feature-toggles-impl, but might be a better way to share this (2025-04-02)
class RealFeatureTogglesInventory @Inject constructor(
    private val toggles: DaggerSet<FeatureTogglesInventory>,
    private val dispatcherProvider: DispatcherProvider,
) : FeatureTogglesInventory {
    override suspend fun getAll(): List<Toggle> = withContext(dispatcherProvider.io()) {
        return@withContext toggles.flatMap { it.getAll() }.distinctBy { it.featureName() }
    }

    override suspend fun getAllTogglesForParent(name: String): List<Toggle> = withContext(dispatcherProvider.io()) {
        return@withContext getAll().filter { it.featureName().parentName == name }
    }

    override suspend fun getAllActiveExperimentToggles(): List<Toggle> = withContext(dispatcherProvider.io()) {
        return@withContext getAll().filter { it.getCohort() != null && it.isEnabled() }
    }
}

@SuppressLint("DenyListedApi")
class AppTPBlockListInterceptorApiPluginTest {

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private lateinit var testBlockListFeature: TestBlockListFeature
    private lateinit var inventory: FeatureTogglesInventory
    private lateinit var interceptor: AppTPBlockListInterceptorApiPlugin
    private val moshi = Moshi.Builder().build()

    private data class Config(
        val treatmentUrl: String? = null,
        val controlUrl: String? = null,
        val nextUrl: String? = null,
    )
    private val configAdapter = moshi.adapter(Config::class.java)

    private val pixel = mock<Pixel>()
    private val sharedPreferencesProvider = mock<SharedPreferencesProvider>()
    private val prefs = InMemorySharedPreferences()

    private lateinit var deviceShieldPixels: DeviceShieldPixels

    @Before
    fun setup() {
        testBlockListFeature = FeatureToggles.Builder(
            FakeToggleStore(),
            featureName = "appTrackerProtection",
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

        whenever(
            sharedPreferencesProvider.getSharedPreferences(eq("com.duckduckgo.mobile.android.device.shield.pixels"), eq(true), eq(true)),
        ).thenReturn(prefs)

        deviceShieldPixels = RealDeviceShieldPixels(pixel, sharedPreferencesProvider)

        interceptor = AppTPBlockListInterceptorApiPlugin(inventory, moshi, deviceShieldPixels)
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
        val annotatedMethod = FakeApiService::class.java.getMethod("endpointRequiringTds")

        val url = getUrl(APPTP_TDS_PATH)
        val result = interceptor.intercept(FakeChain(url = url, serviceMethod = annotatedMethod))

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
        val annotatedMethod = FakeApiService::class.java.getMethod("endpointRequiringTds")
        val url = getUrl(APPTP_TDS_PATH)
        val result = interceptor.intercept(FakeChain(url = url, serviceMethod = annotatedMethod))

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
        val annotatedMethod = FakeApiService::class.java.getMethod("endpointRequiringTds")
        val url = getUrl(APPTP_TDS_PATH)
        val result = interceptor.intercept(FakeChain(url = url, serviceMethod = annotatedMethod))

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
        val annotatedMethod = FakeApiService::class.java.getMethod("endpointRequiringTds")
        val url = getUrl(APPTP_TDS_PATH)
        val result = interceptor.intercept(FakeChain(url = url, serviceMethod = annotatedMethod))

        assertEquals(getUrl("nextUrl"), result.request.url.toString())
    }

    @Test
    fun `when no experiments enabled, use default path`() {
        val annotatedMethod = FakeApiService::class.java.getMethod("endpointRequiringTds")
        val url = getUrl(APPTP_TDS_PATH)
        val result = interceptor.intercept(FakeChain(url = url, serviceMethod = annotatedMethod))
        assertEquals(getUrl(APPTP_TDS_PATH), result.request.url.toString())
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
        val annotatedMethod = FakeApiService::class.java.getMethod("endpointRequiringTds")
        val url = getUrl(APPTP_TDS_PATH)
        val result = interceptor.intercept(FakeChain(url = url, serviceMethod = annotatedMethod))

        assertEquals(getUrl(APPTP_TDS_PATH), result.request.url.toString())
    }

    @Test
    fun `when annotation not present, even if experiment present, ignore and proceed as normal`() {
        testBlockListFeature.tdsNextExperimentAnotherTest().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                settings = configAdapter.toJson(
                    Config(nextUrl = "nextUrl"),
                ),
            ),
        )
        val nonAnnotatedMethod = FakeApiService::class.java.getMethod("endpointNotRequiringTds")
        val url = getUrl("test.json")
        val result = interceptor.intercept(FakeChain(url = url, serviceMethod = nonAnnotatedMethod))
        assertEquals(getUrl("test.json"), result.request.url.toString())
    }

    @Test
    fun `when experiment request succeeds, doesn't send failure pixel`() {
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
        val annotatedMethod = FakeApiService::class.java.getMethod("endpointRequiringTds")
        val url = getUrl(APPTP_TDS_PATH)
        val result = interceptor.intercept(FakeChain(url = url, serviceMethod = annotatedMethod, expectedResponseCode = 200))

        assertEquals(getUrl("anotherControlUrl"), result.request.url.toString())
        verifyNoInteractions(pixel)
    }

    @Test
    fun `when experiment request fails, sends failure pixel`() {
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
        val annotatedMethod = FakeApiService::class.java.getMethod("endpointRequiringTds")
        val url = getUrl(APPTP_TDS_PATH)
        val result = interceptor.intercept(FakeChain(url = url, serviceMethod = annotatedMethod, expectedResponseCode = 400))

        assertEquals(getUrl("anotherControlUrl"), result.request.url.toString())
        verify(pixel).fire(DeviceShieldPixelNames.ATP_TDS_EXPERIMENT_DOWNLOAD_FAILED.pixelName, mapOf("code" to "400"))
    }

    private fun getUrl(path: String): String = "$APPTP_TDS_BASE_URL$path"
}

class FakeFeatureTogglesInventory(private val features: List<Toggle>) : FeatureTogglesInventory {
    override suspend fun getAll(): List<Toggle> {
        return features
    }
}

private interface FakeApiService {
    @AppTPTdsRequired
    fun endpointRequiringTds()
    fun endpointNotRequiringTds()
}

abstract class TriggerTestScope private constructor()

@ContributesRemoteFeature(
    scope = TriggerTestScope::class,
    featureName = "appTrackerProtection"
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
