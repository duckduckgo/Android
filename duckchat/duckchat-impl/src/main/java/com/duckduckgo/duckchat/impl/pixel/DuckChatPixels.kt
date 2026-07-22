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
import com.duckduckgo.duckchat.impl.ModelTier
import com.duckduckgo.duckchat.impl.ReportMetric
import com.duckduckgo.duckchat.impl.ReportMetric.USER_DID_CREATE_NEW_CHAT
import com.duckduckgo.duckchat.impl.ReportMetric.USER_DID_OPEN_HISTORY
import com.duckduckgo.duckchat.impl.ReportMetric.USER_DID_SELECT_FIRST_HISTORY_ITEM
import com.duckduckgo.duckchat.impl.ReportMetric.USER_DID_SUBMIT_FIRST_PROMPT
import com.duckduckgo.duckchat.impl.ReportMetric.USER_DID_SUBMIT_PROMPT
import com.duckduckgo.duckchat.impl.ReportMetric.USER_DID_TAP_KEYBOARD_RETURN_KEY
import com.duckduckgo.duckchat.impl.helper.DuckChatTermsOfServiceHandler
import com.duckduckgo.duckchat.impl.metric.DuckAiMetricCollector
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_ADDRESS_BAR_IS_ENABLED_DAILY
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_BROWSER_MENU_IS_ENABLED_DAILY
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_DUCKAI_DAU_TOGGLE_NEVER_ENABLED
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
    fun sendReportMetricPixel(reportMetric: ReportMetric, modelTier: ModelTier? = null)
    fun reportOpen()
    fun reportContextualSheetOpened()
    fun reportContextualSheetDismissed()
    fun reportContextualSheetSessionRestored()
    fun reportContextualSheetExpanded()
    fun reportContextualSheetNewChat()
    fun reportContextualSheetNewChatFromPopup()
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
    fun reportContextualAskAboutPageSelected()
    fun reportContextualChatsMenuTapped()
    fun reportContextualRecentChatsPopupDisplayed()
    fun reportContextualRecentChatSelected()
    fun reportContextualViewAllChatsTapped()
    fun reportContextualPlaceholderContextTapped()
    fun reportContextualPlaceholderContextShown()
    fun reportContextualPageContextInvalidEmpty()
    fun reportContextualPageContextInvalidNoTitle()
    fun reportContextualPageContextInvalidNoContent()

    fun reportContextualFireButtonTapped()
    fun reportContextualFireButtonConfirmed()

    fun reportChatSyncActive()

    fun reportNativeStorageReaderUsed(native: Boolean)
    fun reportNativeStorageDeletionUsed(native: Boolean)
    fun reportVoiceSessionStarted()
    fun reportVoiceNotificationEndChatTapped()
    fun reportVoiceServiceStarted()
    fun reportVoiceServiceKilled()

    fun fireImageGenerationSelected()
    fun fireImageGenerationDeselected()
    fun fireImageGenerationSubmitted()
    fun fireWebSearchSelected()
    fun fireWebSearchDeselected()
    fun fireWebSearchSubmitted()
    fun firePromptSubmitted(
        selectedTool: String,
        modelId: String?,
        reasoningEffort: String?,
        hasImageAttachment: Boolean,
        hasFileAttachment: Boolean,
        hasText: Boolean,
    )

    /** Prompt submitted while the unified input is in a Duck.ai chat context. Fires alongside [firePromptSubmitted]. */
    fun fireSentPromptInChat()
    fun fireModelSelected(modelId: String)
    fun fireReasoningEffortSelected(effortLevel: String)

    /** FE recovery flow: native model picker opened in response to a `showModelPicker` message. */
    fun fireShowModelPicker()

    /** FE recovery flow: the chosen model reported back to the FE via `submitChangeModelAction`. */
    fun fireSubmitChangeModel(modelId: String)

    /** FE recovery flow: a prompt submitted after recovering the chat's model. */
    fun fireSubmitChangeModelPromptSent()

    fun fireSubscriptionUpsellTriggered(source: String, currentTier: String, requiredTier: String, flowType: String)
    fun fireImageAttached(source: String)
    fun fireImageValidationFailed(reason: String)
    fun fireImageRemoved()
    fun fireFileAttached()
    fun fireFileRemoved()
    fun fireFileValidationFailed(reason: String)
    fun fireVoiceTapped()
    fun fireStopGenerationTapped()
    fun fireDuckAiChatHistorySuggestionClicked()
    fun fireDuckAiSearchDuckDuckGoSuggestionClicked()
    fun fireRecentChatDeleteButtonTapped()
    fun fireRecentChatDeleteConfirmed()
    fun fireRecentChatDeleteCancelled()
    fun fireCustomizeResponsesSelected()
    fun fireOmnibarShown()
    fun fireOmnibarTextAreaFocused(landscape: Boolean)
    fun fireOmnibarQuerySubmitted(query: String)
    fun fireOmnibarModeSwitched(directionToSearch: Boolean, hadText: Boolean)
    fun fireOmnibarClearButtonPressed(isSearchMode: Boolean)
    fun fireOmnibarBackButtonPressed(isSearchMode: Boolean)
    fun fireOmnibarKeyboardGoPressed(isSearchMode: Boolean)
    fun fireOmnibarFloatingSubmitPressed(isSearchMode: Boolean)
    fun fireOmnibarFloatingReturnPressed()
    fun fireOmnibarSessionBothModes()
}

