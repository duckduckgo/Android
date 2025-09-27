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

package com.duckduckgo.app.statistics.wideevents

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.wideevents.db.WideEventRepository
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.device.DeviceInfo
import com.duckduckgo.feature.toggles.api.FeatureTogglesInventory
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class PixelWideEventSenderTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val pixel: Pixel = mock()
    private val appBuildConfig: AppBuildConfig = mock()
    private val deviceInfo: DeviceInfo = mock()
    private val featureTogglesInventory: FeatureTogglesInventory = mock()

    private val pixelWideEventSender = PixelWideEventSender(
        pixelSender = pixel,
        appBuildConfig = appBuildConfig,
        deviceInfo = deviceInfo,
        featureTogglesInventory = featureTogglesInventory,
    )

    @Before
    fun setUp() {
        whenever(appBuildConfig.versionName).thenReturn("5.123.0")
        whenever(appBuildConfig.isDebug).thenReturn(false)
        whenever(deviceInfo.formFactor()).thenReturn(DeviceInfo.FormFactor.PHONE)
        runBlocking {
            whenever(featureTogglesInventory.getAllActiveExperimentToggles()).thenReturn(emptyList())
        }
    }

    @Test
    fun `when sendWideEvent called with completed event then sends count and daily pixels`() = runTest {
        val event = createWideEvent(
            id = 123L,
            name = "subscription.purchase",
            status = WideEventRepository.WideEventStatus.SUCCESS,
            flowEntryPoint = "app_settings",
            metadata = mapOf("plan_type" to "premium"),
        )

        pixelWideEventSender.sendWideEvent(event)

        val expectedParameters = mapOf(
            "global.platform" to "Android",
            "global.type" to "app",
            "global.sample_rate" to "1",
            "app.name" to "DuckDuckGo Android",
            "app.version" to "5.123.0",
            "app.form_factor" to "phone",
            "app.native_apps_experiments" to "",
            "context.name" to "app_settings",
            "feature.status" to "SUCCESS",
            "dev_mode" to "false",
        )
        val expectedEncodedParameters = mapOf("plan_type" to "premium")

        verify(pixel).fire(
            pixelName = eq("wide.subscription.purchase.c"),
            parameters = eq(expectedParameters),
            encodedParameters = eq(expectedEncodedParameters),
            type = eq(Pixel.PixelType.Count),
        )

        verify(pixel).fire(
            pixelName = eq("wide.subscription.purchase.d"),
            parameters = eq(expectedParameters),
            encodedParameters = eq(expectedEncodedParameters),
            type = any<Pixel.PixelType.Daily>(),
        )
    }

    private fun createWideEvent(
        id: Long,
        name: String,
        status: WideEventRepository.WideEventStatus,
        steps: List<WideEventRepository.WideEventStep> = emptyList(),
        metadata: Map<String, String?> = emptyMap(),
        flowEntryPoint: String? = null,
    ) = WideEventRepository.WideEvent(
        id = id,
        name = name,
        status = status,
        steps = steps,
        metadata = metadata,
        flowEntryPoint = flowEntryPoint,
        activeIntervals = emptyList(),
        cleanupPolicy = WideEventRepository.CleanupPolicy.OnTimeout(
            duration = Duration.ofHours(1),
            status = WideEventRepository.WideEventStatus.UNKNOWN,
            metadata = emptyMap(),
        ),
        createdAt = Instant.parse("2025-12-03T10:15:30.00Z"),
    )
}
