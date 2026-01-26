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

import com.duckduckgo.app.statistics.wideevents.api.AppSection
import com.duckduckgo.app.statistics.wideevents.api.ContextSection
import com.duckduckgo.app.statistics.wideevents.api.FeatureData
import com.duckduckgo.app.statistics.wideevents.api.FeatureSection
import com.duckduckgo.app.statistics.wideevents.api.GlobalSection
import com.duckduckgo.app.statistics.wideevents.api.WideEventRequest
import com.duckduckgo.app.statistics.wideevents.api.WideEventService
import com.duckduckgo.app.statistics.wideevents.db.WideEventRepository
import com.duckduckgo.app.statistics.wideevents.db.WideEventRepository.WideEventStatus.CANCELLED
import com.duckduckgo.app.statistics.wideevents.db.WideEventRepository.WideEventStatus.FAILURE
import com.duckduckgo.app.statistics.wideevents.db.WideEventRepository.WideEventStatus.SUCCESS
import com.duckduckgo.app.statistics.wideevents.db.WideEventRepository.WideEventStatus.UNKNOWN
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.device.DeviceInfo
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import logcat.logcat
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Named

@ContributesBinding(AppScope::class)
@Named("api")
class ApiWideEventSender @Inject constructor(
    private val wideEventService: WideEventService,
    private val appBuildConfig: AppBuildConfig,
    private val deviceInfo: DeviceInfo,
) : WideEventSender {

    override suspend fun sendWideEvent(event: WideEventRepository.WideEvent) {
        requireNotNull(event.status) { "Attempting to send wide event with null status" }

        val request = WideEventRequest(
            global = GlobalSection(
                platform = PLATFORM,
                type = TYPE,
                sampleRate = SAMPLE_RATE,
            ),
            app = AppSection(
                name = APP_NAME,
                version = appBuildConfig.versionName,
                formFactor = deviceInfo.formFactor().description,
                devMode = appBuildConfig.isDebug,
            ),
            feature = FeatureSection(
                name = event.name,
                status = event.status.toParamValue(),
                data = buildFeatureData(event),
            ),
            context = event.flowEntryPoint?.let { ContextSection(name = it) },
        )

        try {
            wideEventService.sendWideEvent(request)
        } catch (e: HttpException) {
            logcat { "HttpException on sending wide event. event name: ${event.name}, message: ${e.message()}" }
            if (e.code() !in 400..499) throw e // Don't throw on client error to avoid retries
        }
    }

    private fun buildFeatureData(event: WideEventRepository.WideEvent): FeatureData? {
        val extData = mutableMapOf<String, String>()

        // Add metadata as ext data
        event.metadata
            .filterValues { it != null }
            .forEach { (key, value) ->
                extData[key] = value!!
            }

        // Add steps as ext data with "step." prefix
        event.steps.forEach { (name, success) ->
            extData["step.$name"] = success.toString()
        }

        return if (extData.isNotEmpty()) {
            FeatureData(ext = extData)
        } else {
            null
        }
    }

    private companion object {
        const val PLATFORM = "Android"
        const val TYPE = "app"
        const val SAMPLE_RATE = 1
        const val APP_NAME = "DuckDuckGo Android"
    }
}

private fun WideEventRepository.WideEventStatus.toParamValue(): String =
    when (this) {
        SUCCESS -> "SUCCESS"
        FAILURE -> "FAILURE"
        CANCELLED -> "CANCELLED"
        UNKNOWN -> "UNKNOWN"
    }
