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
import com.duckduckgo.duckchat.impl.ReportMetric.USER_DID_TAP_KEYBOARD_RETURN_KEY
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_ADDRESS_BAR_IS_ENABLED_DAILY
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_BROWSER_MENU_IS_ENABLED_DAILY
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_ADDRESS_BAR_IS_ENABLED_DAILY
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_ADDRESS_BAR_SETTING_OFF
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_ADDRESS_BAR_SETTING_ON
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_LEGACY_OMNIBAR_AICHAT_BUTTON_PRESSED
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_LEGACY_OMNIBAR_AICHAT_BUTTON_PRESSED_DAILY
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_LEGACY_OMNIBAR_BACK_BUTTON_PRESSED
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_LEGACY_OMNIBAR_QUERY_SUBMITTED
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_LEGACY_OMNIBAR_QUERY_SUBMITTED_DAILY
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_LEGACY_OMNIBAR_SHOWN_COUNT
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_LEGACY_OMNIBAR_SHOWN_DAILY
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_BACK_BUTTON_PRESSED
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_CLEAR_BUTTON_PRESSED
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_DAILY_RETENTION
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FIRST_ENABLED
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FIRST_INTERACTION
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FIRST_PROMPT_SUBMISSION
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FIRST_SEARCH_SUBMISSION
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FIRST_SETTINGS_VIEWED
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FLOATING_RETURN_PRESSED
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FLOATING_SUBMIT_PRESSED
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FULL_CONVERSION_USER
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_KEYBOARD_GO_PRESSED
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_MODE_SWITCHED
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_PROMPT_SUBMITTED
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_PROMPT_SUBMITTED_DAILY
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_QUERY_SUBMITTED
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_QUERY_SUBMITTED_DAILY
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_SESSION_BOTH_MODES
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_SESSION_BOTH_MODES_DAILY
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_SESSION_SUMMARY
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_SHOWN_COUNT
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_SHOWN_DAILY
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_TEXT_AREA_FOCUSED
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_IS_ENABLED_DAILY
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_KEYBOARD_RETURN_PRESSED
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_MENU_SETTING_OFF
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_MENU_SETTING_ON
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_NEW_ADDRESS_BAR_PICKER_CANCELLED
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_NEW_ADDRESS_BAR_PICKER_CONFIRMED
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_NEW_ADDRESS_BAR_PICKER_DISPLAYED
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_NEW_ADDRESS_BAR_PICKER_NOT_NOW
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

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
            val sessionParams = mapOf(
                DuckChatPixelParameters.DELTA_TIMESTAMP_PARAMETERS to duckChatFeatureRepository.sessionDeltaInMinutes().toString(),
            )
            val (pixelName, params) = when (reportMetric) {
                USER_DID_SUBMIT_PROMPT -> DUCK_CHAT_SEND_PROMPT_ONGOING_CHAT to sessionParams
                USER_DID_SUBMIT_FIRST_PROMPT -> DUCK_CHAT_START_NEW_CONVERSATION to sessionParams
                USER_DID_OPEN_HISTORY -> DUCK_CHAT_OPEN_HISTORY to sessionParams
                USER_DID_SELECT_FIRST_HISTORY_ITEM -> DUCK_CHAT_OPEN_MOST_RECENT_HISTORY_CHAT to sessionParams
                USER_DID_CREATE_NEW_CHAT -> DUCK_CHAT_START_NEW_CONVERSATION_BUTTON_CLICKED to sessionParams
                USER_DID_TAP_KEYBOARD_RETURN_KEY -> DUCK_CHAT_KEYBOARD_RETURN_PRESSED to emptyMap()
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
    DUCK_CHAT_EXPERIMENTAL_OMNIBAR_SHOWN_DAILY("m_aichat_experimental_omnibar_shown"),
    DUCK_CHAT_EXPERIMENTAL_OMNIBAR_SHOWN_COUNT("m_aichat_experimental_omnibar_shown_count"),
    DUCK_CHAT_EXPERIMENTAL_OMNIBAR_PROMPT_SUBMITTED("m_aichat_experimental_omnibar_prompt_submitted_count"),
    DUCK_CHAT_EXPERIMENTAL_OMNIBAR_PROMPT_SUBMITTED_DAILY("m_aichat_experimental_omnibar_prompt_submitted_daily"),
    DUCK_CHAT_EXPERIMENTAL_OMNIBAR_QUERY_SUBMITTED("m_aichat_experimental_omnibar_query_submitted_count"),
    DUCK_CHAT_EXPERIMENTAL_OMNIBAR_QUERY_SUBMITTED_DAILY("m_aichat_experimental_omnibar_query_submitted_daily"),
    DUCK_CHAT_EXPERIMENTAL_OMNIBAR_MODE_SWITCHED("m_aichat_experimental_omnibar_mode_switched"),
    DUCK_CHAT_EXPERIMENTAL_OMNIBAR_SESSION_BOTH_MODES("m_aichat_experimental_omnibar_session_both_modes_count"),
    DUCK_CHAT_EXPERIMENTAL_OMNIBAR_SESSION_BOTH_MODES_DAILY("m_aichat_experimental_omnibar_session_both_modes_daily"),
    DUCK_CHAT_EXPERIMENTAL_OMNIBAR_SESSION_SUMMARY("m_aichat_experimental_omnibar_session_summary"),
    DUCK_CHAT_EXPERIMENTAL_LEGACY_OMNIBAR_SHOWN_DAILY("m_aichat_legacy_omnibar_shown"),
    DUCK_CHAT_EXPERIMENTAL_LEGACY_OMNIBAR_SHOWN_COUNT("m_aichat_legacy_omnibar_shown_count"),
    DUCK_CHAT_EXPERIMENTAL_LEGACY_OMNIBAR_QUERY_SUBMITTED("m_aichat_legacy_omnibar_query_submitted_count"),
    DUCK_CHAT_EXPERIMENTAL_LEGACY_OMNIBAR_QUERY_SUBMITTED_DAILY("m_aichat_legacy_omnibar_query_submitted_daily"),
    DUCK_CHAT_EXPERIMENTAL_LEGACY_OMNIBAR_AICHAT_BUTTON_PRESSED("m_aichat_legacy_omnibar_aichat_button_pressed_count"),
    DUCK_CHAT_EXPERIMENTAL_LEGACY_OMNIBAR_AICHAT_BUTTON_PRESSED_DAILY("m_aichat_legacy_omnibar_aichat_button_pressed_daily"),
    DUCK_CHAT_OPEN_AUTOCOMPLETE_EXPERIMENTAL("m_aichat_open_autocomplete_experimental"),
    DUCK_CHAT_OPEN_AUTOCOMPLETE_LEGACY("m_aichat_open_autocomplete_legacy"),
    DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FIRST_SETTINGS_VIEWED("m_aichat_experimental_omnibar_first_settings_viewed"),
    DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FIRST_ENABLED("m_aichat_experimental_omnibar_first_enabled"),
    DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FIRST_INTERACTION("m_aichat_experimental_omnibar_first_interaction"),
    DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FIRST_SEARCH_SUBMISSION("m_aichat_experimental_omnibar_first_search_submission"),
    DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FIRST_PROMPT_SUBMISSION("m_aichat_experimental_omnibar_first_prompt_submission"),
    DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FULL_CONVERSION_USER("m_aichat_experimental_omnibar_full_conversion_user"),
    DUCK_CHAT_EXPERIMENTAL_OMNIBAR_TEXT_AREA_FOCUSED("m_aichat_experimental_omnibar_text_area_focused"),
    DUCK_CHAT_EXPERIMENTAL_OMNIBAR_CLEAR_BUTTON_PRESSED("m_aichat_experimental_omnibar_clear_button_pressed"),
    DUCK_CHAT_EXPERIMENTAL_OMNIBAR_BACK_BUTTON_PRESSED("m_aichat_experimental_omnibar_back_button_pressed"),
    DUCK_CHAT_EXPERIMENTAL_OMNIBAR_KEYBOARD_GO_PRESSED("m_aichat_experimental_omnibar_keyboard_go_pressed"),
    DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FLOATING_SUBMIT_PRESSED("m_aichat_experimental_omnibar_floating_submit_pressed"),
    DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FLOATING_RETURN_PRESSED("m_aichat_experimental_omnibar_floating_return_pressed"),
    DUCK_CHAT_EXPERIMENTAL_LEGACY_OMNIBAR_BACK_BUTTON_PRESSED("m_aichat_legacy_omnibar_back_button_pressed"),
    DUCK_CHAT_KEYBOARD_RETURN_PRESSED("m_aichat_duckai_keyboard_return_pressed"),
    DUCK_CHAT_EXPERIMENTAL_OMNIBAR_DAILY_RETENTION("m_aichat_experimental_omnibar_daily_retention"),
    DUCK_CHAT_NEW_ADDRESS_BAR_PICKER_DISPLAYED("m_aichat_new_address_bar_picker_displayed"),
    DUCK_CHAT_NEW_ADDRESS_BAR_PICKER_CONFIRMED("m_aichat_new_address_bar_picker_confirmed"),
    DUCK_CHAT_NEW_ADDRESS_BAR_PICKER_NOT_NOW("m_aichat_new_address_bar_picker_not_now"),
    DUCK_CHAT_NEW_ADDRESS_BAR_PICKER_CANCELLED("m_aichat_new_address_bar_picker_cancelled"),
}

