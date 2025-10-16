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

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.wideevents.db.WideEventRepository
import com.duckduckgo.app.statistics.wideevents.db.WideEventRepository.WideEventStatus.CANCELLED
import com.duckduckgo.app.statistics.wideevents.db.WideEventRepository.WideEventStatus.FAILURE
import com.duckduckgo.app.statistics.wideevents.db.WideEventRepository.WideEventStatus.SUCCESS
import com.duckduckgo.app.statistics.wideevents.db.WideEventRepository.WideEventStatus.UNKNOWN
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.device.DeviceInfo
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.FeatureTogglesInventory
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface WideEventSender {
    suspend fun sendWideEvent(event: WideEventRepository.WideEvent)
}

@ContributesBinding(AppScope::class)
class PixelWideEventSender @Inject constructor(
    private val pixelSender: Pixel,
    private val appBuildConfig: AppBuildConfig,
    private val deviceInfo: DeviceInfo,
    private val featureTogglesInventory: FeatureTogglesInventory,
) : WideEventSender {
    override suspend fun sendWideEvent(event: WideEventRepository.WideEvent) {
        requireNotNull(event.status) { "Attempting to send wide event with null status" }

        val parameters =
            mutableMapOf<String, String>().apply {
                putAll(getCommonPixelParameters())
                put(PARAM_STATUS, event.status.toParamValue())

                if (event.flowEntryPoint != null) {
                    put(PARAM_CONTEXT_NAME, event.flowEntryPoint)
                }
            }

        val encodedParameters =
            event.metadata
                .filterValues { it != null }
                .mapValues { it.value!! }
                .mapKeys { PARAM_METADATA_PREFIX + it.key }

        val basePixelName = PIXEL_NAME_PREFIX + event.name

        pixelSender.fire(
            pixelName = basePixelName + COUNT_PIXEL_SUFFIX,
            parameters = parameters,
            encodedParameters = encodedParameters,
            type = Pixel.PixelType.Count,
        )

        pixelSender.fire(
            pixelName = basePixelName + DAILY_PIXEL_SUFFIX,
            parameters = parameters,
            encodedParameters = encodedParameters,
            type = Pixel.PixelType.Daily(),
        )
    }

    private suspend fun getCommonPixelParameters(): Map<String, String> {
        val activeNaExperimentNames =
            featureTogglesInventory
                .getAllActiveExperimentToggles()
                .map { it.featureName().name }

        return mapOf(
            PARAM_PLATFORM to "Android",
            PARAM_TYPE to "app",
            PARAM_SAMPLE_RATE to "1",
            PARAM_APP_NAME to "DuckDuckGo Android",
            PARAM_APP_VERSION to appBuildConfig.versionName,
            PARAM_FORM_FACTOR to deviceInfo.formFactor().description,
            PARAM_NA_EXPERIMENTS to activeNaExperimentNames.joinToString(","),
            PARAM_DEV_MODE to appBuildConfig.isDebug.toString(),
        )
    }

    private companion object {
        const val PIXEL_NAME_PREFIX = "wide_"
        const val COUNT_PIXEL_SUFFIX = "_c"
        const val DAILY_PIXEL_SUFFIX = "_d"

        const val PARAM_PLATFORM = "global.platform"
        const val PARAM_TYPE = "global.type"
        const val PARAM_SAMPLE_RATE = "global.sample_rate"
        const val PARAM_CONTEXT_NAME = "context.name"
        const val PARAM_STATUS = "feature.status"
        const val PARAM_APP_NAME = "app.name"
        const val PARAM_APP_VERSION = "app.version"
        const val PARAM_FORM_FACTOR = "app.form_factor"
        const val PARAM_NA_EXPERIMENTS = "app.native_apps_experiments"
        const val PARAM_DEV_MODE = "app.dev_mode"

        const val PARAM_METADATA_PREFIX = "feature.data.ext."
    }
}

private fun WideEventRepository.WideEventStatus.toParamValue(): String =
    when (this) {
        SUCCESS -> "SUCCESS"
        FAILURE -> "FAILURE"
        CANCELLED -> "CANCELLED"
        UNKNOWN -> "UNKNOWN"
    }
