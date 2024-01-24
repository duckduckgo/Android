/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.privacyprotectionspopup.impl

import com.duckduckgo.app.statistics.pixels.Pixel.PixelName
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.COUNT
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.DAILY
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.UNIQUE

enum class PrivacyProtectionsPopupPixelName(
    override val pixelName: String,
    val type: PixelType,
) : PixelName {
    EXPERIMENT_VARIANT_ASSIGNED(
        pixelName = "m_privacy_protections_popup_experiment_variant_assigned_u",
        type = UNIQUE,
    ),
    POPUP_TRIGGERED(
        pixelName = "m_privacy_protections_popup_triggered_c",
        type = COUNT,
    ),
    PROTECTIONS_DISABLED(
        pixelName = "m_privacy_protections_popup_protections_disabled_c",
        type = COUNT,
    ),
    PROTECTIONS_DISABLED_UNIQUE(
        pixelName = "m_privacy_protections_popup_protections_disabled_u",
        type = UNIQUE,
    ),
    PRIVACY_DASHBOARD_OPENED(
        pixelName = "m_privacy_protections_popup_dashboard_opened_c",
        type = COUNT,
    ),
    PRIVACY_DASHBOARD_OPENED_UNIQUE(
        pixelName = "m_privacy_protections_popup_dashboard_opened_u",
        type = UNIQUE,
    ),
    POPUP_DISMISSED_VIA_BUTTON(
        pixelName = "m_privacy_protections_popup_dismissed_via_button_c",
        type = COUNT,
    ),
    POPUP_DISMISSED_VIA_CLICK_OUTSIDE(
        pixelName = "m_privacy_protections_popup_dismissed_via_click_outside_c",
        type = COUNT,
    ),
    DO_NOT_SHOW_AGAIN_CLICKED(
        pixelName = "m_privacy_protections_popup_do_not_show_again_clicked_u",
        type = UNIQUE,
    ),
    PAGE_REFRESH_ON_POSSIBLE_BREAKAGE(
        pixelName = "m_privacy_protections_popup_page_refresh_on_possible_breakage_c",
        type = COUNT,
    ),
    PAGE_REFRESH_ON_POSSIBLE_BREAKAGE_DAILY(
        pixelName = "m_privacy_protections_popup_page_refresh_on_possible_breakage_d",
        type = DAILY,
    ),
    ;

    object Params {
        const val PARAM_POPUP_TRIGGER_COUNT = "privacy_protections_popup_trigger_count"
    }
}
