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

package com.duckduckgo.adblocking.impl

import com.duckduckgo.app.statistics.pixels.Pixel

enum class AdBlockingPixelNames(override val pixelName: String) : Pixel.PixelName {
    AD_BLOCKING_SETTINGS_OPENED_DAILY("adBlocking_settings_opened_daily"),
    AD_BLOCKING_SETTINGS_OPENED_COUNT("adBlocking_settings_opened_count"),

    // Daily report of the current ad blocking state, fired at app launch.
    AD_BLOCKING_STATE_DAILY("adBlocking_state_daily"),

    // User enabled/disabled YouTube ad blocking in settings.
    AD_BLOCKING_ENABLED_DAILY("adBlocking_enabled_daily"),
    AD_BLOCKING_ENABLED_COUNT("adBlocking_enabled_count"),
    AD_BLOCKING_DISABLED_DAILY("adBlocking_disabled_daily"),
    AD_BLOCKING_DISABLED_COUNT("adBlocking_disabled_count"),

    // Scriptlet update lifecycle: fetch -> validation -> install.
    AD_BLOCKING_SCRIPTLETS_FETCH_ERROR_DAILY("adBlocking_scriptlets_fetch_error_daily"),
    AD_BLOCKING_SCRIPTLETS_FETCH_ERROR_COUNT("adBlocking_scriptlets_fetch_error_count"),
    AD_BLOCKING_SCRIPTLETS_VALIDATION_ERROR_DAILY("adBlocking_scriptlets_validation_error_daily"),
    AD_BLOCKING_SCRIPTLETS_VALIDATION_ERROR_COUNT("adBlocking_scriptlets_validation_error_count"),
    AD_BLOCKING_SCRIPTLETS_INSTALLED_DAILY("adBlocking_scriptlets_installed_daily"),
    AD_BLOCKING_SCRIPTLETS_INSTALLED_COUNT("adBlocking_scriptlets_installed_count"),
    AD_BLOCKING_SCRIPTLETS_INSTALL_ERROR_DAILY("adBlocking_scriptlets_install_error_daily"),
    AD_BLOCKING_SCRIPTLETS_INSTALL_ERROR_COUNT("adBlocking_scriptlets_install_error_count"),
    AD_BLOCKING_MENU_ENABLE_TAPPED_DAILY("adBlocking_menu_enable_tapped_daily"),
    AD_BLOCKING_MENU_ENABLE_TAPPED_COUNT("adBlocking_menu_enable_tapped_count"),
    AD_BLOCKING_MENU_DISABLE_TAPPED_DAILY("adBlocking_menu_disable_tapped_daily"),
    AD_BLOCKING_MENU_DISABLE_TAPPED_COUNT("adBlocking_menu_disable_tapped_count"),
    AD_BLOCKING_PICKER_ALWAYS_ON_DAILY("adBlocking_picker_always_on_daily"),
    AD_BLOCKING_PICKER_ALWAYS_ON_COUNT("adBlocking_picker_always_on_count"),
    AD_BLOCKING_PICKER_ALWAYS_OFF_DAILY("adBlocking_picker_always_off_daily"),
    AD_BLOCKING_PICKER_ALWAYS_OFF_COUNT("adBlocking_picker_always_off_count"),
    AD_BLOCKING_PICKER_DISABLE_UNTIL_RELAUNCH_DAILY("adBlocking_picker_disable_until_relaunch_daily"),
    AD_BLOCKING_PICKER_DISABLE_UNTIL_RELAUNCH_COUNT("adBlocking_picker_disable_until_relaunch_count"),
    AD_BLOCKING_BREAKAGE_REPORT_ENTERED_DAILY("adBlocking_breakage_report_entered_daily"),
    AD_BLOCKING_BREAKAGE_REPORT_ENTERED_COUNT("adBlocking_breakage_report_entered_count"),
}
