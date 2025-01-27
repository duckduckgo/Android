/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.privacy.dashboard.impl.pixels

import com.duckduckgo.app.statistics.pixels.Pixel

enum class PrivacyDashboardPixels(override val pixelName: String, val enqueue: Boolean = false) : Pixel.PixelName {
    PRIVACY_DASHBOARD_OPENED("mp"),
    PRIVACY_DASHBOARD_ALLOWLIST_ADD("mp_wla"),
    PRIVACY_DASHBOARD_ALLOWLIST_REMOVE("mp_wlr"),
    PRIVACY_DASHBOARD_FIRST_TIME_OPENED("m_privacy_dashboard_first_time_used"),
    BROKEN_SITE_ALLOWLIST_ADD("m_broken_site_allowlist_add"),
    BROKEN_SITE_ALLOWLIST_REMOVE("m_broken_site_allowlist_remove"),
    REPORT_BROKEN_SITE_SHOWN("m_report-broken-site_shown"),
    REPORT_BROKEN_SITE_SENT("m_report-broken-site_sent"),
}

enum class PrivacyDashboardCustomTabPixelNames(override val pixelName: String) : Pixel.PixelName {
    CUSTOM_TABS_PRIVACY_DASHBOARD_ALLOW_LIST_ADD("m_custom_tabs_privacy_dashboard_allow_list_add"),
    CUSTOM_TABS_PRIVACY_DASHBOARD_ALLOW_LIST_REMOVE("m_custom_tabs_privacy_dashboard_allow_list_remove"),
}
