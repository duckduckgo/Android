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

package com.duckduckgo.promptscoordinator.impl.exposure

import com.duckduckgo.app.statistics.pixels.Pixel

/** @property randomizeTimestamp whether the pixel is routed through PETAL timestamp randomization. */
internal enum class PromptExposurePixelName(
    override val pixelName: String,
    val randomizeTimestamp: Boolean,
) : Pixel.PixelName {
    PROMPT_EXPOSURE("m_prompt_exposure", randomizeTimestamp = true),
    PROMPT_SHOWN("m_prompt_shown", randomizeTimestamp = true),
    PROMPT_USER_WEEK("m_prompt_user_week", randomizeTimestamp = false),
    PROMPT_GAP("m_prompt_gap", randomizeTimestamp = true),
    PROMPT_SESSION("m_prompt_session", randomizeTimestamp = true),
}

internal object PromptExposurePixelParams {
    const val DAYS_SINCE_INSTALL = "days_since_install"
    const val NTH_IN_WEEK = "nth_in_week"
    const val PROMPT_ID = "prompt_id"
    const val GAP_BUCKET = "gap_bucket"
    const val PROMPT_TYPE = "prompt_type"
    const val PROMPT_SHOWN = "prompt_shown"
    const val VERSION = "version"
}

/**
 * Sent as `version` on every prompt exposure pixel. Bump it whenever the meaning of a parameter or
 * [SESSION_SAMPLE_RATE] changes, so data from before and after can be told apart.
 */
internal const val PROMPT_EXPOSURE_PIXELS_VERSION = "1"

/** Fires [pixel] with the parameters every prompt exposure pixel carries on top of [parameters]. */
internal fun Pixel.firePromptExposurePixel(
    pixel: PromptExposurePixelName,
    vararg parameters: Pair<String, String>,
    type: Pixel.PixelType = Pixel.PixelType.Count,
) {
    val allParameters = buildMap {
        putAll(parameters)
        put(PromptExposurePixelParams.VERSION, PROMPT_EXPOSURE_PIXELS_VERSION)
        if (pixel.randomizeTimestamp) put(Pixel.PixelParameter.PETAL, Pixel.PixelValues.PETAL_RANDOMIZE)
    }
    fire(pixel, allParameters, type = type)
}

/** Every value `prompt_id` may take; anything else is sent as [OTHER_PROMPT_ID]. */
internal val KNOWN_PROMPT_IDS = setOf(
    "default_browser_changed_survey_evaluator",
    "win_back_prompt",
    "re_engagement_prompt",
    "remote_message_modal",
    "additional_default_browser_prompts",
    "subscription_promo_modal",
    "add_widget_modal",
    "app_rating_prompt",
    "cookie_popup_opt_in",
    "remote_message_card",
    "new_address_bar_picker",
    "import_passwords_google",
)

/** Keeps `prompt_id` bounded when a new evaluator ships before it is registered here. */
internal const val OTHER_PROMPT_ID = "other"

internal const val REMOTE_MESSAGE_CARD_PROMPT_ID = "remote_message_card"
