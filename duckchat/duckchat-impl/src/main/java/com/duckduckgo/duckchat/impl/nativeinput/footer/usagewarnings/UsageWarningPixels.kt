/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.duckchat.impl.nativeinput.footer.usagewarnings

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.plugins.pixel.PixelParamRemovalPlugin
import com.duckduckgo.common.utils.plugins.pixel.PixelParamRemovalPlugin.PixelParameter
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.nativeinput.footer.usagewarnings.UsageWarningExposureKind.APPROACHING
import com.duckduckgo.duckchat.impl.nativeinput.footer.usagewarnings.UsageWarningExposureKind.HIGH_USAGE_MODEL_NOTICE
import com.duckduckgo.duckchat.impl.nativeinput.footer.usagewarnings.UsageWarningExposureKind.LIMIT_REACHED
import com.duckduckgo.duckchat.impl.nativeinput.footer.usagewarnings.UsageWarningPixelName.HIGH_USAGE_MODEL_NOTICE_ABANDONED
import com.duckduckgo.duckchat.impl.nativeinput.footer.usagewarnings.UsageWarningPixelName.HIGH_USAGE_MODEL_NOTICE_DISMISSED
import com.duckduckgo.duckchat.impl.nativeinput.footer.usagewarnings.UsageWarningPixelName.HIGH_USAGE_MODEL_NOTICE_MODEL_SWITCHED
import com.duckduckgo.duckchat.impl.nativeinput.footer.usagewarnings.UsageWarningPixelName.HIGH_USAGE_MODEL_NOTICE_PROMPT_SUBMITTED
import com.duckduckgo.duckchat.impl.nativeinput.footer.usagewarnings.UsageWarningPixelName.HIGH_USAGE_MODEL_NOTICE_SHOWN
import com.duckduckgo.duckchat.impl.nativeinput.footer.usagewarnings.UsageWarningPixelName.USAGE_WARNING_APPROACHING_ABANDONED
import com.duckduckgo.duckchat.impl.nativeinput.footer.usagewarnings.UsageWarningPixelName.USAGE_WARNING_APPROACHING_DISMISSED
import com.duckduckgo.duckchat.impl.nativeinput.footer.usagewarnings.UsageWarningPixelName.USAGE_WARNING_APPROACHING_MODEL_SWITCHED
import com.duckduckgo.duckchat.impl.nativeinput.footer.usagewarnings.UsageWarningPixelName.USAGE_WARNING_APPROACHING_PROMPT_SUBMITTED
import com.duckduckgo.duckchat.impl.nativeinput.footer.usagewarnings.UsageWarningPixelName.USAGE_WARNING_APPROACHING_SHOWN
import com.duckduckgo.duckchat.impl.nativeinput.footer.usagewarnings.UsageWarningPixelName.USAGE_WARNING_LIMIT_REACHED_ABANDONED
import com.duckduckgo.duckchat.impl.nativeinput.footer.usagewarnings.UsageWarningPixelName.USAGE_WARNING_LIMIT_REACHED_MODEL_SWITCHED
import com.duckduckgo.duckchat.impl.nativeinput.footer.usagewarnings.UsageWarningPixelName.USAGE_WARNING_LIMIT_REACHED_PROMPT_SUBMITTED
import com.duckduckgo.duckchat.impl.nativeinput.footer.usagewarnings.UsageWarningPixelName.USAGE_WARNING_LIMIT_REACHED_SHOWN
import com.duckduckgo.duckchat.impl.nativeinput.footer.usagewarnings.UsageWarningPixelName.USAGE_WARNING_SWITCH_MODEL_TAPPED
import com.duckduckgo.duckchat.impl.nativeinput.footer.usagewarnings.UsageWarningPixelName.USAGE_WARNING_UPSELL_TAPPED
import com.duckduckgo.duckchat.impl.nativeinput.footer.usagewarnings.UsageWarningPixelName.USAGE_WARNING_WEEKLY_LIMIT_TAPPED
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelParameters
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelSurface
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class UsageWarningPixelName(private val baseName: String) {
    USAGE_WARNING_APPROACHING_SHOWN("m_aichat_usage_warning_approaching_shown"),
    USAGE_WARNING_APPROACHING_DISMISSED("m_aichat_usage_warning_approaching_dismissed"),
    USAGE_WARNING_APPROACHING_PROMPT_SUBMITTED("m_aichat_usage_warning_approaching_prompt_submitted"),
    USAGE_WARNING_APPROACHING_MODEL_SWITCHED("m_aichat_usage_warning_approaching_model_switched"),
    USAGE_WARNING_APPROACHING_ABANDONED("m_aichat_usage_warning_approaching_abandoned"),
    USAGE_WARNING_LIMIT_REACHED_SHOWN("m_aichat_usage_warning_limit_reached_shown"),
    USAGE_WARNING_LIMIT_REACHED_PROMPT_SUBMITTED("m_aichat_usage_warning_limit_reached_prompt_submitted"),
    USAGE_WARNING_LIMIT_REACHED_MODEL_SWITCHED("m_aichat_usage_warning_limit_reached_model_switched"),
    USAGE_WARNING_LIMIT_REACHED_ABANDONED("m_aichat_usage_warning_limit_reached_abandoned"),
    USAGE_WARNING_SWITCH_MODEL_TAPPED("m_aichat_usage_warning_switch_model_tapped"),
    USAGE_WARNING_UPSELL_TAPPED("m_aichat_usage_warning_upsell_tapped"),
    USAGE_WARNING_WEEKLY_LIMIT_TAPPED("m_aichat_usage_warning_weekly_limit_tapped"),
    HIGH_USAGE_MODEL_NOTICE_SHOWN("m_aichat_high_usage_model_notice_shown"),
    HIGH_USAGE_MODEL_NOTICE_DISMISSED("m_aichat_high_usage_model_notice_dismissed"),
    HIGH_USAGE_MODEL_NOTICE_PROMPT_SUBMITTED("m_aichat_high_usage_model_notice_prompt_submitted"),
    HIGH_USAGE_MODEL_NOTICE_MODEL_SWITCHED("m_aichat_high_usage_model_notice_model_switched"),
    HIGH_USAGE_MODEL_NOTICE_ABANDONED("m_aichat_high_usage_model_notice_abandoned"),
    ;

    val count: Pixel.PixelName = WireName("${baseName}_count")
    val daily: Pixel.PixelName = WireName("${baseName}_daily")

    private data class WireName(override val pixelName: String) : Pixel.PixelName
}

