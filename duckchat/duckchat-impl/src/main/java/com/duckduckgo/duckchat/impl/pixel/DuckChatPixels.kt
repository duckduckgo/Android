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

package com.duckduckgo.duckchat.impl.pixel

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.plugins.pixel.PixelParamRemovalPlugin
import com.duckduckgo.common.utils.plugins.pixel.PixelParamRemovalPlugin.PixelParameter
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.ReportMetric
import com.duckduckgo.duckchat.impl.ReportMetric.USER_DID_CREATE_NEW_CHAT
import com.duckduckgo.duckchat.impl.ReportMetric.USER_DID_OPEN_HISTORY
import com.duckduckgo.duckchat.impl.ReportMetric.USER_DID_SELECT_FIRST_HISTORY_ITEM
import com.duckduckgo.duckchat.impl.ReportMetric.USER_DID_SUBMIT_FIRST_PROMPT
import com.duckduckgo.duckchat.impl.ReportMetric.USER_DID_SUBMIT_PROMPT
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_ADDRESS_BAR_IS_ENABLED_DAILY
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_BROWSER_MENU_IS_ENABLED_DAILY
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_ADDRESS_BAR_IS_ENABLED_DAILY
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_ADDRESS_BAR_SETTING_OFF
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_ADDRESS_BAR_SETTING_ON
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_LEGACY_OMNIBAR_AICHAT_BUTTON_PRESSED
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_LEGACY_OMNIBAR_AICHAT_BUTTON_PRESSED_DAILY
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_LEGACY_OMNIBAR_QUERY_SUBMITTED
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_LEGACY_OMNIBAR_QUERY_SUBMITTED_DAILY
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_LEGACY_OMNIBAR_SHOWN
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_MODE_SWITCHED
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_PROMPT_SUBMITTED
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_PROMPT_SUBMITTED_DAILY
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_QUERY_SUBMITTED
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_QUERY_SUBMITTED_DAILY
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_SESSION_BOTH_MODES
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_SESSION_BOTH_MODES_DAILY
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_SESSION_SUMMARY
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_SHOWN
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_IS_ENABLED_DAILY
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_MENU_SETTING_OFF
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_MENU_SETTING_ON
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_OPEN
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_OPEN_BROWSER_MENU
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_OPEN_HISTORY
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_OPEN_MOST_RECENT_HISTORY_CHAT
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_OPEN_NEW_TAB_MENU
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_OPEN_TAB_SWITCHER_FAB
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_PAID_OPEN_DUCK_AI_CLICKED
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_PAID_SETTINGS_OPENED
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_SEARCHBAR_BUTTON_OPEN
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_SEARCHBAR_SETTING_OFF
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_SEARCHBAR_SETTING_ON
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_SEARCH_ASSIST_SETTINGS_BUTTON_CLICKED
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_SEND_PROMPT_ONGOING_CHAT
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_SETTINGS_DISPLAYED
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_SETTINGS_PRESSED
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_START_NEW_CONVERSATION
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_START_NEW_CONVERSATION_BUTTON_CLICKED
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_USER_DISABLED
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_USER_ENABLED
import com.duckduckgo.duckchat.impl.repository.DuckChatFeatureRepository
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface DuckChatPixels {
    fun sendReportMetricPixel(reportMetric: ReportMetric)
}

@ContributesBinding(AppScope::class)
class RealDuckChatPixels @Inject constructor(
    private val pixel: Pixel,
    private val duckChatFeatureRepository: DuckChatFeatureRepository,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,

) : DuckChatPixels {

    override fun sendReportMetricPixel(reportMetric: ReportMetric) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            val params = mapOf(DuckChatPixelParameters.DELTA_TIMESTAMP_PARAMETERS to duckChatFeatureRepository.sessionDeltaInMinutes().toString())
            val pixelName = when (reportMetric) {
                USER_DID_SUBMIT_PROMPT -> DUCK_CHAT_SEND_PROMPT_ONGOING_CHAT
                USER_DID_SUBMIT_FIRST_PROMPT -> DUCK_CHAT_START_NEW_CONVERSATION
                USER_DID_OPEN_HISTORY -> DUCK_CHAT_OPEN_HISTORY
                USER_DID_SELECT_FIRST_HISTORY_ITEM -> DUCK_CHAT_OPEN_MOST_RECENT_HISTORY_CHAT
                USER_DID_CREATE_NEW_CHAT -> DUCK_CHAT_START_NEW_CONVERSATION_BUTTON_CLICKED
            }

            withContext(dispatcherProvider.main()) {
                pixel.fire(pixelName, parameters = params)
            }
        }
    }
}

