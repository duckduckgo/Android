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
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.device.DeviceInfo
import com.duckduckgo.common.utils.plugins.pixel.PixelParamRemovalPlugin
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Named

@ContributesBinding(AppScope::class)
@Named("pixel")
class PixelWideEventSender @Inject constructor(
    private val wideEventFeature: WideEventFeature,
    private val dispatchers: DispatcherProvider,
    private val pixelSender: Pixel,
    private val appBuildConfig: AppBuildConfig,
    private val deviceInfo: DeviceInfo,
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

                event.steps.forEach { (name, success) ->
                    put(PARAM_METADATA_STEP_PREFIX + name, success.toString())
                }
            }

        val encodedParameters =
            event.metadata
                .filterValues { it != null }
                .mapValues { it.value!! }
                .mapKeys { PARAM_METADATA_PREFIX + it.key }

        val basePixelName = PIXEL_NAME_PREFIX + event.name
        val countPixelName = basePixelName + COUNT_PIXEL_SUFFIX
        val dailyPixelName = basePixelName + DAILY_PIXEL_SUFFIX

        if (shouldEnqueuePixel()) {
            pixelSender.enqueueFire(
                pixelName = countPixelName,
                parameters = parameters,
                encodedParameters = encodedParameters,
            )

            pixelSender.enqueueFire(
                pixelName = dailyPixelName,
                parameters = parameters,
                encodedParameters = encodedParameters,
                type = Pixel.PixelType.Daily(),
            )
        } else {
            pixelSender.fire(
                pixelName = countPixelName,
                parameters = parameters,
                encodedParameters = encodedParameters,
                type = Pixel.PixelType.Count,
            )

            pixelSender.fire(
                pixelName = dailyPixelName,
                parameters = parameters,
                encodedParameters = encodedParameters,
                type = Pixel.PixelType.Daily(),
            )
        }
    }

    private fun getCommonPixelParameters(): Map<String, String> {
        return mapOf(
            PARAM_PLATFORM to "Android",
            PARAM_TYPE to "app",
            PARAM_SAMPLE_RATE to "1",
            PARAM_APP_NAME to "DuckDuckGo Android",
            PARAM_APP_VERSION to appBuildConfig.versionName,
            PARAM_FORM_FACTOR to deviceInfo.formFactor().description,
            PARAM_DEV_MODE to appBuildConfig.isDebug.toString(),
        )
    }

    private suspend fun shouldEnqueuePixel() = withContext(dispatchers.io()) {
        wideEventFeature.enqueueWideEventPixels().isEnabled()
    }

    private companion object {
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
        const val PARAM_DEV_MODE = "app.dev_mode"

        const val PARAM_METADATA_PREFIX = "feature.data.ext."
        const val PARAM_METADATA_STEP_PREFIX = PARAM_METADATA_PREFIX + "step."
    }
}

private fun WideEventRepository.WideEventStatus.toParamValue(): String =
    when (this) {
        SUCCESS -> "SUCCESS"
        FAILURE -> "FAILURE"
        CANCELLED -> "CANCELLED"
        UNKNOWN -> "UNKNOWN"
    }

@ContributesMultibinding(AppScope::class)
class WideEventPixelParamRemovalPlugin @Inject constructor() : PixelParamRemovalPlugin {
    override fun names(): List<Pair<String, Set<PixelParamRemovalPlugin.PixelParameter>>> =
        listOf(PIXEL_NAME_PREFIX to PixelParamRemovalPlugin.PixelParameter.Companion.removeAll())
}

private const val PIXEL_NAME_PREFIX = "wide_"