@ContributesBinding(AppScope::class)
class RealDuckChatPixels @Inject constructor(
    private val pixel: Pixel,
    private val duckChatFeatureRepository: DuckChatFeatureRepository,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val statisticsUpdater: StatisticsUpdater,
    private val duckAiMetricCollector: DuckAiMetricCollector,
    private val termsOfServiceHandler: DuckChatTermsOfServiceHandler,
) : DuckChatPixels {

    private fun fireCountAndDaily(
        count: DuckChatPixelName,
        daily: DuckChatPixelName,
        parameters: Map<String, String> = emptyMap(),
    ) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            pixel.fire(count, parameters = parameters)
            pixel.fire(daily, parameters = parameters, type = Pixel.PixelType.Daily())
        }
    }

    override fun sendReportMetricPixel(reportMetric: ReportMetric, modelTier: ModelTier?) {
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
                ReportMetric.USER_DID_ACCEPT_TERMS_AND_CONDITIONS -> {
                    val result = termsOfServiceHandler.userAcceptedTerms()
                    if (result.isDuplicate) {
                        if (result.isSyncEnabled) {
                            pixel.fire(DuckChatPixelName.DUCK_CHAT_TERMS_ACCEPTED_DUPLICATE_SYNC_ON)
                        } else {
                            pixel.fire(DuckChatPixelName.DUCK_CHAT_TERMS_ACCEPTED_DUPLICATE_SYNC_OFF)
                        }
                    }
                    DuckChatPixelName.DUCK_CHAT_USER_ACCEPTED_TERMS_AND_CONDITIONS to emptyMap()
                }
            }

            withContext(dispatcherProvider.main()) {
                pixel.fire(pixelName, parameters = params)
                if (refreshAtb) {
                    statisticsUpdater.refreshDuckAiRetentionAtb(mapOf("modelTier" to modelTier?.model))
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

    override fun reportContextualSheetNewChatFromPopup() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_NEW_CHAT_MENU_TAPPED_COUNT)
            pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_NEW_CHAT_MENU_TAPPED_DAILY, type = Pixel.PixelType.Daily())
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

    override fun reportContextualAskAboutPageSelected() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_QUICK_ACTION_ASK_ABOUT_PAGE_SELECTED_COUNT)
            pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_QUICK_ACTION_ASK_ABOUT_PAGE_SELECTED_DAILY, type = Pixel.PixelType.Daily())
        }
    }

    override fun reportContextualChatsMenuTapped() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_CHATS_BUTTON_TAPPED_COUNT)
            pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_CHATS_BUTTON_TAPPED_DAILY, type = Pixel.PixelType.Daily())
        }
    }

    override fun reportContextualRecentChatsPopupDisplayed() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_RECENT_CHATS_POPUP_DISPLAYED_COUNT)
            pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_RECENT_CHATS_POPUP_DISPLAYED_DAILY, type = Pixel.PixelType.Daily())
        }
    }

    override fun reportContextualRecentChatSelected() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_RECENT_CHAT_SELECTED_COUNT)
            pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_RECENT_CHAT_SELECTED_DAILY, type = Pixel.PixelType.Daily())
        }
    }

    override fun reportContextualViewAllChatsTapped() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_VIEW_ALL_CHATS_TAPPED_COUNT)
            pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_VIEW_ALL_CHATS_TAPPED_DAILY, type = Pixel.PixelType.Daily())
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

    override fun reportContextualPageContextInvalidEmpty() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_INVALID_EMPTY_COUNT)
            pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_INVALID_EMPTY_DAILY, type = Pixel.PixelType.Daily())
        }
    }

    override fun reportContextualPageContextInvalidNoTitle() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_INVALID_NO_TITLE_COUNT)
            pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_INVALID_NO_TITLE_DAILY, type = Pixel.PixelType.Daily())
        }
    }

    override fun reportContextualPageContextInvalidNoContent() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_INVALID_NO_CONTENT_COUNT)
            pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_INVALID_NO_CONTENT_DAILY, type = Pixel.PixelType.Daily())
        }
    }

    override fun reportContextualFireButtonTapped() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_FIRE_BUTTON_TAPPED_FIRST, type = Pixel.PixelType.Unique())
            pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_FIRE_BUTTON_TAPPED_DAILY, type = Pixel.PixelType.Daily())
            pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_FIRE_BUTTON_TAPPED_COUNT)
        }
    }

    override fun reportContextualFireButtonConfirmed() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_FIRE_BUTTON_CONFIRMED_FIRST, type = Pixel.PixelType.Unique())
            pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_FIRE_BUTTON_CONFIRMED_DAILY, type = Pixel.PixelType.Daily())
            pixel.fire(DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_FIRE_BUTTON_CONFIRMED_COUNT)
        }
    }

    override fun reportChatSyncActive() {
        pixel.fire(DuckChatPixelName.SYNC_AI_CHAT_ACTIVE, type = Pixel.PixelType.Daily())
    }

    override fun reportNativeStorageReaderUsed(native: Boolean) {
        val pixelName = if (native) {
            DuckChatPixelName.DUCK_CHAT_NATIVE_STORAGE_READER_NATIVE_DAILY
        } else {
            DuckChatPixelName.DUCK_CHAT_NATIVE_STORAGE_READER_WEBVIEW_DAILY
        }
        pixel.fire(pixelName, type = Pixel.PixelType.Daily())
    }

    override fun reportNativeStorageDeletionUsed(native: Boolean) {
        val pixelName = if (native) {
            DuckChatPixelName.DUCK_CHAT_NATIVE_STORAGE_DELETION_NATIVE_COUNT
        } else {
            DuckChatPixelName.DUCK_CHAT_NATIVE_STORAGE_DELETION_WEBVIEW_COUNT
        }
        pixel.fire(pixelName)
    }

    override fun reportVoiceSessionStarted() {
        pixel.fire(DuckChatPixelName.DUCK_CHAT_VOICE_SESSION_STARTED)
    }

    override fun reportVoiceNotificationEndChatTapped() {
        pixel.fire(DuckChatPixelName.DUCK_CHAT_VOICE_NOTIFICATION_END_CHAT_TAPPED)
    }

    override fun reportVoiceServiceStarted() {
        pixel.fire(DuckChatPixelName.DUCK_CHAT_VOICE_SERVICE_STARTED)
    }

    override fun reportVoiceServiceKilled() {
        pixel.fire(DuckChatPixelName.DUCK_CHAT_VOICE_SERVICE_KILLED)
    }

    override fun fireImageGenerationSelected() = fireCountAndDaily(
        DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_IMAGE_GENERATION_SELECTED_COUNT,
        DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_IMAGE_GENERATION_SELECTED_DAILY,
    )

    override fun fireImageGenerationDeselected() = fireCountAndDaily(
        DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_IMAGE_GENERATION_DESELECTED_COUNT,
        DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_IMAGE_GENERATION_DESELECTED_DAILY,
    )

    override fun fireImageGenerationSubmitted() = fireCountAndDaily(
        DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_IMAGE_GENERATION_SUBMITTED_COUNT,
        DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_IMAGE_GENERATION_SUBMITTED_DAILY,
    )

    override fun fireWebSearchSelected() = fireCountAndDaily(
        DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_WEB_SEARCH_SELECTED_COUNT,
        DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_WEB_SEARCH_SELECTED_DAILY,
    )

    override fun fireWebSearchDeselected() = fireCountAndDaily(
        DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_WEB_SEARCH_DESELECTED_COUNT,
        DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_WEB_SEARCH_DESELECTED_DAILY,
    )

    override fun fireWebSearchSubmitted() = fireCountAndDaily(
        DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_WEB_SEARCH_SUBMITTED_COUNT,
        DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_WEB_SEARCH_SUBMITTED_DAILY,
    )

    override fun firePromptSubmitted(
        selectedTool: String,
        modelId: String?,
        reasoningEffort: String?,
        hasImageAttachment: Boolean,
        hasFileAttachment: Boolean,
        hasText: Boolean,
    ) {
        val params = buildMap {
            put(DuckChatPixelParameters.SELECTED_TOOL, selectedTool)
            modelId?.let { put(DuckChatPixelParameters.MODEL_ID, it) }
            reasoningEffort?.let { put(DuckChatPixelParameters.REASONING_EFFORT, it) }
            put(DuckChatPixelParameters.HAS_IMAGE_ATTACHMENT, hasImageAttachment.toString())
            put(DuckChatPixelParameters.HAS_FILE_ATTACHMENT, hasFileAttachment.toString())
            put(DuckChatPixelParameters.HAS_TEXT, hasText.toString())
        }
        fireCountAndDaily(
            DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_PROMPT_SUBMITTED_COUNT,
            DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_PROMPT_SUBMITTED_DAILY,
            params,
        )
    }

    override fun fireSentPromptInChat() = fireCountAndDaily(
        DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_SENT_PROMPT_IN_CHAT_COUNT,
        DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_SENT_PROMPT_IN_CHAT_DAILY,
    )

    override fun fireModelSelected(modelId: String) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            pixel.fire(DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_MODEL_SELECTED, parameters = mapOf(DuckChatPixelParameters.MODEL_ID to modelId))
        }
    }

    override fun fireReasoningEffortSelected(effortLevel: String) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            pixel.fire(
                DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_REASONING_EFFORT_SELECTED,
                parameters = mapOf(DuckChatPixelParameters.EFFORT_LEVEL to effortLevel),
            )
        }
    }

    override fun fireShowModelPicker() {
        fireCountAndDaily(
            DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_SHOW_MODEL_PICKER_COUNT,
            DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_SHOW_MODEL_PICKER_DAILY,
        )
    }

    override fun fireCustomizeResponsesSelected() {
        fireCountAndDaily(
            DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_CUSTOMIZE_RESPONSES_SELECTED_COUNT,
            DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_CUSTOMIZE_RESPONSES_SELECTED_DAILY,
        )
    }

    override fun fireSubmitChangeModel(modelId: String) {
        fireCountAndDaily(
            DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_SUBMIT_CHANGE_MODEL_COUNT,
            DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_SUBMIT_CHANGE_MODEL_DAILY,
            parameters = mapOf(DuckChatPixelParameters.MODEL_ID to modelId),
        )
    }

    override fun fireSubmitChangeModelPromptSent() {
        fireCountAndDaily(
            DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_SUBMIT_CHANGE_MODEL_PROMPT_SENT_COUNT,
            DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_SUBMIT_CHANGE_MODEL_PROMPT_SENT_DAILY,
        )
    }

    override fun fireSubscriptionUpsellTriggered(source: String, currentTier: String, requiredTier: String, flowType: String) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            pixel.fire(
                DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_SUBSCRIPTION_UPSELL_TRIGGERED,
                parameters = mapOf(
                    DuckChatPixelParameters.UPSELL_SOURCE to source,
                    DuckChatPixelParameters.UPSELL_CURRENT_TIER to currentTier,
                    DuckChatPixelParameters.UPSELL_REQUIRED_TIER to requiredTier,
                    DuckChatPixelParameters.UPSELL_FLOW_TYPE to flowType,
                ),
            )
        }
    }

    override fun fireImageAttached(source: String) = fireCountAndDaily(
        DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_IMAGE_ATTACHED_COUNT,
        DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_IMAGE_ATTACHED_DAILY,
        mapOf(DuckChatPixelParameters.ATTACHMENT_SOURCE to source),
    )

    override fun fireImageRemoved() = fireCountAndDaily(
        DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_IMAGE_REMOVED_COUNT,
        DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_IMAGE_REMOVED_DAILY,
    )

    override fun fireImageValidationFailed(reason: String) = fireCountAndDaily(
        DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_IMAGE_VALIDATION_FAILED_COUNT,
        DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_IMAGE_VALIDATION_FAILED_DAILY,
        mapOf(DuckChatPixelParameters.FILE_VALIDATION_REASON to reason),
    )

    override fun fireFileAttached() = fireCountAndDaily(
        DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_FILE_ATTACHED_COUNT,
        DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_FILE_ATTACHED_DAILY,
    )

    override fun fireFileRemoved() = fireCountAndDaily(
        DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_FILE_REMOVED_COUNT,
        DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_FILE_REMOVED_DAILY,
    )

    override fun fireFileValidationFailed(reason: String) = fireCountAndDaily(
        DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_FILE_VALIDATION_FAILED_COUNT,
        DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_FILE_VALIDATION_FAILED_DAILY,
        mapOf(DuckChatPixelParameters.FILE_VALIDATION_REASON to reason),
    )

    override fun fireVoiceTapped() = fireCountAndDaily(
        DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_VOICE_TAPPED_COUNT,
        DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_VOICE_TAPPED_DAILY,
    )

    override fun fireStopGenerationTapped() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            pixel.fire(DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_STOP_GENERATION_TAPPED)
        }
    }

    override fun fireDuckAiChatHistorySuggestionClicked() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            pixel.fire(DuckChatPixelName.AUTOCOMPLETE_DUCKAI_CLICK_CHAT_HISTORY)
        }
    }

    override fun fireDuckAiSearchDuckDuckGoSuggestionClicked() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            pixel.fire(DuckChatPixelName.AUTOCOMPLETE_DUCKAI_CLICK_SEARCH_DUCKDUCKGO)
        }
    }

    override fun fireRecentChatDeleteButtonTapped() = fireCountAndDaily(
        DuckChatPixelName.DUCK_CHAT_RECENT_CHAT_DELETE_BUTTON_TAPPED_COUNT,
        DuckChatPixelName.DUCK_CHAT_RECENT_CHAT_DELETE_BUTTON_TAPPED_DAILY,
    )

    override fun fireRecentChatDeleteConfirmed() = fireCountAndDaily(
        DuckChatPixelName.DUCK_CHAT_RECENT_CHAT_DELETE_CONFIRMED_COUNT,
        DuckChatPixelName.DUCK_CHAT_RECENT_CHAT_DELETE_CONFIRMED_DAILY,
    )

    override fun fireRecentChatDeleteCancelled() = fireCountAndDaily(
        DuckChatPixelName.DUCK_CHAT_RECENT_CHAT_DELETE_CANCELLED_COUNT,
        DuckChatPixelName.DUCK_CHAT_RECENT_CHAT_DELETE_CANCELLED_DAILY,
    )

    override fun fireOmnibarShown() = fireCountAndDaily(
        DUCK_CHAT_EXPERIMENTAL_OMNIBAR_SHOWN_COUNT,
        DUCK_CHAT_EXPERIMENTAL_OMNIBAR_SHOWN_DAILY,
    )

    override fun fireOmnibarTextAreaFocused(landscape: Boolean) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            pixel.fire(
                pixel = DUCK_CHAT_EXPERIMENTAL_OMNIBAR_TEXT_AREA_FOCUSED,
                parameters = mapOf("orientation" to if (landscape) "landscape" else "portrait"),
            )
        }
    }

    override fun fireOmnibarQuerySubmitted(query: String) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            pixel.fire(
                pixel = DUCK_CHAT_EXPERIMENTAL_OMNIBAR_QUERY_SUBMITTED,
                parameters = mapOf(DuckChatPixelParameters.TEXT_LENGTH_BUCKET to toQueryLengthBucket(query.length)),
            )
            pixel.fire(DUCK_CHAT_EXPERIMENTAL_OMNIBAR_QUERY_SUBMITTED_DAILY, type = Pixel.PixelType.Daily())
        }
    }

    override fun fireOmnibarModeSwitched(directionToSearch: Boolean, hadText: Boolean) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            pixel.fire(
                pixel = DUCK_CHAT_EXPERIMENTAL_OMNIBAR_MODE_SWITCHED,
                parameters = mapOf(
                    "direction" to if (directionToSearch) "to_search" else "to_duckai",
                    "had_text" to hadText.toString(),
                ),
            )
        }
    }

    override fun fireOmnibarClearButtonPressed(isSearchMode: Boolean) = fireModeParam(
        DUCK_CHAT_EXPERIMENTAL_OMNIBAR_CLEAR_BUTTON_PRESSED,
        isSearchMode,
    )

    override fun fireOmnibarBackButtonPressed(isSearchMode: Boolean) = fireModeParam(
        DUCK_CHAT_EXPERIMENTAL_OMNIBAR_BACK_BUTTON_PRESSED,
        isSearchMode,
    )

    override fun fireOmnibarKeyboardGoPressed(isSearchMode: Boolean) = fireModeParam(
        DUCK_CHAT_EXPERIMENTAL_OMNIBAR_KEYBOARD_GO_PRESSED,
        isSearchMode,
    )

    override fun fireOmnibarFloatingSubmitPressed(isSearchMode: Boolean) = fireModeParam(
        DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FLOATING_SUBMIT_PRESSED,
        isSearchMode,
    )

    override fun fireOmnibarFloatingReturnPressed() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            pixel.fire(DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FLOATING_RETURN_PRESSED)
        }
    }

    override fun fireOmnibarSessionBothModes() = fireCountAndDaily(
        DUCK_CHAT_EXPERIMENTAL_OMNIBAR_SESSION_BOTH_MODES,
        DUCK_CHAT_EXPERIMENTAL_OMNIBAR_SESSION_BOTH_MODES_DAILY,
    )

    private fun fireModeParam(name: DuckChatPixelName, isSearchMode: Boolean) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            pixel.fire(pixel = name, parameters = inputScreenPixelsModeParam(isSearchMode))
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
    AI_FEATURES_DISABLED_COUNT("ai_features_disabled_count"),
    AI_FEATURES_DISABLED_DAILY("ai_features_disabled_daily"),
    AI_FEATURES_SEARCH_ASSIST_NEVER_COUNT("ai_features_search_assist_never_count"),
    AI_FEATURES_SEARCH_ASSIST_NEVER_DAILY("ai_features_search_assist_never_daily"),
    AI_FEATURES_SEARCH_ASSIST_ON_DEMAND_COUNT("ai_features_search_assist_on_demand_count"),
    AI_FEATURES_SEARCH_ASSIST_ON_DEMAND_DAILY("ai_features_search_assist_on_demand_daily"),
    AI_FEATURES_SEARCH_ASSIST_SOMETIMES_COUNT("ai_features_search_assist_sometimes_count"),
    AI_FEATURES_SEARCH_ASSIST_SOMETIMES_DAILY("ai_features_search_assist_sometimes_daily"),
    AI_FEATURES_SEARCH_ASSIST_OFTEN_COUNT("ai_features_search_assist_often_count"),
    AI_FEATURES_SEARCH_ASSIST_OFTEN_DAILY("ai_features_search_assist_often_daily"),
    AI_FEATURES_HIDE_IMAGES_ON_COUNT("ai_features_hide_images_on_count"),
    AI_FEATURES_HIDE_IMAGES_ON_DAILY("ai_features_hide_images_on_daily"),
    AI_FEATURES_HIDE_IMAGES_OFF_COUNT("ai_features_hide_images_off_count"),
    AI_FEATURES_HIDE_IMAGES_OFF_DAILY("ai_features_hide_images_off_daily"),
    AI_FEATURES_STATE_DAILY("ai_features_state_daily"),
    SERP_SETTINGS_UNRECOGNIZED_VALUE("serp_settings_unrecognized_value"),
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
    DUCK_CHAT_USER_ACCEPTED_TERMS_AND_CONDITIONS("m_aichat_duckai_accepted_terms_and_conditions"),
    DUCK_CHAT_EXPERIMENTAL_OMNIBAR_DAILY_RETENTION("m_aichat_experimental_omnibar_daily_retention"),
    DUCK_CHAT_DUCKAI_DAU_TOGGLE_NEVER_ENABLED("m_aichat_duckai_dau_toggle_never_enabled"),
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
    DUCK_CHAT_CONTEXTUAL_NEW_CHAT_MENU_TAPPED_COUNT("m_aichat_contextual_new_chat_menu_tapped_count"),
    DUCK_CHAT_CONTEXTUAL_NEW_CHAT_MENU_TAPPED_DAILY("m_aichat_contextual_new_chat_menu_tapped_daily"),
    DUCK_CHAT_CONTEXTUAL_QUICK_ACTION_SUMMARISE_SELECTED_COUNT("m_aichat_contextual_quick_action_summarize_selected_count"),
    DUCK_CHAT_CONTEXTUAL_QUICK_ACTION_SUMMARISE_SELECTED_DAILY("m_aichat_contextual_quick_action_summarize_selected_daily"),
    DUCK_CHAT_CONTEXTUAL_QUICK_ACTION_ASK_ABOUT_PAGE_SELECTED_COUNT("m_aichat_contextual_quick_action_ask_about_page_selected_count"),
    DUCK_CHAT_CONTEXTUAL_QUICK_ACTION_ASK_ABOUT_PAGE_SELECTED_DAILY("m_aichat_contextual_quick_action_ask_about_page_selected_daily"),
    DUCK_CHAT_CONTEXTUAL_CHATS_BUTTON_TAPPED_COUNT("m_aichat_contextual_chats_button_tapped_count"),
    DUCK_CHAT_CONTEXTUAL_CHATS_BUTTON_TAPPED_DAILY("m_aichat_contextual_chats_button_tapped_daily"),
    DUCK_CHAT_CONTEXTUAL_RECENT_CHATS_POPUP_DISPLAYED_COUNT("m_aichat_contextual_recent_chats_popup_displayed_count"),
    DUCK_CHAT_CONTEXTUAL_RECENT_CHATS_POPUP_DISPLAYED_DAILY("m_aichat_contextual_recent_chats_popup_displayed_daily"),
    DUCK_CHAT_CONTEXTUAL_RECENT_CHAT_SELECTED_COUNT("m_aichat_contextual_recent_chat_selected_count"),
    DUCK_CHAT_CONTEXTUAL_RECENT_CHAT_SELECTED_DAILY("m_aichat_contextual_recent_chat_selected_daily"),
    DUCK_CHAT_CONTEXTUAL_VIEW_ALL_CHATS_TAPPED_COUNT("m_aichat_contextual_view_all_chats_tapped_count"),
    DUCK_CHAT_CONTEXTUAL_VIEW_ALL_CHATS_TAPPED_DAILY("m_aichat_contextual_view_all_chats_tapped_daily"),
    DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_PLACEHOLDER_SHOWN_COUNT("m_aichat_contextual_page_context_placeholder_shown_count"),
    DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_PLACEHOLDER_SHOWN_DAILY("m_aichat_contextual_page_context_placeholder_shown_daily"),
    DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_PLACEHOLDER_TAPPED_COUNT("m_aichat_contextual_page_context_placeholder_tapped_count"),
    DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_PLACEHOLDER_TAPPED_DAILY("m_aichat_contextual_page_context_placeholder_tapped_daily"),
    DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_INVALID_EMPTY_COUNT("m_aichat_contextual_page_context_invalid_empty_c"),
    DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_INVALID_EMPTY_DAILY("m_aichat_contextual_page_context_invalid_empty_d"),
    DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_INVALID_NO_TITLE_COUNT("m_aichat_contextual_page_context_invalid_no_title_c"),
    DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_INVALID_NO_TITLE_DAILY("m_aichat_contextual_page_context_invalid_no_title_d"),
    DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_INVALID_NO_CONTENT_COUNT("m_aichat_contextual_page_context_invalid_no_content_c"),
    DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_INVALID_NO_CONTENT_DAILY("m_aichat_contextual_page_context_invalid_no_content_d"),
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
    DUCK_CHAT_RECENT_CHAT_SELECTED_COUNT("m_aichat_recent_chat_selected_count"),
    DUCK_CHAT_RECENT_CHAT_SELECTED_DAILY("m_aichat_recent_chat_selected_daily"),
    DUCK_CHAT_RECENT_CHAT_SELECTED_PINNED_COUNT("m_aichat_recent_chat_selected_pinned_count"),
    DUCK_CHAT_RECENT_CHAT_SELECTED_PINNED_DAILY("m_aichat_recent_chat_selected_pinned_daily"),
    DUCK_CHAT_RECENT_CHAT_DELETE_BUTTON_TAPPED_COUNT("m_aichat_recent_chat_delete_button_tapped_count"),
    DUCK_CHAT_RECENT_CHAT_DELETE_BUTTON_TAPPED_DAILY("m_aichat_recent_chat_delete_button_tapped_daily"),
    DUCK_CHAT_RECENT_CHAT_DELETE_CONFIRMED_COUNT("m_aichat_recent_chat_delete_confirmed_count"),
    DUCK_CHAT_RECENT_CHAT_DELETE_CONFIRMED_DAILY("m_aichat_recent_chat_delete_confirmed_daily"),
    DUCK_CHAT_RECENT_CHAT_DELETE_CANCELLED_COUNT("m_aichat_recent_chat_delete_cancelled_count"),
    DUCK_CHAT_RECENT_CHAT_DELETE_CANCELLED_DAILY("m_aichat_recent_chat_delete_cancelled_daily"),
    AUTOCOMPLETE_DUCKAI_CLICK_CHAT_HISTORY("m_autocomplete_duckai_click_chat_history"),
    AUTOCOMPLETE_DUCKAI_CLICK_SEARCH_DUCKDUCKGO("m_autocomplete_duckai_click_search_duckduckgo"),
    DUCK_CHAT_VOICE_ENTRY_TAPPED_COUNT("m_aichat_voice_entry_tapped_count"),
    DUCK_CHAT_VOICE_ENTRY_TAPPED_DAILY("m_aichat_voice_entry_tapped_daily"),
    DUCK_CHAT_VOICE_SESSION_STARTED("m_aichat_voice_session_started"),
    DUCK_CHAT_VOICE_NOTIFICATION_OPEN_CHAT_TAPPED("m_aichat_voice_notification_open_chat_tapped"),
    DUCK_CHAT_VOICE_NOTIFICATION_END_CHAT_TAPPED("m_aichat_voice_notification_end_chat_tapped"),
    DUCK_CHAT_VOICE_SERVICE_STARTED("m_aichat_voice_service_started"),
    DUCK_CHAT_VOICE_SERVICE_KILLED("m_aichat_voice_service_killed"),
    DUCK_CHAT_CONTEXTUAL_FIRE_BUTTON_TAPPED_FIRST("m_aichat_contextual_fire_button_tapped_first"),
    DUCK_CHAT_CONTEXTUAL_FIRE_BUTTON_TAPPED_DAILY("m_aichat_contextual_fire_button_tapped_daily"),
    DUCK_CHAT_CONTEXTUAL_FIRE_BUTTON_TAPPED_COUNT("m_aichat_contextual_fire_button_tapped_count"),
    DUCK_CHAT_CONTEXTUAL_FIRE_BUTTON_CONFIRMED_FIRST("m_aichat_contextual_fire_button_confirmed_first"),
    DUCK_CHAT_CONTEXTUAL_FIRE_BUTTON_CONFIRMED_DAILY("m_aichat_contextual_fire_button_confirmed_daily"),
    DUCK_CHAT_CONTEXTUAL_FIRE_BUTTON_CONFIRMED_COUNT("m_aichat_contextual_fire_button_confirmed_count"),

    SYNC_AI_CHAT_ACTIVE("sync_ai_chat_active"),

    DUCK_CHAT_TERMS_ACCEPTED_DUPLICATE_SYNC_ON("m_aichat_terms_accepted_duplicate_sync_on"),
    DUCK_CHAT_TERMS_ACCEPTED_DUPLICATE_SYNC_OFF("m_aichat_terms_accepted_duplicate_sync_off"),
    DUCK_CHAT_SETTINGS_DEFAULT_TOGGLE_POSITION_CHANGED_COUNT("m_aichat_settings_default_toggle_position_changed_count"),
    DUCK_CHAT_SETTINGS_DEFAULT_TOGGLE_POSITION_CHANGED_DAILY("m_aichat_settings_default_toggle_position_changed_daily"),

    DUCK_CHAT_NATIVE_STORAGE_READER_NATIVE_DAILY("m_duck-ai_native-storage_reader_native_daily"),
    DUCK_CHAT_NATIVE_STORAGE_READER_WEBVIEW_DAILY("m_duck-ai_native-storage_reader_webview_daily"),
    DUCK_CHAT_NATIVE_STORAGE_DELETION_NATIVE_COUNT("m_duck-ai_native-storage_deletion_native_count"),
    DUCK_CHAT_NATIVE_STORAGE_DELETION_WEBVIEW_COUNT("m_duck-ai_native-storage_deletion_webview_count"),

    DUCK_CHAT_HISTORY_CHAT_OPENED_COUNT("m_aichat_history_chat_opened_count"),
    DUCK_CHAT_HISTORY_CHAT_OPENED_DAILY("m_aichat_history_chat_opened_daily"),
    DUCK_CHAT_HISTORY_EMPTY_CTA_TAPPED_COUNT("m_aichat_history_empty_cta_tapped_count"),
    DUCK_CHAT_HISTORY_EMPTY_CTA_TAPPED_DAILY("m_aichat_history_empty_cta_tapped_daily"),
    DUCK_CHAT_HISTORY_SEARCH_ACTIVATED_COUNT("m_aichat_history_search_activated_count"),
    DUCK_CHAT_HISTORY_SEARCH_ACTIVATED_DAILY("m_aichat_history_search_activated_daily"),
    DUCK_CHAT_HISTORY_FIRE_ALL_TAPPED_COUNT("m_aichat_history_fire_all_tapped_count"),
    DUCK_CHAT_HISTORY_FIRE_ALL_TAPPED_DAILY("m_aichat_history_fire_all_tapped_daily"),
    DUCK_CHAT_HISTORY_FIRE_ALL_CONFIRMED_COUNT("m_aichat_history_fire_all_confirmed_count"),
    DUCK_CHAT_HISTORY_FIRE_ALL_CONFIRMED_DAILY("m_aichat_history_fire_all_confirmed_daily"),
    DUCK_CHAT_HISTORY_FIRE_SELECTED_TAPPED_COUNT("m_aichat_history_fire_selected_tapped_count"),
    DUCK_CHAT_HISTORY_FIRE_SELECTED_TAPPED_DAILY("m_aichat_history_fire_selected_tapped_daily"),
    DUCK_CHAT_HISTORY_FIRE_SELECTED_CONFIRMED_COUNT("m_aichat_history_fire_selected_confirmed_count"),
    DUCK_CHAT_HISTORY_FIRE_SELECTED_CONFIRMED_DAILY("m_aichat_history_fire_selected_confirmed_daily"),
    DUCK_CHAT_HISTORY_PIN_ADDED_COUNT("m_aichat_history_pin_added_count"),
    DUCK_CHAT_HISTORY_PIN_ADDED_DAILY("m_aichat_history_pin_added_daily"),
    DUCK_CHAT_HISTORY_PIN_REMOVED_COUNT("m_aichat_history_pin_removed_count"),
    DUCK_CHAT_HISTORY_PIN_REMOVED_DAILY("m_aichat_history_pin_removed_daily"),
    DUCK_CHAT_HISTORY_RENAME_OPENED_COUNT("m_aichat_history_rename_opened_count"),
    DUCK_CHAT_HISTORY_RENAME_OPENED_DAILY("m_aichat_history_rename_opened_daily"),
    DUCK_CHAT_HISTORY_RENAME_SAVED_COUNT("m_aichat_history_rename_saved_count"),
    DUCK_CHAT_HISTORY_RENAME_SAVED_DAILY("m_aichat_history_rename_saved_daily"),
    DUCK_CHAT_HISTORY_DOWNLOAD_STARTED_COUNT("m_aichat_history_download_started_count"),
    DUCK_CHAT_HISTORY_DOWNLOAD_STARTED_DAILY("m_aichat_history_download_started_daily"),
    DUCK_CHAT_HISTORY_SELECT_MODE_ENTERED_COUNT("m_aichat_history_select_mode_entered_count"),
    DUCK_CHAT_HISTORY_SELECT_MODE_ENTERED_DAILY("m_aichat_history_select_mode_entered_daily"),
    DUCK_CHAT_HISTORY_SELECT_ALL_TOGGLED_COUNT("m_aichat_history_select_all_toggled_count"),
    DUCK_CHAT_HISTORY_SELECT_ALL_TOGGLED_DAILY("m_aichat_history_select_all_toggled_daily"),
    DUCK_CHAT_HISTORY_NEW_CHAT_TAPPED_COUNT("m_aichat_history_new_chat_tapped_count"),
    DUCK_CHAT_HISTORY_NEW_CHAT_TAPPED_DAILY("m_aichat_history_new_chat_tapped_daily"),
    DUCK_CHAT_HISTORY_DOWNLOAD_SELECTED_COUNT("m_aichat_history_download_selected_count"),
    DUCK_CHAT_HISTORY_DOWNLOAD_SELECTED_DAILY("m_aichat_history_download_selected_daily"),
    DUCK_CHAT_UNIFIED_INPUT_IMAGE_GENERATION_SELECTED_COUNT("m_aichat_unified_input_image_generation_selected_count"),
    DUCK_CHAT_UNIFIED_INPUT_IMAGE_GENERATION_SELECTED_DAILY("m_aichat_unified_input_image_generation_selected_daily"),
    DUCK_CHAT_UNIFIED_INPUT_IMAGE_GENERATION_DESELECTED_COUNT("m_aichat_unified_input_image_generation_deselected_count"),
    DUCK_CHAT_UNIFIED_INPUT_IMAGE_GENERATION_DESELECTED_DAILY("m_aichat_unified_input_image_generation_deselected_daily"),
    DUCK_CHAT_UNIFIED_INPUT_IMAGE_GENERATION_SUBMITTED_COUNT("m_aichat_unified_input_image_generation_submitted_count"),
    DUCK_CHAT_UNIFIED_INPUT_IMAGE_GENERATION_SUBMITTED_DAILY("m_aichat_unified_input_image_generation_submitted_daily"),
    DUCK_CHAT_UNIFIED_INPUT_WEB_SEARCH_SELECTED_COUNT("m_aichat_unified_input_web_search_selected_count"),
    DUCK_CHAT_UNIFIED_INPUT_WEB_SEARCH_SELECTED_DAILY("m_aichat_unified_input_web_search_selected_daily"),
    DUCK_CHAT_UNIFIED_INPUT_WEB_SEARCH_DESELECTED_COUNT("m_aichat_unified_input_web_search_deselected_count"),
    DUCK_CHAT_UNIFIED_INPUT_WEB_SEARCH_DESELECTED_DAILY("m_aichat_unified_input_web_search_deselected_daily"),
    DUCK_CHAT_UNIFIED_INPUT_WEB_SEARCH_SUBMITTED_COUNT("m_aichat_unified_input_web_search_submitted_count"),
    DUCK_CHAT_UNIFIED_INPUT_WEB_SEARCH_SUBMITTED_DAILY("m_aichat_unified_input_web_search_submitted_daily"),
    DUCK_CHAT_UNIFIED_INPUT_PROMPT_SUBMITTED_COUNT("m_aichat_unified_input_prompt_submitted_count"),
    DUCK_CHAT_UNIFIED_INPUT_PROMPT_SUBMITTED_DAILY("m_aichat_unified_input_prompt_submitted_daily"),
    DUCK_CHAT_UNIFIED_INPUT_SENT_PROMPT_IN_CHAT_COUNT("m_aichat_unified_input_sent_prompt_in_chat_count"),
    DUCK_CHAT_UNIFIED_INPUT_SENT_PROMPT_IN_CHAT_DAILY("m_aichat_unified_input_sent_prompt_in_chat_daily"),
    DUCK_CHAT_UNIFIED_INPUT_MODEL_SELECTED("m_aichat_unified_input_model_selected"),
    DUCK_CHAT_UNIFIED_INPUT_REASONING_EFFORT_SELECTED("m_aichat_unified_input_reasoning_effort_selected"),
    DUCK_CHAT_UNIFIED_INPUT_SUBSCRIPTION_UPSELL_TRIGGERED("m_aichat_unified_input_subscription_upsell_triggered"),
    DUCK_CHAT_UNIFIED_INPUT_SHOW_MODEL_PICKER_COUNT("aichat_unified_input_show_model_picker_count"),
    DUCK_CHAT_UNIFIED_INPUT_SHOW_MODEL_PICKER_DAILY("aichat_unified_input_show_model_picker_daily"),
    DUCK_CHAT_UNIFIED_INPUT_CUSTOMIZE_RESPONSES_SELECTED_COUNT("m_aichat_unified_input_customize_responses_selected_count"),
    DUCK_CHAT_UNIFIED_INPUT_CUSTOMIZE_RESPONSES_SELECTED_DAILY("m_aichat_unified_input_customize_responses_selected_daily"),
    DUCK_CHAT_UNIFIED_INPUT_SUBMIT_CHANGE_MODEL_COUNT("aichat_unified_input_submit_change_model_count"),
    DUCK_CHAT_UNIFIED_INPUT_SUBMIT_CHANGE_MODEL_DAILY("aichat_unified_input_submit_change_model_daily"),
    DUCK_CHAT_UNIFIED_INPUT_SUBMIT_CHANGE_MODEL_PROMPT_SENT_COUNT("aichat_unified_input_submit_change_model_prompt_sent_count"),
    DUCK_CHAT_UNIFIED_INPUT_SUBMIT_CHANGE_MODEL_PROMPT_SENT_DAILY("aichat_unified_input_submit_change_model_prompt_sent_daily"),
    DUCK_CHAT_UNIFIED_INPUT_IMAGE_ATTACHED_COUNT("m_aichat_unified_input_image_attached_count"),
    DUCK_CHAT_UNIFIED_INPUT_IMAGE_ATTACHED_DAILY("m_aichat_unified_input_image_attached_daily"),
    DUCK_CHAT_UNIFIED_INPUT_IMAGE_REMOVED_COUNT("m_aichat_unified_input_image_removed_count"),
    DUCK_CHAT_UNIFIED_INPUT_IMAGE_REMOVED_DAILY("m_aichat_unified_input_image_removed_daily"),
    DUCK_CHAT_UNIFIED_INPUT_IMAGE_VALIDATION_FAILED_COUNT("m_aichat_unified_input_image_validation_failed_count"),
    DUCK_CHAT_UNIFIED_INPUT_IMAGE_VALIDATION_FAILED_DAILY("m_aichat_unified_input_image_validation_failed_daily"),
    DUCK_CHAT_UNIFIED_INPUT_FILE_ATTACHED_COUNT("m_aichat_unified_input_file_attached_count"),
    DUCK_CHAT_UNIFIED_INPUT_FILE_ATTACHED_DAILY("m_aichat_unified_input_file_attached_daily"),
    DUCK_CHAT_UNIFIED_INPUT_FILE_REMOVED_COUNT("m_aichat_unified_input_file_removed_count"),
    DUCK_CHAT_UNIFIED_INPUT_FILE_REMOVED_DAILY("m_aichat_unified_input_file_removed_daily"),
    DUCK_CHAT_UNIFIED_INPUT_FILE_VALIDATION_FAILED_COUNT("m_aichat_unified_input_file_validation_failed_count"),
    DUCK_CHAT_UNIFIED_INPUT_FILE_VALIDATION_FAILED_DAILY("m_aichat_unified_input_file_validation_failed_daily"),
    DUCK_CHAT_UNIFIED_INPUT_VOICE_TAPPED_COUNT("m_aichat_unified_input_voice_tapped_count"),
    DUCK_CHAT_UNIFIED_INPUT_VOICE_TAPPED_DAILY("m_aichat_unified_input_voice_tapped_daily"),
    DUCK_CHAT_UNIFIED_INPUT_STOP_GENERATION_TAPPED("m_aichat_unified_input_stop_generation_tapped"),
}

