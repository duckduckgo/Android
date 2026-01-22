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
import com.duckduckgo.app.statistics.wideevents.api.AppSection
import com.duckduckgo.app.statistics.wideevents.api.ContextSection
import com.duckduckgo.app.statistics.wideevents.api.FeatureData
import com.duckduckgo.app.statistics.wideevents.api.FeatureSection
import com.duckduckgo.app.statistics.wideevents.api.GlobalSection
import com.duckduckgo.app.statistics.wideevents.api.WideEventRequest
import com.duckduckgo.app.statistics.wideevents.api.WideEventService
import com.duckduckgo.app.statistics.wideevents.db.WideEventRepository
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.device.DeviceInfo
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.HttpException
import retrofit2.Response
import java.time.Duration
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class ApiWideEventSenderTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val wideEventService: WideEventService = mock()

    private val appBuildConfig: AppBuildConfig = mock { appBuildConfig ->
        whenever(appBuildConfig.versionName).thenReturn("5.123.0")
        whenever(appBuildConfig.isDebug).thenReturn(false)
    }

    private val deviceInfo: DeviceInfo = mock { deviceInfo ->
        whenever(deviceInfo.formFactor()).thenReturn(DeviceInfo.FormFactor.PHONE)
    }

    private val apiWideEventSender = ApiWideEventSender(
        wideEventService = wideEventService,
        appBuildConfig = appBuildConfig,
        deviceInfo = deviceInfo,
    )

    @Test
    fun `when sendWideEvent called with completed event then sends correct request to service`() = runTest {
        val eventName = "subscription-purchase"

        val event = createWideEvent(
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

        apiWideEventSender.sendWideEvent(event)

        val expectedRequest = WideEventRequest(
            global = GlobalSection(
                platform = "Android",
                type = "app",
                sampleRate = 1,
            ),
            app = AppSection(
                name = "DuckDuckGo Android",
                version = "5.123.0",
                formFactor = "phone",
                devMode = false,
            ),
            feature = FeatureSection(
                name = eventName,
                status = "SUCCESS",
                data = FeatureData(
                    ext = mapOf(
                        "plan_type" to "premium",
                        "step.init" to "true",
                        "step.refresh_data" to "false",
                    ),
                ),
            ),
            context = ContextSection(name = "app_settings"),
        )

        verify(wideEventService).sendWideEvent(eq(expectedRequest))
    }

    @Test
    fun `when sendWideEvent called without flowEntryPoint then context is null`() = runTest {
        val event = createWideEvent(
            id = 456L,
            name = "feature-event",
            status = WideEventRepository.WideEventStatus.FAILURE,
            flowEntryPoint = null,
        )

        apiWideEventSender.sendWideEvent(event)

        val expectedRequest = WideEventRequest(
            global = GlobalSection(
                platform = "Android",
                type = "app",
                sampleRate = 1,
            ),
            app = AppSection(
                name = "DuckDuckGo Android",
                version = "5.123.0",
                formFactor = "phone",
                devMode = false,
            ),
            feature = FeatureSection(
                name = "feature-event",
                status = "FAILURE",
                data = null,
            ),
            context = null,
        )

        verify(wideEventService).sendWideEvent(eq(expectedRequest))
    }

    @Test
    fun `when sendWideEvent called without metadata and steps then feature data is null`() = runTest {
        val event = createWideEvent(
            id = 789L,
            name = "simple-event",
            status = WideEventRepository.WideEventStatus.CANCELLED,
            metadata = emptyMap(),
            steps = emptyList(),
        )

        apiWideEventSender.sendWideEvent(event)

        val expectedRequest = WideEventRequest(
            global = GlobalSection(
                platform = "Android",
                type = "app",
                sampleRate = 1,
            ),
            app = AppSection(
                name = "DuckDuckGo Android",
                version = "5.123.0",
                formFactor = "phone",
                devMode = false,
            ),
            feature = FeatureSection(
                name = "simple-event",
                status = "CANCELLED",
                data = null,
            ),
            context = null,
        )

        verify(wideEventService).sendWideEvent(eq(expectedRequest))
    }

    @Test
    fun `when sendWideEvent called with debug build then devMode is true`() = runTest {
        whenever(appBuildConfig.isDebug).thenReturn(true)

        val event = createWideEvent(
            id = 100L,
            name = "debug-event",
            status = WideEventRepository.WideEventStatus.SUCCESS,
        )

        apiWideEventSender.sendWideEvent(event)

        val expectedRequest = WideEventRequest(
            global = GlobalSection(
                platform = "Android",
                type = "app",
                sampleRate = 1,
            ),
            app = AppSection(
                name = "DuckDuckGo Android",
                version = "5.123.0",
                formFactor = "phone",
                devMode = true,
            ),
            feature = FeatureSection(
                name = "debug-event",
                status = "SUCCESS",
                data = null,
            ),
            context = null,
        )

        verify(wideEventService).sendWideEvent(eq(expectedRequest))
    }

    @Test
    fun `when sendWideEvent called on tablet then formFactor is tablet`() = runTest {
        whenever(deviceInfo.formFactor()).thenReturn(DeviceInfo.FormFactor.TABLET)

        val event = createWideEvent(
            id = 200L,
            name = "tablet-event",
            status = WideEventRepository.WideEventStatus.SUCCESS,
        )

        apiWideEventSender.sendWideEvent(event)

        val expectedRequest = WideEventRequest(
            global = GlobalSection(
                platform = "Android",
                type = "app",
                sampleRate = 1,
            ),
            app = AppSection(
                name = "DuckDuckGo Android",
                version = "5.123.0",
                formFactor = "tablet",
                devMode = false,
            ),
            feature = FeatureSection(
                name = "tablet-event",
                status = "SUCCESS",
                data = null,
            ),
            context = null,
        )

        verify(wideEventService).sendWideEvent(eq(expectedRequest))
    }

    @Test
    fun `when sendWideEvent called with UNKNOWN status then status is UNKNOWN`() = runTest {
        val event = createWideEvent(
            id = 300L,
            name = "unknown-event",
            status = WideEventRepository.WideEventStatus.UNKNOWN,
        )

        apiWideEventSender.sendWideEvent(event)

        val expectedRequest = WideEventRequest(
            global = GlobalSection(
                platform = "Android",
                type = "app",
                sampleRate = 1,
            ),
            app = AppSection(
                name = "DuckDuckGo Android",
                version = "5.123.0",
                formFactor = "phone",
                devMode = false,
            ),
            feature = FeatureSection(
                name = "unknown-event",
                status = "UNKNOWN",
                data = null,
            ),
            context = null,
        )

        verify(wideEventService).sendWideEvent(eq(expectedRequest))
    }

    @Test
    fun `when sendWideEvent called with metadata containing null values then null values are filtered out`() = runTest {
        val event = createWideEvent(
            id = 400L,
            name = "filtered-event",
            status = WideEventRepository.WideEventStatus.SUCCESS,
            metadata = mapOf(
                "key1" to "value1",
                "key2" to null,
                "key3" to "value3",
            ),
        )

        apiWideEventSender.sendWideEvent(event)

        val expectedRequest = WideEventRequest(
            global = GlobalSection(
                platform = "Android",
                type = "app",
                sampleRate = 1,
            ),
            app = AppSection(
                name = "DuckDuckGo Android",
                version = "5.123.0",
                formFactor = "phone",
                devMode = false,
            ),
            feature = FeatureSection(
                name = "filtered-event",
                status = "SUCCESS",
                data = FeatureData(
                    ext = mapOf(
                        "key1" to "value1",
                        "key3" to "value3",
                    ),
                ),
            ),
            context = null,
        )

        verify(wideEventService).sendWideEvent(eq(expectedRequest))
    }

    @Test
    fun `when sendWideEvent called and API returns client error then it does not throw`() = runTest {
        val event = createWideEvent(
            id = 450L,
            name = "client-error-event",
            status = WideEventRepository.WideEventStatus.SUCCESS,
        )

        whenever(wideEventService.sendWideEvent(any())).thenThrow(httpException(400))

        apiWideEventSender.sendWideEvent(event)

        verify(wideEventService).sendWideEvent(any())
    }

    @Test(expected = HttpException::class)
    fun `when sendWideEvent called and API returns server error then it rethrows`() = runTest {
        val event = createWideEvent(
            id = 460L,
            name = "server-error-event",
            status = WideEventRepository.WideEventStatus.SUCCESS,
        )

        whenever(wideEventService.sendWideEvent(any())).thenThrow(httpException(500))

        apiWideEventSender.sendWideEvent(event)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `when sendWideEvent called with null status then throws exception`() = runTest {
        val event = createWideEvent(
            id = 500L,
            name = "null-status-event",
            status = null,
        )

        apiWideEventSender.sendWideEvent(event)
    }

    private fun httpException(code: Int): HttpException {
        val responseBody = "failure".toResponseBody("text/json".toMediaTypeOrNull())
        return HttpException(Response.error<String>(code, responseBody))
    }

    private fun createWideEvent(
        id: Long,
        name: String,
        status: WideEventRepository.WideEventStatus?,
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
