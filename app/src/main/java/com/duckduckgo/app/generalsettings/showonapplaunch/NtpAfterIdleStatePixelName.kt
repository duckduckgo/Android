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

package com.duckduckgo.app.generalsettings.showonapplaunch

import com.duckduckgo.app.statistics.pixels.Pixel

/**
 * Daily state  pixels for the NTP-after-idle settings.
 */
enum class NtpAfterIdleStatePixelName(override val pixelName: String) : Pixel.PixelName {
    RETURN_TO_LAST_TAB_ENABLED_DAILY("m_ntp_after_idle_return_to_last_tab_enabled_daily"),
    RETURN_TO_LAST_TAB_DISABLED_DAILY("m_ntp_after_idle_return_to_last_tab_disabled_daily"),
    IDLE_TIMEOUT_ALWAYS_DAILY("m_ntp_after_idle_idle_timeout_always_daily"),
    IDLE_TIMEOUT_60_DAILY("m_ntp_after_idle_idle_timeout_60_daily"),
    IDLE_TIMEOUT_300_DAILY("m_ntp_after_idle_idle_timeout_300_daily"),
    IDLE_TIMEOUT_600_DAILY("m_ntp_after_idle_idle_timeout_600_daily"),
    IDLE_TIMEOUT_1800_DAILY("m_ntp_after_idle_idle_timeout_1800_daily"),
    IDLE_TIMEOUT_3600_DAILY("m_ntp_after_idle_idle_timeout_3600_daily"),
    IDLE_TIMEOUT_43200_DAILY("m_ntp_after_idle_idle_timeout_43200_daily"),
    IDLE_TIMEOUT_86400_DAILY("m_ntp_after_idle_idle_timeout_86400_daily"),
}