enum class DuckChatPixelName(override val pixelName: String) : Pixel.PixelName {
    DUCK_CHAT_OPEN("aichat_open"),
    DUCK_CHAT_OPEN_BROWSER_MENU("aichat_open_browser_menu"),
    DUCK_CHAT_OPEN_NEW_TAB_MENU("aichat_open_new_tab_menu"),
    DUCK_CHAT_OPEN_TAB_SWITCHER_FAB("aichat_open_tab_switcher_fab"),
    DUCK_CHAT_USER_ENABLED("aichat_enabled"),
    DUCK_CHAT_USER_DISABLED("aichat_disabled"),
    DUCK_CHAT_MENU_SETTING_OFF("aichat_menu_setting_off"),
    DUCK_CHAT_MENU_SETTING_ON("aichat_menu_setting_on"),
    DUCK_CHAT_SEARCHBAR_SETTING_OFF("aichat_searchbar_setting_off"),
    DUCK_CHAT_SEARCHBAR_SETTING_ON("aichat_searchbar_setting_on"),
    DUCK_CHAT_EXPERIMENTAL_ADDRESS_BAR_SETTING_OFF("aichat_experimental_address_bar_setting_off"),
    DUCK_CHAT_EXPERIMENTAL_ADDRESS_BAR_SETTING_ON("aichat_experimental_address_bar_setting_on"),
    DUCK_CHAT_SETTINGS_PRESSED("settings_aichat_pressed"),
    DUCK_CHAT_SETTINGS_DISPLAYED("m_aichat_settings_displayed"),
    DUCK_CHAT_SEARCHBAR_BUTTON_OPEN("aichat_searchbar_button_open"),
    DUCK_CHAT_IS_ENABLED_DAILY("aichat_is_enabled_daily"),
    DUCK_CHAT_BROWSER_MENU_IS_ENABLED_DAILY("aichat_browser_menu_is_enabled_daily"),
    DUCK_CHAT_ADDRESS_BAR_IS_ENABLED_DAILY("aichat_address_bar_is_enabled_daily"),
    DUCK_CHAT_EXPERIMENTAL_ADDRESS_BAR_IS_ENABLED_DAILY("aichat_experimental_address_bar_is_enabled_daily"),
    DUCK_CHAT_SEARCH_ASSIST_SETTINGS_BUTTON_CLICKED("aichat_search_assist_settings_button_clicked"),
    DUCK_CHAT_START_NEW_CONVERSATION("aichat_start_new_conversation"),
    DUCK_CHAT_START_NEW_CONVERSATION_BUTTON_CLICKED("aichat_start_new_conversation_button_clicked"),
    DUCK_CHAT_OPEN_HISTORY("aichat_open_history"),
    DUCK_CHAT_OPEN_MOST_RECENT_HISTORY_CHAT("aichat_open_most_recent_history_chat"),
    DUCK_CHAT_SEND_PROMPT_ONGOING_CHAT("aichat_sent_prompt_ongoing_chat"),
    DUCK_CHAT_PAID_OPEN_DUCK_AI_CLICKED("m_privacy-pro_settings_paid-ai-chat_click"),
    DUCK_CHAT_PAID_SETTINGS_OPENED("m_privacy-pro_settings_paid-ai-chat_impression"),
    DUCK_CHAT_EXPERIMENTAL_OMNIBAR_SHOWN("m_aichat_experimental_omnibar_shown"),
    DUCK_CHAT_EXPERIMENTAL_OMNIBAR_PROMPT_SUBMITTED("m_aichat_experimental_omnibar_prompt_submitted_count"),
    DUCK_CHAT_EXPERIMENTAL_OMNIBAR_PROMPT_SUBMITTED_DAILY("m_aichat_experimental_omnibar_prompt_submitted_daily"),
    DUCK_CHAT_EXPERIMENTAL_OMNIBAR_QUERY_SUBMITTED("m_aichat_experimental_omnibar_query_submitted_count"),
    DUCK_CHAT_EXPERIMENTAL_OMNIBAR_QUERY_SUBMITTED_DAILY("m_aichat_experimental_omnibar_query_submitted_daily"),
    DUCK_CHAT_EXPERIMENTAL_OMNIBAR_MODE_SWITCHED("m_aichat_experimental_omnibar_mode_switched"),
    DUCK_CHAT_EXPERIMENTAL_OMNIBAR_SESSION_BOTH_MODES("m_aichat_experimental_omnibar_session_both_modes_count"),
    DUCK_CHAT_EXPERIMENTAL_OMNIBAR_SESSION_BOTH_MODES_DAILY("m_aichat_experimental_omnibar_session_both_modes_daily"),
    DUCK_CHAT_EXPERIMENTAL_OMNIBAR_SESSION_SUMMARY("m_aichat_experimental_omnibar_session_summary"),
    DUCK_CHAT_EXPERIMENTAL_LEGACY_OMNIBAR_SHOWN("m_aichat_legacy_omnibar_shown"),
    DUCK_CHAT_EXPERIMENTAL_LEGACY_OMNIBAR_QUERY_SUBMITTED("m_aichat_legacy_omnibar_query_submitted_count"),
    DUCK_CHAT_EXPERIMENTAL_LEGACY_OMNIBAR_QUERY_SUBMITTED_DAILY("m_aichat_legacy_omnibar_query_submitted_daily"),
    DUCK_CHAT_EXPERIMENTAL_LEGACY_OMNIBAR_AICHAT_BUTTON_PRESSED("m_aichat_legacy_omnibar_aichat_button_pressed_count"),
    DUCK_CHAT_EXPERIMENTAL_LEGACY_OMNIBAR_AICHAT_BUTTON_PRESSED_DAILY("m_aichat_legacy_omnibar_aichat_button_pressed_daily"),
}

