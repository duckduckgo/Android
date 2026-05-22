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

package com.duckduckgo.browser.ui.newtab.hatch

import com.duckduckgo.app.statistics.pixels.Pixel

enum class NewTabReturnHatchPixelName(override val pixelName: String) : Pixel.PixelName {
    OPTION_SELECTED_BURN_TAB("m_ntp_after_idle_escape_hatch_burn_with_confirmation_tapped"),
    OPTION_SELECTED_BURN_TAB_DAILY("m_ntp_after_idle_escape_hatch_burn_with_confirmation_tapped_daily"),
    OPTION_SELECTED_CLOSE_TAB("m_ntp_after_idle_escape_hatch_close_tab_tapped"),
    OPTION_SELECTED_CLOSE_TAB_DAILY("m_ntp_after_idle_escape_hatch_close_tab_tapped_daily"),
    OPTION_SELECTED_RETURN_TAB("m_ntp_after_idle_escape_hatch_return_tapped"),
    OPTION_SELECTED_RETURN_TAB_DAILY("m_ntp_after_idle_escape_hatch_return_tapped_daily"),
    OPTION_SELECTED_AFTER_INACTIVITY("m_ntp_after_escape_hatch_after_inactivity_tapped"),
    OPTION_SELECTED_AFTER_INACTIVITY_DAILY("m_ntp_after_escape_hatch_after_inactivity_tapped_daily"),
}
