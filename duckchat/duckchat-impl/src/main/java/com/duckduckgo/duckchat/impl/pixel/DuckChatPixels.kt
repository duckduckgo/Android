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
import com.duckduckgo.app.statistics.api.StatisticsUpdater
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
import com.duckduckgo.duckchat.impl.metric.DuckAiMetricCollector
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_ADDRESS_BAR_IS_ENABLED_DAILY
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_BROWSER_MENU_IS_ENABLED_DAILY
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_ONBOARDING_CONFIRM_PRESSED
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_ONBOARDING_DISPLAYED
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_ONBOARDING_SETTINGS_PRESSED
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_DUCK_AI_SETTINGS_TAPPED
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
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_FIRE_BUTTON_TAPPED
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
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_SEARCH_ASSIST_SETTINGS_BUTTON_CLICKED
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_SEND_PROMPT_ONGOING_CHAT
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_SETTINGS_DISPLAYED
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_SETTINGS_MENU_OPEN
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_SETTINGS_PRESSED
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_START_NEW_CONVERSATION
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_START_NEW_CONVERSATION_BUTTON_CLICKED
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_TAB_SWITCHER_OPENED
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_USER_DISABLED
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_USER_ENABLED
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.PRODUCT_TELEMETRY_SURFACE_AUTOCOMPLETE_DISPLAYED
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.PRODUCT_TELEMETRY_SURFACE_AUTOCOMPLETE_DISPLAYED_DAILY
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.PRODUCT_TELEMETRY_SURFACE_DUCK_AI_OPEN
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.PRODUCT_TELEMETRY_SURFACE_DUCK_AI_OPEN_DAILY
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.PRODUCT_TELEMETRY_SURFACE_KEYBOARD_USAGE
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.PRODUCT_TELEMETRY_SURFACE_KEYBOARD_USAGE_DAILY
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.SERP_SETTINGS_OPEN_HIDE_AI_GENERATED_IMAGES
import com.duckduckgo.duckchat.impl.repository.DuckChatFeatureRepository
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface DuckChatPixels {
    fun sendReportMetricPixel(reportMetric: ReportMetric)
    fun reportOpen()
    fun reportContextualSheetOpened()
    fun reportContextualSheetDismissed()
    fun reportContextualSheetSessionRestored()
    fun reportContextualSheetExpanded()
    fun reportContextualSheetNewChat()
    fun reportContextualPageContextManuallyAttachedNative()
    fun reportContextualPageContextManuallyAttachedFrontend()
    fun reportContextualPageContextRemovedNative()
    fun reportContextualPageContextRemovedFrontend()
    fun reportContextualPageContextAutoAttached()
    fun reportContextualPromptSubmittedWithContextNative()
    fun reportContextualPromptSubmittedWithoutContextNative()
    fun reportContextualPageContextCollectionEmpty()
    fun reportContextualSettingAutomaticPageContentToggled(enabled: Boolean)
    fun reportContextualSummarizePromptSelected()
    fun reportContextualPlaceholderContextTapped()
    fun reportContextualPlaceholderContextShown()
}