object DuckChatPixelParameters {
    const val WAS_USED_BEFORE = "was_used_before"
    const val DELTA_TIMESTAMP_PARAMETERS = "delta-timestamp-minutes"
    const val INPUT_SCREEN_MODE = "mode"
    const val TEXT_LENGTH_BUCKET = "text_length_bucket"
    const val NEW_ADDRESS_BAR_SELECTION = "selection"
    const val DEFAULT_TOGGLE_POSITION = "default_position"
    const val DEFAULT_TOGGLE_POSITION_VALUE = "value"

    // Unified Input pixels
    const val SELECTED_TOOL = "selected_tool"
    const val MODEL_ID = "model_id"
    const val REASONING_EFFORT = "reasoning_effort"
    const val EFFORT_LEVEL = "effort_level"
    const val HAS_IMAGE_ATTACHMENT = "has_image_attachment"
    const val HAS_FILE_ATTACHMENT = "has_file_attachment"
    const val HAS_TEXT = "has_text"
    const val ATTACHMENT_SOURCE = "source"
    const val FILE_VALIDATION_REASON = "reason"
    const val UPSELL_SOURCE = "source"
    const val UPSELL_CURRENT_TIER = "current_tier"
    const val UPSELL_REQUIRED_TIER = "required_tier"
    const val UPSELL_FLOW_TYPE = "flow_type"