object DuckChatPixelParameters {
    const val WAS_USED_BEFORE = "was_used_before"
    const val DELTA_TIMESTAMP_PARAMETERS = "delta-timestamp-minutes"
    const val INPUT_SCREEN_MODE = "mode"
    const val TEXT_LENGTH_BUCKET = "text_length_bucket"
    const val NEW_ADDRESS_BAR_SELECTION = "selection"
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
            DUCK_CHAT_EXPERIMENTAL_OMNIBAR_SHOWN_DAILY.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_EXPERIMENTAL_OMNIBAR_SHOWN_COUNT.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_EXPERIMENTAL_OMNIBAR_PROMPT_SUBMITTED.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_EXPERIMENTAL_OMNIBAR_PROMPT_SUBMITTED_DAILY.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_EXPERIMENTAL_OMNIBAR_QUERY_SUBMITTED.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_EXPERIMENTAL_OMNIBAR_QUERY_SUBMITTED_DAILY.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_EXPERIMENTAL_OMNIBAR_MODE_SWITCHED.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_EXPERIMENTAL_OMNIBAR_SESSION_BOTH_MODES.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_EXPERIMENTAL_OMNIBAR_SESSION_BOTH_MODES_DAILY.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_EXPERIMENTAL_OMNIBAR_SESSION_SUMMARY.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_EXPERIMENTAL_LEGACY_OMNIBAR_SHOWN_DAILY.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_EXPERIMENTAL_LEGACY_OMNIBAR_SHOWN_COUNT.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_EXPERIMENTAL_LEGACY_OMNIBAR_QUERY_SUBMITTED.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_EXPERIMENTAL_LEGACY_OMNIBAR_QUERY_SUBMITTED_DAILY.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_EXPERIMENTAL_LEGACY_OMNIBAR_AICHAT_BUTTON_PRESSED.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_EXPERIMENTAL_LEGACY_OMNIBAR_AICHAT_BUTTON_PRESSED_DAILY.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FIRST_SETTINGS_VIEWED.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FIRST_ENABLED.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FIRST_INTERACTION.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FIRST_SEARCH_SUBMISSION.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FIRST_PROMPT_SUBMISSION.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FULL_CONVERSION_USER.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_EXPERIMENTAL_OMNIBAR_TEXT_AREA_FOCUSED.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_EXPERIMENTAL_OMNIBAR_CLEAR_BUTTON_PRESSED.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_EXPERIMENTAL_OMNIBAR_BACK_BUTTON_PRESSED.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_EXPERIMENTAL_OMNIBAR_KEYBOARD_GO_PRESSED.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FLOATING_SUBMIT_PRESSED.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FLOATING_RETURN_PRESSED.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_EXPERIMENTAL_LEGACY_OMNIBAR_BACK_BUTTON_PRESSED.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_EXPERIMENTAL_OMNIBAR_DAILY_RETENTION.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_NEW_ADDRESS_BAR_PICKER_DISPLAYED.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_NEW_ADDRESS_BAR_PICKER_CONFIRMED.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_NEW_ADDRESS_BAR_PICKER_NOT_NOW.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_NEW_ADDRESS_BAR_PICKER_CANCELLED.pixelName to PixelParameter.removeAtb(),
        )
    }
}

internal fun inputScreenPixelsModeParam(isSearchMode: Boolean) = mapOf(
    DuckChatPixelParameters.INPUT_SCREEN_MODE to if (isSearchMode) {
        "search"
    } else {
        "aiChat"
    },
)
