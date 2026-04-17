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

package com.duckduckgo.newtabpage.impl.pixels

import com.duckduckgo.app.statistics.pixels.Pixel

enum class NtpAfterIdlePixelName(override val pixelName: String) : Pixel.PixelName {
    // NTP shown pixels
    NTP_SHOWN_AFTER_IDLE("m_ntp_after_idle_ntp_shown_after_idle"),
    NTP_SHOWN_AFTER_IDLE_DAILY("m_ntp_after_idle_ntp_shown_after_idle_daily"),
    NTP_SHOWN_USER_INITIATED("m_ntp_after_idle_ntp_shown_user_initiated"),
    NTP_SHOWN_USER_INITIATED_DAILY("m_ntp_after_idle_ntp_shown_user_initiated_daily"),

    // Return to page (hatch) tapped pixels
    RETURN_TO_PAGE_TAPPED_AFTER_IDLE("m_ntp_after_idle_return_to_page_tapped_after_idle"),
    RETURN_TO_PAGE_TAPPED_AFTER_IDLE_DAILY("m_ntp_after_idle_return_to_page_tapped_after_idle_daily"),
    RETURN_TO_PAGE_TAPPED_USER_INITIATED("m_ntp_after_idle_return_to_page_tapped_user_initiated"),
    RETURN_TO_PAGE_TAPPED_USER_INITIATED_DAILY("m_ntp_after_idle_return_to_page_tapped_user_initiated_daily"),

    // Bar (search) used from NTP pixels
    BAR_USED_FROM_NTP_AFTER_IDLE("m_ntp_after_idle_bar_used_from_ntp_after_idle"),
    BAR_USED_FROM_NTP_AFTER_IDLE_DAILY("m_ntp_after_idle_bar_used_from_ntp_after_idle_daily"),
    BAR_USED_FROM_NTP_USER_INITIATED("m_ntp_after_idle_bar_used_from_ntp_user_initiated"),
    BAR_USED_FROM_NTP_USER_INITIATED_DAILY("m_ntp_after_idle_bar_used_from_ntp_user_initiated_daily"),

    // Timeout selected pixels — one enum entry per supported timeout value
    TIMEOUT_SELECTED_ALWAYS("m_ntp_after_idle_timeout_selected_always"),
    TIMEOUT_SELECTED_ALWAYS_DAILY("m_ntp_after_idle_timeout_selected_daily_always"),
    TIMEOUT_SELECTED_60("m_ntp_after_idle_timeout_selected_60"),
    TIMEOUT_SELECTED_60_DAILY("m_ntp_after_idle_timeout_selected_daily_60"),
    TIMEOUT_SELECTED_300("m_ntp_after_idle_timeout_selected_300"),
    TIMEOUT_SELECTED_300_DAILY("m_ntp_after_idle_timeout_selected_daily_300"),
    TIMEOUT_SELECTED_600("m_ntp_after_idle_timeout_selected_600"),
    TIMEOUT_SELECTED_600_DAILY("m_ntp_after_idle_timeout_selected_daily_600"),
    TIMEOUT_SELECTED_1800("m_ntp_after_idle_timeout_selected_1800"),
    TIMEOUT_SELECTED_1800_DAILY("m_ntp_after_idle_timeout_selected_daily_1800"),
    TIMEOUT_SELECTED_3600("m_ntp_after_idle_timeout_selected_3600"),
    TIMEOUT_SELECTED_3600_DAILY("m_ntp_after_idle_timeout_selected_daily_3600"),
    TIMEOUT_SELECTED_43200("m_ntp_after_idle_timeout_selected_43200"),
    TIMEOUT_SELECTED_43200_DAILY("m_ntp_after_idle_timeout_selected_daily_43200"),
    TIMEOUT_SELECTED_86400("m_ntp_after_idle_timeout_selected_86400"),
    TIMEOUT_SELECTED_86400_DAILY("m_ntp_after_idle_timeout_selected_daily_86400"),
}

object NtpAfterIdlePixels {
    /** Returns the count and daily pixel pair for the given timeout [seconds], or null if unknown. */
    fun timeoutPixelsForSeconds(seconds: Long): Pair<NtpAfterIdlePixelName, NtpAfterIdlePixelName>? = when (seconds) {
        0L -> Pair(NtpAfterIdlePixelName.TIMEOUT_SELECTED_ALWAYS, NtpAfterIdlePixelName.TIMEOUT_SELECTED_ALWAYS_DAILY)
        60L -> Pair(NtpAfterIdlePixelName.TIMEOUT_SELECTED_60, NtpAfterIdlePixelName.TIMEOUT_SELECTED_60_DAILY)
        300L -> Pair(NtpAfterIdlePixelName.TIMEOUT_SELECTED_300, NtpAfterIdlePixelName.TIMEOUT_SELECTED_300_DAILY)
        600L -> Pair(NtpAfterIdlePixelName.TIMEOUT_SELECTED_600, NtpAfterIdlePixelName.TIMEOUT_SELECTED_600_DAILY)
        1800L -> Pair(NtpAfterIdlePixelName.TIMEOUT_SELECTED_1800, NtpAfterIdlePixelName.TIMEOUT_SELECTED_1800_DAILY)
        3600L -> Pair(NtpAfterIdlePixelName.TIMEOUT_SELECTED_3600, NtpAfterIdlePixelName.TIMEOUT_SELECTED_3600_DAILY)
        43200L -> Pair(NtpAfterIdlePixelName.TIMEOUT_SELECTED_43200, NtpAfterIdlePixelName.TIMEOUT_SELECTED_43200_DAILY)
        86400L -> Pair(NtpAfterIdlePixelName.TIMEOUT_SELECTED_86400, NtpAfterIdlePixelName.TIMEOUT_SELECTED_86400_DAILY)
        else -> null
    }
}