object DuckChatPixelParameters {
    const val WAS_USED_BEFORE = "was_used_before"
    const val DELTA_TIMESTAMP_PARAMETERS = "delta-timestamp-minutes"
}

@ContributesMultibinding(AppScope::class)
class DuckChatParamRemovalPlugin @Inject constructor() : PixelParamRemovalPlugin {
    override fun names(): List<Pair<String, Set<PixelParameter>>> {
        return listOf(
            DUCK_CHAT_OPEN.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_OPEN_BROWSER_MENU.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_OPEN_NEW_TAB_MENU.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_OPEN_TAB_SWITCHER_FAB.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_USER_ENABLED.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_USER_DISABLED.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_MENU_SETTING_OFF.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_MENU_SETTING_ON.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_SEARCHBAR_SETTING_OFF.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_SEARCHBAR_SETTING_ON.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_EXPERIMENTAL_ADDRESS_BAR_SETTING_OFF.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_EXPERIMENTAL_ADDRESS_BAR_SETTING_ON.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_SETTINGS_PRESSED.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_SETTINGS_DISPLAYED.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_SEARCHBAR_BUTTON_OPEN.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_IS_ENABLED_DAILY.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_BROWSER_MENU_IS_ENABLED_DAILY.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_ADDRESS_BAR_IS_ENABLED_DAILY.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_EXPERIMENTAL_ADDRESS_BAR_IS_ENABLED_DAILY.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_SEARCH_ASSIST_SETTINGS_BUTTON_CLICKED.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_START_NEW_CONVERSATION.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_START_NEW_CONVERSATION_BUTTON_CLICKED.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_OPEN_HISTORY.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_OPEN_MOST_RECENT_HISTORY_CHAT.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_SEND_PROMPT_ONGOING_CHAT.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_PAID_OPEN_DUCK_AI_CLICKED.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_PAID_SETTINGS_OPENED.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_EXPERIMENTAL_OMNIBAR_SHOWN.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_EXPERIMENTAL_OMNIBAR_PROMPT_SUBMITTED.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_EXPERIMENTAL_OMNIBAR_PROMPT_SUBMITTED_DAILY.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_EXPERIMENTAL_OMNIBAR_QUERY_SUBMITTED.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_EXPERIMENTAL_OMNIBAR_QUERY_SUBMITTED_DAILY.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_EXPERIMENTAL_OMNIBAR_MODE_SWITCHED.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_EXPERIMENTAL_OMNIBAR_SESSION_BOTH_MODES.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_EXPERIMENTAL_OMNIBAR_SESSION_BOTH_MODES_DAILY.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_EXPERIMENTAL_OMNIBAR_SESSION_SUMMARY.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_EXPERIMENTAL_LEGACY_OMNIBAR_SHOWN.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_EXPERIMENTAL_LEGACY_OMNIBAR_QUERY_SUBMITTED.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_EXPERIMENTAL_LEGACY_OMNIBAR_QUERY_SUBMITTED_DAILY.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_EXPERIMENTAL_LEGACY_OMNIBAR_AICHAT_BUTTON_PRESSED.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_EXPERIMENTAL_LEGACY_OMNIBAR_AICHAT_BUTTON_PRESSED_DAILY.pixelName to PixelParameter.removeAtb(),
        )
    }
}