    // ai_features_state_daily
    const val DUCK_AI = "duck_ai"
    const val SEARCH_ASSIST = "search_assist"
    const val HIDE_AI_IMAGES = "hide_ai_images"
    const val NO_AI = "no_ai"
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
            DuckChatPixelName.AI_FEATURES_DISABLED_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.AI_FEATURES_DISABLED_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.AI_FEATURES_SEARCH_ASSIST_NEVER_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.AI_FEATURES_SEARCH_ASSIST_NEVER_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.AI_FEATURES_SEARCH_ASSIST_ON_DEMAND_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.AI_FEATURES_SEARCH_ASSIST_ON_DEMAND_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.AI_FEATURES_SEARCH_ASSIST_SOMETIMES_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.AI_FEATURES_SEARCH_ASSIST_SOMETIMES_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.AI_FEATURES_SEARCH_ASSIST_OFTEN_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.AI_FEATURES_SEARCH_ASSIST_OFTEN_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.AI_FEATURES_HIDE_IMAGES_ON_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.AI_FEATURES_HIDE_IMAGES_ON_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.AI_FEATURES_HIDE_IMAGES_OFF_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.AI_FEATURES_HIDE_IMAGES_OFF_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.AI_FEATURES_STATE_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.SERP_SETTINGS_UNRECOGNIZED_VALUE.pixelName to PixelParameter.removeAtb(),
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
            DUCK_CHAT_DUCKAI_DAU_TOGGLE_NEVER_ENABLED.pixelName to PixelParameter.removeAtb(),
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
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_NEW_CHAT_MENU_TAPPED_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_NEW_CHAT_MENU_TAPPED_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_QUICK_ACTION_SUMMARISE_SELECTED_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_QUICK_ACTION_SUMMARISE_SELECTED_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_QUICK_ACTION_ASK_ABOUT_PAGE_SELECTED_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_QUICK_ACTION_ASK_ABOUT_PAGE_SELECTED_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_CHATS_BUTTON_TAPPED_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_CHATS_BUTTON_TAPPED_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_RECENT_CHATS_POPUP_DISPLAYED_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_RECENT_CHATS_POPUP_DISPLAYED_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_RECENT_CHAT_SELECTED_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_RECENT_CHAT_SELECTED_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_VIEW_ALL_CHATS_TAPPED_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_VIEW_ALL_CHATS_TAPPED_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_PLACEHOLDER_SHOWN_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_PLACEHOLDER_SHOWN_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_PLACEHOLDER_TAPPED_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_PLACEHOLDER_TAPPED_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_INVALID_EMPTY_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_INVALID_EMPTY_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_INVALID_NO_TITLE_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_INVALID_NO_TITLE_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_INVALID_NO_CONTENT_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_INVALID_NO_CONTENT_DAILY.pixelName to PixelParameter.removeAtb(),
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
            DuckChatPixelName.DUCK_CHAT_RECENT_CHAT_SELECTED_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_RECENT_CHAT_SELECTED_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_RECENT_CHAT_SELECTED_PINNED_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_RECENT_CHAT_SELECTED_PINNED_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_RECENT_CHAT_DELETE_BUTTON_TAPPED_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_RECENT_CHAT_DELETE_BUTTON_TAPPED_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_RECENT_CHAT_DELETE_CONFIRMED_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_RECENT_CHAT_DELETE_CONFIRMED_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_RECENT_CHAT_DELETE_CANCELLED_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_RECENT_CHAT_DELETE_CANCELLED_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.SYNC_AI_CHAT_ACTIVE.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_VOICE_ENTRY_TAPPED_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_VOICE_ENTRY_TAPPED_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_VOICE_SESSION_STARTED.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_VOICE_NOTIFICATION_OPEN_CHAT_TAPPED.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_VOICE_NOTIFICATION_END_CHAT_TAPPED.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_VOICE_SERVICE_STARTED.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_VOICE_SERVICE_KILLED.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_SETTINGS_DEFAULT_TOGGLE_POSITION_CHANGED_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_SETTINGS_DEFAULT_TOGGLE_POSITION_CHANGED_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_FIRE_BUTTON_TAPPED_FIRST.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_FIRE_BUTTON_TAPPED_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_FIRE_BUTTON_TAPPED_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_FIRE_BUTTON_CONFIRMED_FIRST.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_FIRE_BUTTON_CONFIRMED_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_FIRE_BUTTON_CONFIRMED_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_NATIVE_STORAGE_READER_NATIVE_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_NATIVE_STORAGE_READER_WEBVIEW_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_NATIVE_STORAGE_DELETION_NATIVE_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_NATIVE_STORAGE_DELETION_WEBVIEW_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_HISTORY_CHAT_OPENED_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_HISTORY_CHAT_OPENED_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_HISTORY_EMPTY_CTA_TAPPED_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_HISTORY_EMPTY_CTA_TAPPED_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_HISTORY_SEARCH_ACTIVATED_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_HISTORY_SEARCH_ACTIVATED_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_HISTORY_FIRE_ALL_TAPPED_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_HISTORY_FIRE_ALL_TAPPED_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_HISTORY_FIRE_ALL_CONFIRMED_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_HISTORY_FIRE_ALL_CONFIRMED_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_HISTORY_FIRE_SELECTED_TAPPED_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_HISTORY_FIRE_SELECTED_TAPPED_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_HISTORY_FIRE_SELECTED_CONFIRMED_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_HISTORY_FIRE_SELECTED_CONFIRMED_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_HISTORY_PIN_ADDED_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_HISTORY_PIN_ADDED_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_HISTORY_PIN_REMOVED_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_HISTORY_PIN_REMOVED_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_HISTORY_RENAME_OPENED_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_HISTORY_RENAME_OPENED_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_HISTORY_RENAME_SAVED_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_HISTORY_RENAME_SAVED_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_HISTORY_DOWNLOAD_STARTED_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_HISTORY_DOWNLOAD_STARTED_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_HISTORY_SELECT_MODE_ENTERED_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_HISTORY_SELECT_MODE_ENTERED_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_HISTORY_SELECT_ALL_TOGGLED_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_HISTORY_SELECT_ALL_TOGGLED_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_HISTORY_NEW_CHAT_TAPPED_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_HISTORY_NEW_CHAT_TAPPED_DAILY.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_HISTORY_DOWNLOAD_SELECTED_COUNT.pixelName to PixelParameter.removeAtb(),
            DuckChatPixelName.DUCK_CHAT_HISTORY_DOWNLOAD_SELECTED_DAILY.pixelName to PixelParameter.removeAtb(),
            "m_duck-ai_native-storage_" to PixelParameter.removeAtb(),
            // Prefix: covers every m_aichat_unified_input_* pixel (tools, submit, model/reasoning,
            // upsell, attachments, voice, stop) AND the app-side chat_header_upgrade_tapped, which
            // shares the prefix — the interceptor matches outgoing pixel names with startsWith.
            "m_aichat_unified_input_" to PixelParameter.removeAtb(),
            // Same coverage for unified_input pixels added without the legacy m_ prefix.
            "aichat_unified_input_" to PixelParameter.removeAtb(),
        )
    }
}

internal fun Pixel.fireCountAndDaily(
    countPixel: DuckChatPixelName,
    dailyPixel: DuckChatPixelName,
    parameters: Map<String, String> = emptyMap(),
) {
    fire(countPixel, parameters)
    fire(dailyPixel, parameters, type = Pixel.PixelType.Daily())
}

internal fun inputScreenPixelsModeParam(isSearchMode: Boolean) = mapOf(
    DuckChatPixelParameters.INPUT_SCREEN_MODE to if (isSearchMode) {
        "search"
    } else {
        "aiChat"
    },
)

internal fun toQueryLengthBucket(length: Int): String =
    when {
        length <= 15 -> "short"
        length <= 40 -> "medium"
        length <= 100 -> "long"
        else -> "very_long"
    }
