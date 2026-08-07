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

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.wideevents.db.WideEventRepository
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.device.DeviceInfo
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Duration
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class PixelWideEventSenderTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val pixel: Pixel = mock()
    private val appBuildConfig: AppBuildConfig = mock()
    private val deviceInfo: DeviceInfo = mock()

    @SuppressLint("DenyListedApi")
    private val wideEventFeature: WideEventFeature = FakeFeatureToggleFactory
        .create(WideEventFeature::class.java)
        .also { it.enqueueWideEventPixels().setRawStoredState(Toggle.State(enable = true)) }

    private val pixelWideEventSender =
        PixelWideEventSender(
            pixelSender = pixel,
            appBuildConfig = appBuildConfig,
            deviceInfo = deviceInfo,
            wideEventFeature = wideEventFeature,
            dispatchers = coroutineRule.testDispatcherProvider,
        )

    @Before
    fun setUp() {
        whenever(appBuildConfig.versionName).thenReturn("5.123.0")
        whenever(appBuildConfig.isDebug).thenReturn(false)
        whenever(deviceInfo.formFactor()).thenReturn(DeviceInfo.FormFactor.PHONE)
    }

    @Test
    fun `when sendWideEvent called with completed event then sends count and daily pixels`() =
        runTest {
            val eventName = "subscription-purchase"

            val event =
                createWideEvent(
                    id = 123L,
                    name = eventName,
                    status = WideEventRepository.WideEventStatus.SUCCESS,
                    flowEntryPoint = "app_settings",
                    metadata = mapOf("plan_type" to "premium"),
                    steps = listOf(
                        WideEventRepository.WideEventStep(name = "init", success = true),
                        WideEventRepository.WideEventStep(name = "refresh_data", success = false),
                    ),
                )

            pixelWideEventSender.sendWideEvent(event)

            val expectedParameters =
                mapOf(
                    "meta.type" to "android-subscription-purchase",
                    "meta.version" to "1.0.0",
                    "global.platform" to "Android",
                    "global.type" to "app",
                    "global.sample_rate" to "1.0",
                    "global.is_first_daily_occurrence" to "false",
                    "app.name" to "DuckDuckGo Android",
                    "app.version" to "5.123.0",
                    "app.form_factor" to "phone",
                    "context.name" to "app_settings",
                    "feature.status" to "SUCCESS",
                    "app.dev_mode" to "false",
                    "feature.data.ext.step.init" to "true",
                    "feature.data.ext.step.refresh_data" to "false",
                )
            val expectedEncodedParameters = mapOf("feature.data.ext.plan_type" to "premium")

            verify(pixel).enqueueFire(
                pixelName = eq("wide_${eventName}_c"),
                parameters = eq(expectedParameters),
                encodedParameters = eq(expectedEncodedParameters),
                type = any(),
            )

            verify(pixel).enqueueFire(
                pixelName = eq("wide_${eventName}_d"),
                parameters = eq(expectedParameters),
                encodedParameters = eq(expectedEncodedParameters),
                type = any<Pixel.PixelType.Daily>(),
            )
        }

    @Test
    fun `when event has stored meta type and version then stored values are sent`() =
        runTest {
            val event =
                createWideEvent(
                    id = 789L,
                    name = "some-event",
                    status = WideEventRepository.WideEventStatus.SUCCESS,
                    metaType = "android-some-other-event",
                    metaVersion = "2.1.3",
                )

            pixelWideEventSender.sendWideEvent(event)

            verify(pixel).enqueueFire(
                pixelName = eq("wide_some-event_c"),
                parameters = argThat {
                    get("meta.type") == "android-some-other-event" && get("meta.version") == "2.1.3"
                },
                encodedParameters = any(),
                type = any(),
            )
        }

    @Test
    fun `when event is the first daily occurrence then count and daily pixels report it`() =
        runTest {
            val event =
                createWideEvent(
                    id = 456L,
                    name = "some-event",
                    status = WideEventRepository.WideEventStatus.SUCCESS,
                    isFirstDailyOccurrence = true,
                )

            pixelWideEventSender.sendWideEvent(event)

            verify(pixel).enqueueFire(
                pixelName = eq("wide_some-event_c"),
                parameters = argThat { get("global.is_first_daily_occurrence") == "true" },
                encodedParameters = any(),
                type = any(),
            )

            verify(pixel).enqueueFire(
                pixelName = eq("wide_some-event_d"),
                parameters = argThat { get("global.is_first_daily_occurrence") == "true" },
                encodedParameters = any(),
                type = any<Pixel.PixelType.Daily>(),
            )
        }

    @Test
    fun `when event is not the first daily occurrence then count and daily pixels report it`() =
        runTest {
            val event =
                createWideEvent(
                    id = 456L,
                    name = "some-event",
                    status = WideEventRepository.WideEventStatus.SUCCESS,
                    isFirstDailyOccurrence = false,
                )

            pixelWideEventSender.sendWideEvent(event)

            verify(pixel).enqueueFire(
                pixelName = eq("wide_some-event_c"),
                parameters = argThat { get("global.is_first_daily_occurrence") == "false" },
                encodedParameters = any(),
                type = any(),
            )

            verify(pixel).enqueueFire(
                pixelName = eq("wide_some-event_d"),
                parameters = argThat { get("global.is_first_daily_occurrence") == "false" },
                encodedParameters = any(),
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
        samplingProbability: Float = 1.0f,
        metaType: String = "android-$name",
        metaVersion: String = "1.0.0",
        isFirstDailyOccurrence: Boolean = false,
    ) = WideEventRepository.WideEvent(
        id = id,
        name = name,
        status = status,
        steps = steps,
        metadata = metadata,
        flowEntryPoint = flowEntryPoint,
        activeIntervals = emptyList(),
        cleanupPolicy =
        WideEventRepository.CleanupPolicy.OnTimeout(
            duration = Duration.ofHours(1),
            status = WideEventRepository.WideEventStatus.UNKNOWN,
            metadata = emptyMap(),
        ),
        createdAt = Instant.parse("2025-12-03T10:15:30.00Z"),
        samplingProbability = samplingProbability,
        metaType = metaType,
        metaVersion = metaVersion,
        isFirstDailyOccurrence = isFirstDailyOccurrence,
    )
}
