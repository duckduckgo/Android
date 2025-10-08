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

package com.duckduckgo.daxprompts.impl.pixels

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.plugins.pixel.PixelParamRemovalPlugin
import com.duckduckgo.common.utils.plugins.pixel.PixelParamRemovalPlugin.PixelParameter
import com.duckduckgo.daxprompts.impl.pixels.DaxPromptBrowserComparisonPixelName.REACTIVATE_USERS_BROWSER_COMPARISON_PROMPT_CLOSED
import com.duckduckgo.daxprompts.impl.pixels.DaxPromptBrowserComparisonPixelName.REACTIVATE_USERS_BROWSER_COMPARISON_PROMPT_DEFAULT_BROWSER_SET
import com.duckduckgo.daxprompts.impl.pixels.DaxPromptBrowserComparisonPixelName.REACTIVATE_USERS_BROWSER_COMPARISON_PROMPT_PRIMARY_BUTTON_CLICKED
import com.duckduckgo.daxprompts.impl.pixels.DaxPromptBrowserComparisonPixelName.REACTIVATE_USERS_BROWSER_COMPARISON_PROMPT_SHOWN
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding

enum class DaxPromptBrowserComparisonPixelName(override val pixelName: String) : Pixel.PixelName {
    REACTIVATE_USERS_BROWSER_COMPARISON_PROMPT_SHOWN("m_reactivate_users_browser_comparison_prompt_shown"),
    REACTIVATE_USERS_BROWSER_COMPARISON_PROMPT_CLOSED("m_reactivate_users_browser_comparison_prompt_closed"),
    REACTIVATE_USERS_BROWSER_COMPARISON_PROMPT_PRIMARY_BUTTON_CLICKED("m_reactivate_users_browser_comparison_prompt_primary_button_clicked"),
    REACTIVATE_USERS_BROWSER_COMPARISON_PROMPT_DEFAULT_BROWSER_SET("m_reactivate_users_browser_comparison_prompt_default_browser_set"),
}

object DaxPromptBrowserComparisonPixelParameter {
    const val PARAM_NAME_INTERACTION_TYPE = "interaction_type"

    const val PARAM_VALUE_X_BUTTON_TAPPED = "x_button_tapped"
    const val PARAM_VALUE_MAYBE_LATER_BUTTON_TAPPED = "maybe_later_button_tapped"
    const val PARAM_VALUE_NAVIGATION_BUTTON_OR_GESTURE_USED = "navigation_button_or_gesture_used"
    const val PARAM_VALUE_SYSTEM_DIALOG_DISMISSED = "system_dialog_dismissed"
}

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = PixelParamRemovalPlugin::class,
)
object DaxPromptBrowserComparisonPixelParamRemoval : PixelParamRemovalPlugin {
    override fun names(): List<Pair<String, Set<PixelParameter>>> {
        return listOf(
            REACTIVATE_USERS_BROWSER_COMPARISON_PROMPT_SHOWN.pixelName to PixelParameter.removeAll(),
            REACTIVATE_USERS_BROWSER_COMPARISON_PROMPT_CLOSED.pixelName to PixelParameter.removeAll(),
            REACTIVATE_USERS_BROWSER_COMPARISON_PROMPT_PRIMARY_BUTTON_CLICKED.pixelName to PixelParameter.removeAll(),
            REACTIVATE_USERS_BROWSER_COMPARISON_PROMPT_DEFAULT_BROWSER_SET.pixelName to PixelParameter.removeAll(),
        )
    }
}
