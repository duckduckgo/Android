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
import com.duckduckgo.common.test.api.FakeChain
import com.duckduckgo.data.store.api.FakeSharedPreferencesProvider
import com.duckduckgo.feature.toggles.api.FakeToggleStore
import com.duckduckgo.feature.toggles.api.FeatureToggles
import com.duckduckgo.feature.toggles.api.FeatureTogglesInventory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultValue
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.mobile.android.vpn.feature.AppTpRemoteFeatures.Cohorts.CONTROL
import com.duckduckgo.mobile.android.vpn.feature.AppTpRemoteFeatures.Cohorts.TREATMENT
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixelNames
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.pixels.RealDeviceShieldPixels
import com.squareup.moshi.Moshi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
class AppTPBlockListInterceptorApiPluginTest {

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
    private val sharedPreferencesProvider = FakeSharedPreferencesProvider()

    private lateinit var deviceShieldPixels: DeviceShieldPixels

    @Before
    fun setup() {
        testBlockListFeature = FeatureToggles.Builder(
            FakeToggleStore(),
            featureName = "appTrackerProtection",
        ).build().create(TestBlockListFeature::class.java)

        inventory = mock<FeatureTogglesInventory>()

        runBlocking {
            whenever(inventory.getAllTogglesForParent(Mockito.anyString())).thenReturn(
                listOf(
                    testBlockListFeature.tdsNextExperimentTest(),
                    testBlockListFeature.tdsNextExperimentAnotherTest(),
                ),
            )
        }

        deviceShieldPixels = RealDeviceShieldPixels(pixel, sharedPreferencesProvider)

        interceptor = AppTPBlockListInterceptorApiPlugin(inventory, moshi, deviceShieldPixels)
    }

    @Test
    fun `when multiple experiments enabled, use the first one`() {
        testBlockListFeature.tdsNextExperimentTest().setRawStoredState(
            makeExperiment(
                controlUrl = "controlUrl1",
                treatmentUrl = "treatmentUrl1",
            ),
        )
        testBlockListFeature.tdsNextExperimentAnotherTest().setRawStoredState(
            makeExperiment(
                controlUrl = "controlUrl2",
                treatmentUrl = "treatmentUrl2",
            ),
        )
        checkEndpointIntercept("controlUrl1")
    }

    @Test
    fun `when cohort is treatment use treatment URL`() {
        testBlockListFeature.tdsNextExperimentAnotherTest().setRawStoredState(makeExperiment(useTreatment = true))
        checkEndpointIntercept("treatmentUrl")
    }

    @Test
    fun `when cohort is control use control URL`() {
        testBlockListFeature.tdsNextExperimentAnotherTest().setRawStoredState(makeExperiment())
        checkEndpointIntercept("controlUrl")
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
        checkEndpointIntercept("nextUrl")
    }

    @Test
    fun `when no experiments enabled, use default path`() {
        checkEndpointIntercept(APPTP_TDS_PATH)
    }

    @Test
    fun `when feature name doesn't match prefix, it is ignored`() {
        testBlockListFeature.nonMatchingFeatureName().setRawStoredState(makeExperiment())
        checkEndpointIntercept(APPTP_TDS_PATH)
    }

    @Test
    fun `when annotation not present, even if experiment present, ignore and proceed as normal`() {
        testBlockListFeature.tdsNextExperimentAnotherTest().setRawStoredState(makeExperiment())
        // even though there's an experiment, we expect the request to not be altered,
        // as the requesting method doesn't have the annotation
        checkEndpointIntercept(APPTP_TDS_PATH, requestMethodName = "endpointNotRequiringTds")
    }

    @Test
    fun `when experiment request succeeds, doesn't send failure pixel`() {
        testBlockListFeature.tdsNextExperimentAnotherTest().setRawStoredState(makeExperiment())
        checkEndpointIntercept("controlUrl", expectedResponseCode = 200)
        verifyNoInteractions(pixel)
    }

    @Test
    fun `when experiment request fails, sends failure pixel`() {
        testBlockListFeature.tdsNextExperimentAnotherTest().setRawStoredState(makeExperiment())
        checkEndpointIntercept("controlUrl", expectedResponseCode = 400)
        verify(pixel).fire(DeviceShieldPixelNames.ATP_TDS_EXPERIMENT_DOWNLOAD_FAILED.pixelName, mapOf("code" to "400"))
    }

    private fun getUrl(path: String): String = "$APPTP_TDS_BASE_URL$path"

    /**
     * Check that the usual TDS endpoint request is rewritten to `expectedURL`.
     *
     * @param requestMethodName - name of the method generating the request. By default this is a method that is annotated (and thus would be intercepted).
     */
    private fun checkEndpointIntercept(expectedURL: String, expectedResponseCode: Int? = null, requestMethodName: String = "endpointRequiringTds") {
        val method = FakeApiService::class.java.getMethod(requestMethodName)
        val url = getUrl(APPTP_TDS_PATH)
        val result = interceptor.intercept(FakeChain(url = url, serviceMethod = method, expectedResponseCode = expectedResponseCode))
        assertEquals(getUrl(expectedURL), result.request.url.toString())
    }

    /**
     * Configuration for a valid experiment.
     *
     * @param useTreatment - weight the treatment as 1 instead of control.
     */
    private fun makeExperiment(
        useTreatment: Boolean = false,
        treatmentUrl: String = "treatmentUrl",
        controlUrl: String = "controlUrl",
    ): State {
        return State(
            remoteEnableState = true,
            enable = true,
            settings = configAdapter.toJson(
                Config(treatmentUrl = treatmentUrl, controlUrl = controlUrl),
            ),
            cohorts = listOf(
                State.Cohort(name = CONTROL.cohortName, weight = if (useTreatment) 0 else 1),
                State.Cohort(name = TREATMENT.cohortName, weight = if (useTreatment) 1 else 0),
            ),
        )
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
    featureName = "appTrackerProtection",
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