@ContributesBinding(AppScope::class)
class RealDuckChatPixels @Inject constructor(
    private val pixel: Pixel,
    private val duckChatFeatureRepository: DuckChatFeatureRepository,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val statisticsUpdater: StatisticsUpdater,
    private val duckAiMetricCollector: DuckAiMetricCollector,
) : DuckChatPixels {

    override fun sendReportMetricPixel(reportMetric: ReportMetric) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            var refreshAtb = false
            val sessionParams = mapOf(
                DuckChatPixelParameters.DELTA_TIMESTAMP_PARAMETERS to duckChatFeatureRepository.sessionDeltaInMinutes().toString(),
            )
            val (pixelName, params) = when (reportMetric) {
                USER_DID_SUBMIT_PROMPT -> {
                    refreshAtb = true
                    DUCK_CHAT_SEND_PROMPT_ONGOING_CHAT to sessionParams
                }
                USER_DID_SUBMIT_FIRST_PROMPT -> {
                    refreshAtb = true
                    DUCK_CHAT_START_NEW_CONVERSATION to sessionParams
                }
                USER_DID_OPEN_HISTORY -> DUCK_CHAT_OPEN_HISTORY to sessionParams
                USER_DID_SELECT_FIRST_HISTORY_ITEM -> DUCK_CHAT_OPEN_MOST_RECENT_HISTORY_CHAT to sessionParams
                USER_DID_CREATE_NEW_CHAT -> DUCK_CHAT_START_NEW_CONVERSATION_BUTTON_CLICKED to sessionParams
                USER_DID_TAP_KEYBOARD_RETURN_KEY -> DUCK_CHAT_KEYBOARD_RETURN_PRESSED to emptyMap()
            }

            withContext(dispatcherProvider.main()) {
                pixel.fire(pixelName, parameters = params)
                if (refreshAtb) {
                    statisticsUpdater.refreshDuckAiRetentionAtb()
                    duckAiMetricCollector.onMessageSent()
                }
            }
        }
    }

    override fun reportOpen() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            duckChatFeatureRepository.registerOpened()
            val sessionDelta = duckChatFeatureRepository.sessionDeltaInMinutes()
            val params = mapOf(DuckChatPixelParameters.DELTA_TIMESTAMP_PARAMETERS to sessionDelta.toString())
            pixel.fire(DUCK_CHAT_OPEN, parameters = params)
            pixel.fire(PRODUCT_TELEMETRY_SURFACE_DUCK_AI_OPEN)
            pixel.fire(PRODUCT_TELEMETRY_SURFACE_DUCK_AI_OPEN_DAILY, type = Pixel.PixelType.Daily())
        }
    }

    override fun reportContextualSheetOpened() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_SHEET_OPENED_COUNT)
            pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_SHEET_OPENED_DAILY, type = Pixel.PixelType.Daily())
        }
    }

    override fun reportContextualSheetDismissed() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_SHEET_DISMISSED_COUNT)
            pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_SHEET_DISMISSED_DAILY, type = Pixel.PixelType.Daily())
        }
    }

    override fun reportContextualSheetSessionRestored() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_SESSION_RESTORED_COUNT)
            pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_SESSION_RESTORED_DAILY, type = Pixel.PixelType.Daily())
        }
    }

    override fun reportContextualSheetExpanded() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_EXPANDED_COUNT)
            pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_EXPANDED_DAILY, type = Pixel.PixelType.Daily())
        }
    }

    override fun reportContextualSheetNewChat() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_NEW_CHAT_COUNT)
            pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_NEW_CHAT_DAILY, type = Pixel.PixelType.Daily())
        }
    }

    override fun reportContextualPageContextManuallyAttachedNative() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_MANUALLY_ATTACHED_NATIVE_COUNT)
            pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_MANUALLY_ATTACHED_NATIVE_DAILY, type = Pixel.PixelType.Daily())
        }
    }

    override fun reportContextualPageContextManuallyAttachedFrontend() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_MANUALLY_ATTACHED_FRONTEND_COUNT)
            pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_MANUALLY_ATTACHED_FRONTEND_DAILY, type = Pixel.PixelType.Daily())
        }
    }

    override fun reportContextualPageContextRemovedNative() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_REMOVED_NATIVE_COUNT)
            pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_REMOVED_NATIVE_DAILY, type = Pixel.PixelType.Daily())
        }
    }

    override fun reportContextualPageContextRemovedFrontend() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_REMOVED_FRONTEND_COUNT)
            pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_REMOVED_FRONTEND_DAILY, type = Pixel.PixelType.Daily())
        }
    }

    override fun reportContextualPromptSubmittedWithContextNative() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PROMPT_SUBMITTED_WITH_CONTEXT_NATIVE_COUNT)
            pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PROMPT_SUBMITTED_WITH_CONTEXT_NATIVE_DAILY, type = Pixel.PixelType.Daily())
        }
    }

    override fun reportContextualPageContextAutoAttached() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_AUTO_ATTACHED_COUNT)
            pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_AUTO_ATTACHED_DAILY, type = Pixel.PixelType.Daily())
        }
    }

    override fun reportContextualPromptSubmittedWithoutContextNative() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PROMPT_SUBMITTED_WITHOUT_CONTEXT_NATIVE_COUNT)
            pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PROMPT_SUBMITTED_WITHOUT_CONTEXT_NATIVE_DAILY, type = Pixel.PixelType.Daily())
        }
    }

    override fun reportContextualPageContextCollectionEmpty() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_COLLECTION_EMPTY)
        }
    }

    override fun reportContextualSettingAutomaticPageContentToggled(checked: Boolean) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            if (checked) {
                pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_SETTING_AUTOMATIC_PAGE_CONTENT_ENABLED_COUNT)
                pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_SETTING_AUTOMATIC_PAGE_CONTENT_ENABLED_DAILY, type = Pixel.PixelType.Daily())
            } else {
                pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_SETTING_AUTOMATIC_PAGE_CONTENT_DISABLED_COUNT)
                pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_SETTING_AUTOMATIC_PAGE_CONTENT_DISABLED_DAILY, type = Pixel.PixelType.Daily())
            }
        }
    }

    override fun reportContextualSummarizePromptSelected() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_QUICK_ACTION_SUMMARISE_SELECTED_COUNT)
            pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_QUICK_ACTION_SUMMARISE_SELECTED_DAILY, type = Pixel.PixelType.Daily())
        }
    }

    override fun reportContextualPlaceholderContextTapped() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_PLACEHOLDER_TAPPED_COUNT)
            pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_PLACEHOLDER_TAPPED_DAILY, type = Pixel.PixelType.Daily())
        }
    }

    override fun reportContextualPlaceholderContextShown() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_PLACEHOLDER_SHOWN_COUNT)
            pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_PLACEHOLDER_SHOWN_DAILY, type = Pixel.PixelType.Daily())
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
    SERP_SETTINGS_OPEN_HIDE_AI_GENERATED_IMAGES("serp_settings_open_hide-ai-generated-images"),
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
    DUCK_CHAT_SETTINGS_NEW_CHAT_TAB_TAPPED("m_aichat_settings_menu_new_chat_tab_tapped"),
    DUCK_CHAT_OMNIBAR_NEW_CHAT_TAPPED("m_aichat_omnibar_new_chat_button_tapped"),
    DUCK_CHAT_OMNIBAR_SIDEBAR_TAPPED("m_aichat_omnibar_sidebar_button_tapped"),
    DUCK_CHAT_SETTINGS_SIDEBAR_TAPPED("m_aichat_settings_menu_sidebar_tapped"),
    DUCK_CHAT_SETTINGS_MENU_OPEN("m_aichat_settings_menu_opened"),
    DUCK_CHAT_DUCK_AI_SETTINGS_TAPPED("m_aichat_settings_menu_aichat_settings_tapped"),
    DUCK_CHAT_TAB_SWITCHER_OPENED("m_aichat_tab_switcher_opened"),
    DUCK_CHAT_FIRE_BUTTON_TAPPED("m_aichat_fire_button_tapped"),
    PRODUCT_TELEMETRY_SURFACE_AUTOCOMPLETE_DISPLAYED("m_product_telemetry_surface_usage_autocomplete"),
    PRODUCT_TELEMETRY_SURFACE_AUTOCOMPLETE_DISPLAYED_DAILY("m_product_telemetry_surface_usage_autocomplete_daily"),
    PRODUCT_TELEMETRY_SURFACE_DUCK_AI_OPEN("m_product_telemetry_surface_usage_duck_ai"),
    PRODUCT_TELEMETRY_SURFACE_DUCK_AI_OPEN_DAILY("m_product_telemetry_surface_usage_duck_ai_daily"),
    PRODUCT_TELEMETRY_SURFACE_KEYBOARD_USAGE("m_product_telemetry_surface_usage_keyboard_active"),
    PRODUCT_TELEMETRY_SURFACE_KEYBOARD_USAGE_DAILY("m_product_telemetry_surface_usage_keyboard_active_daily"),
    DUCK_CHAT_CONTEXTUAL_ONBOARDING_DISPLAYED("m_aichat_contextual_onboarding_displayed"),
    DUCK_CHAT_CONTEXTUAL_ONBOARDING_CONFIRM_PRESSED("m_aichat_contextual_onboarding_confirm_pressed"),
    DUCK_CHAT_CONTEXTUAL_ONBOARDING_SETTINGS_PRESSED("m_aichat_contextual_onboarding_settings_pressed"),
    DUCK_CHAT_CONTEXTUAL_SHEET_OPENED_COUNT("m_aichat_contextual_sheet_opened_count"),
    DUCK_CHAT_CONTEXTUAL_SHEET_OPENED_DAILY("m_aichat_contextual_sheet_opened_daily"),
    DUCK_CHAT_CONTEXTUAL_SHEET_DISMISSED_COUNT("m_aichat_contextual_sheet_dismissed_count"),
    DUCK_CHAT_CONTEXTUAL_SHEET_DISMISSED_DAILY("m_aichat_contextual_sheet_dismissed_daily"),
    DUCK_CHAT_CONTEXTUAL_SESSION_RESTORED_COUNT("m_aichat_contextual_session_restored_count"),
    DUCK_CHAT_CONTEXTUAL_SESSION_RESTORED_DAILY("m_aichat_contextual_session_restored_daily"),
    DUCK_CHAT_CONTEXTUAL_EXPANDED_COUNT("m_aichat_contextual_expand_button_tapped_count"),
    DUCK_CHAT_CONTEXTUAL_EXPANDED_DAILY("m_aichat_contextual_expand_button_tapped_daily"),
    DUCK_CHAT_CONTEXTUAL_NEW_CHAT_COUNT("m_aichat_contextual_new_chat_button_tapped_count"),
    DUCK_CHAT_CONTEXTUAL_NEW_CHAT_DAILY("m_aichat_contextual_new_chat_button_tapped_daily"),
    DUCK_CHAT_CONTEXTUAL_QUICK_ACTION_SUMMARISE_SELECTED_COUNT("m_aichat_contextual_quick_action_summarize_selected_count"),
    DUCK_CHAT_CONTEXTUAL_QUICK_ACTION_SUMMARISE_SELECTED_DAILY("m_aichat_contextual_quick_action_summarize_selected_daily"),
    DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_PLACEHOLDER_SHOWN_COUNT("m_aichat_contextual_page_context_placeholder_shown_count"),
    DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_PLACEHOLDER_SHOWN_DAILY("m_aichat_contextual_page_context_placeholder_shown_daily"),
    DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_PLACEHOLDER_TAPPED_COUNT("m_aichat_contextual_page_context_placeholder_tapped_count"),
    DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_PLACEHOLDER_TAPPED_DAILY("m_aichat_contextual_page_context_placeholder_tapped_daily"),
    DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_AUTO_ATTACHED_COUNT("m_aichat_contextual_page_context_auto_attached_count"),
    DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_AUTO_ATTACHED_DAILY("m_aichat_contextual_page_context_auto_attached_daily"),
    DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_MANUALLY_ATTACHED_NATIVE_COUNT("m_aichat_contextual_page_context_manually_attached_native_count"),
    DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_MANUALLY_ATTACHED_NATIVE_DAILY("m_aichat_contextual_page_context_manually_attached_native_daily"),
    DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_MANUALLY_ATTACHED_FRONTEND_COUNT("m_aichat_contextual_page_context_manually_attached_frontend_count"),
    DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_MANUALLY_ATTACHED_FRONTEND_DAILY("m_aichat_contextual_page_context_manually_attached_frontend_daily"),
    DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_REMOVED_NATIVE_COUNT("m_aichat_contextual_page_context_removed_native_count"),
    DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_REMOVED_NATIVE_DAILY("m_aichat_contextual_page_context_removed_native_daily"),
    DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_REMOVED_FRONTEND_COUNT("m_aichat_contextual_page_context_removed_frontend_count"),
    DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_REMOVED_FRONTEND_DAILY("m_aichat_contextual_page_context_removed_frontend_daily"),
    DUCK_CHAT_CONTEXTUAL_PROMPT_SUBMITTED_WITH_CONTEXT_NATIVE_COUNT("m_aichat_contextual_prompt_submitted_with_context_native_count"),
    DUCK_CHAT_CONTEXTUAL_PROMPT_SUBMITTED_WITH_CONTEXT_NATIVE_DAILY("m_aichat_contextual_prompt_submitted_with_context_native_daily"),
    DUCK_CHAT_CONTEXTUAL_PROMPT_SUBMITTED_WITHOUT_CONTEXT_NATIVE_COUNT("m_aichat_contextual_prompt_submitted_without_context_native_count"),
    DUCK_CHAT_CONTEXTUAL_PROMPT_SUBMITTED_WITHOUT_CONTEXT_NATIVE_DAILY("m_aichat_contextual_prompt_submitted_without_context_native_daily"),
    DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_COLLECTION_EMPTY("m_aichat_contextual_page_context_collection_empty"),
    DUCK_CHAT_CONTEXTUAL_SETTING_AUTOMATIC_PAGE_CONTENT_ENABLED_COUNT("m_aichat_settings_auto_context_enabled_count"),
    DUCK_CHAT_CONTEXTUAL_SETTING_AUTOMATIC_PAGE_CONTENT_ENABLED_DAILY("m_aichat_settings_auto_context_enabled_daily"),
    DUCK_CHAT_CONTEXTUAL_SETTING_AUTOMATIC_PAGE_CONTENT_DISABLED_COUNT("m_aichat_settings_auto_context_disabled_count"),
    DUCK_CHAT_CONTEXTUAL_SETTING_AUTOMATIC_PAGE_CONTENT_DISABLED_DAILY("m_aichat_settings_auto_context_disabled_daily"),
    DUCK_CHAT_CONTEXTUAL_SETTING_AUTOMATIC_PAGE_CONTENT_DAILY("m_aichat_settings_auto_context"),
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
            SERP_SETTINGS_OPEN_HIDE_AI_GENERATED_IMAGES.pixelName to PixelParameter.removeAtb(),
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
            DUCK_CHAT_SETTINGS_MENU_OPEN.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_DUCK_AI_SETTINGS_TAPPED.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_TAB_SWITCHER_OPENED.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_FIRE_BUTTON_TAPPED.pixelName to PixelParameter.removeAtb(),
            PRODUCT_TELEMETRY_SURFACE_AUTOCOMPLETE_DISPLAYED.pixelName to PixelParameter.removeAtb(),
            PRODUCT_TELEMETRY_SURFACE_AUTOCOMPLETE_DISPLAYED_DAILY.pixelName to PixelParameter.removeAtb(),
            PRODUCT_TELEMETRY_SURFACE_DUCK_AI_OPEN.pixelName to PixelParameter.removeAtb(),
            PRODUCT_TELEMETRY_SURFACE_DUCK_AI_OPEN_DAILY.pixelName to PixelParameter.removeAtb(),
            PRODUCT_TELEMETRY_SURFACE_KEYBOARD_USAGE.pixelName to PixelParameter.removeAtb(),
            PRODUCT_TELEMETRY_SURFACE_KEYBOARD_USAGE_DAILY.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_CONTEXTUAL_ONBOARDING_DISPLAYED.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_CONTEXTUAL_ONBOARDING_CONFIRM_PRESSED.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_CONTEXTUAL_ONBOARDING_SETTINGS_PRESSED.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_SHEET_OPENED_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_SHEET_OPENED_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_SHEET_DISMISSED_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_SHEET_DISMISSED_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_SESSION_RESTORED_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_SESSION_RESTORED_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_EXPANDED_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_EXPANDED_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_NEW_CHAT_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_NEW_CHAT_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_QUICK_ACTION_SUMMARISE_SELECTED_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_QUICK_ACTION_SUMMARISE_SELECTED_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_PLACEHOLDER_SHOWN_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_PLACEHOLDER_SHOWN_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_PLACEHOLDER_TAPPED_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_PLACEHOLDER_TAPPED_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_AUTO_ATTACHED_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_AUTO_ATTACHED_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_MANUALLY_ATTACHED_NATIVE_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_MANUALLY_ATTACHED_NATIVE_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_MANUALLY_ATTACHED_FRONTEND_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_MANUALLY_ATTACHED_FRONTEND_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_REMOVED_NATIVE_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_REMOVED_NATIVE_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_REMOVED_FRONTEND_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_REMOVED_FRONTEND_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PROMPT_SUBMITTED_WITH_CONTEXT_NATIVE_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PROMPT_SUBMITTED_WITH_CONTEXT_NATIVE_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PROMPT_SUBMITTED_WITHOUT_CONTEXT_NATIVE_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PROMPT_SUBMITTED_WITHOUT_CONTEXT_NATIVE_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_COLLECTION_EMPTY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_SETTING_AUTOMATIC_PAGE_CONTENT_ENABLED_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_SETTING_AUTOMATIC_PAGE_CONTENT_ENABLED_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_SETTING_AUTOMATIC_PAGE_CONTENT_DISABLED_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_SETTING_AUTOMATIC_PAGE_CONTENT_DISABLED_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_SETTING_AUTOMATIC_PAGE_CONTENT_DAILY.pixelName to PixelParameter.removeAtb(),
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