/**
 * Maps a measurement event to its pixel. A reached limit has no close button, so its dismissal reports nothing.
 */
fun UsageWarningEvent.pixelName(): UsageWarningPixelName? = when (this) {
    is UsageWarningEvent.Shown -> when (exposure.kind) {
        APPROACHING -> USAGE_WARNING_APPROACHING_SHOWN
        LIMIT_REACHED -> USAGE_WARNING_LIMIT_REACHED_SHOWN
        HIGH_USAGE_MODEL_NOTICE -> HIGH_USAGE_MODEL_NOTICE_SHOWN
    }
    is UsageWarningEvent.Dismissed -> when (exposure.kind) {
        APPROACHING -> USAGE_WARNING_APPROACHING_DISMISSED
        LIMIT_REACHED -> null
        HIGH_USAGE_MODEL_NOTICE -> HIGH_USAGE_MODEL_NOTICE_DISMISSED
    }
    is UsageWarningEvent.PromptSubmitted -> when (exposure.kind) {
        APPROACHING -> USAGE_WARNING_APPROACHING_PROMPT_SUBMITTED
        LIMIT_REACHED -> USAGE_WARNING_LIMIT_REACHED_PROMPT_SUBMITTED
        HIGH_USAGE_MODEL_NOTICE -> HIGH_USAGE_MODEL_NOTICE_PROMPT_SUBMITTED
    }
    is UsageWarningEvent.ModelSwitched -> when (exposure.kind) {
        APPROACHING -> USAGE_WARNING_APPROACHING_MODEL_SWITCHED
        LIMIT_REACHED -> USAGE_WARNING_LIMIT_REACHED_MODEL_SWITCHED
        HIGH_USAGE_MODEL_NOTICE -> HIGH_USAGE_MODEL_NOTICE_MODEL_SWITCHED
    }
    is UsageWarningEvent.Abandoned -> when (exposure.kind) {
        APPROACHING -> USAGE_WARNING_APPROACHING_ABANDONED
        LIMIT_REACHED -> USAGE_WARNING_LIMIT_REACHED_ABANDONED
        HIGH_USAGE_MODEL_NOTICE -> HIGH_USAGE_MODEL_NOTICE_ABANDONED
    }
    is UsageWarningEvent.CtaTapped -> when (cta) {
        UsageWarningCta.SWITCH_MODEL -> USAGE_WARNING_SWITCH_MODEL_TAPPED
        UsageWarningCta.UPSELL -> USAGE_WARNING_UPSELL_TAPPED
        UsageWarningCta.WEEKLY_LIMIT -> USAGE_WARNING_WEEKLY_LIMIT_TAPPED
    }
}

fun UsageWarningExposure.pixelParameters(surface: DuckChatPixelSurface): Map<String, String> = buildMap {
    put(DuckChatPixelParameters.SURFACE, surface.value)
    window?.let { put(WINDOW, it.jsonId) }
    percentBucket?.let { put(PERCENT_BUCKET, it.toString()) }
    modelId?.let { put(DuckChatPixelParameters.MODEL_ID, it) }
}

private const val WINDOW = "window"
private const val PERCENT_BUCKET = "percent_bucket"

@ContributesBinding(AppScope::class)
class RealUsageWarningPixelSender @Inject constructor(
    private val pixel: Pixel,
    private val dispatchers: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : UsageWarningPixelSender {

    override fun send(
        event: UsageWarningEvent,
        surface: DuckChatPixelSurface,
    ) {
        val name = event.pixelName() ?: return
        val parameters = event.exposure.pixelParameters(surface)
        appCoroutineScope.launch(dispatchers.io()) {
            pixel.fire(name.count, parameters = parameters)
            pixel.fire(name.daily, parameters = parameters, type = Pixel.PixelType.Daily())
        }
    }
}

@ContributesMultibinding(AppScope::class)
class UsageWarningPixelParamRemovalPlugin @Inject constructor() : PixelParamRemovalPlugin {
    override fun names(): List<Pair<String, Set<PixelParameter>>> =
        UsageWarningPixelName.entries.flatMap { name ->
            listOf(
                name.count.pixelName to PixelParameter.removeAtb(),
                name.daily.pixelName to PixelParameter.removeAtb(),
            )
        }
}
