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

package com.duckduckgo.autoconsent.impl.pixels

import com.duckduckgo.app.statistics.pixels.Pixel

enum class AutoConsentPixel(override val pixelName: String) : Pixel.PixelName {

    SETTINGS_AUTOCONSENT_SHOWN("m_settings_autoconsent_shown"),
    SETTINGS_AUTOCONSENT_ON("m_settings_autoconsent_on"),
    SETTINGS_AUTOCONSENT_OFF("m_settings_autoconsent_off"),

    AUTOCONSENT_INIT_DAILY("m_autoconsent_init_android_daily"),
    AUTOCONSENT_DISABLED_FOR_SITE_DAILY("m_autoconsent_disabled-for-site_android_daily"),
    AUTOCONSENT_POPUP_FOUND_DAILY("m_autoconsent_popup-found_android_daily"),
    AUTOCONSENT_ERROR_OPTOUT_DAILY("m_autoconsent_error_optout_android_daily"),
    AUTOCONSENT_DONE_DAILY("m_autoconsent_done_android_daily"),
    AUTOCONSENT_DONE_COSMETIC_DAILY("m_autoconsent_done_cosmetic_android_daily"),
    AUTOCONSENT_ANIMATION_SHOWN_DAILY("m_autoconsent_animation-shown_android_daily"),
    AUTOCONSENT_ANIMATION_SHOWN_COSMETIC_DAILY("m_autoconsent_animation-shown_cosmetic_android_daily"),
    AUTOCONSENT_SELF_TEST_OK_DAILY("m_autoconsent_self-test-ok_android_daily"),
    AUTOCONSENT_SELF_TEST_FAIL_DAILY("m_autoconsent_self-test-fail_android_daily"),
    AUTOCONSENT_ERROR_MULTIPLE_POPUPS_DAILY("m_autoconsent_error_multiple-popups_android_daily"),
    AUTOCONSENT_DETECTED_BY_PATTERNS_DAILY("m_autoconsent_detected-by-patterns_android_daily"),
    AUTOCONSENT_DETECTED_BY_BOTH_DAILY("m_autoconsent_detected-by-both_android_daily"),
    AUTOCONSENT_DETECTED_ONLY_RULES_DAILY("m_autoconsent_detected-only-rules_android_daily"),
    AUTOCONSENT_SUMMARY("m_autoconsent_summary_android"),
}
