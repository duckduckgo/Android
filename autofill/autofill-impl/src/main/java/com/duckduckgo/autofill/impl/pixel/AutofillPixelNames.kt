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

package com.duckduckgo.autofill.impl.pixel

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_DECLINE_PROMPT_TO_DISABLE_AUTOFILL_SNACKBAR_OPEN_SETTINGS
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_DECLINE_PROMPT_TO_DISABLE_AUTOFILL_SNACKBAR_SHOWN
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_DEVICE_AUTH_ERROR_HARDWARE_UNAVAILABLE
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_ENGAGEMENT_ACTIVE_USER
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_ENGAGEMENT_ENABLED_USER
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_ENGAGEMENT_ONBOARDED_USER
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_ENGAGEMENT_STACKED_LOGINS
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_IMPORT_GOOGLE_PASSWORDS_MAIN_APP_SETTINGS_HIDDEN
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_IMPORT_GOOGLE_PASSWORDS_RESULT_FAILURE_WEBVIEW_CRASH
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_IMPORT_PASSWORDS_COPIED_DESKTOP_LINK
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_IMPORT_PASSWORDS_GET_DESKTOP_BROWSER
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_IMPORT_PASSWORDS_SHARED_DESKTOP_LINK
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_IMPORT_PASSWORDS_SYNC_WITH_DESKTOP
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_IMPORT_PASSWORDS_USER_JOURNEY_RESTARTED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_IMPORT_PASSWORDS_USER_JOURNEY_STARTED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_IMPORT_PASSWORDS_USER_JOURNEY_SUCCESSFUL
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_IMPORT_PASSWORDS_USER_JOURNEY_UNSUCCESSFUL
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_IMPORT_PASSWORDS_USER_TOOK_NO_ACTION
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_MANAGEMENT_SCREEN_OPENED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_NEVER_SAVE_FOR_THIS_SITE_OVERFLOW_MENU
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_ONBOARDING_SAVE_PROMPT_DISMISSED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_ONBOARDING_SAVE_PROMPT_EXCLUDE
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_ONBOARDING_SAVE_PROMPT_SAVED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_ONBOARDING_SAVE_PROMPT_SHOWN
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SELECT_LOGIN_AUTOPROMPT_SELECTED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SELECT_LOGIN_PROMPT_SELECTED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SERVICE_CRASH
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SERVICE_DISABLED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SERVICE_DISABLED_DAU
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SERVICE_ENABLED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SERVICE_ENABLED_DAU
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SERVICE_PASSWORDS_DISMISSED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SERVICE_PASSWORDS_DISMISSED_AUTH
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SERVICE_PASSWORDS_OPEN
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SERVICE_PASSWORDS_SEARCH
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SERVICE_PASSWORDS_SEARCH_INPUT
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SERVICE_PASSWORD_SELECTED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SERVICE_SUGGESTION_CONFIRMED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SETTINGS_OPENED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SITE_BREAKAGE_REPORT
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SITE_BREAKAGE_REPORT_AVAILABLE
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SITE_BREAKAGE_REPORT_CONFIRMATION_CONFIRMED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SITE_BREAKAGE_REPORT_CONFIRMATION_DISMISSED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SITE_BREAKAGE_REPORT_CONFIRMATION_DISPLAYED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SYNC_DESKTOP_PASSWORDS_CTA_BUTTON
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SYNC_DESKTOP_PASSWORDS_OVERFLOW_MENU
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SYSTEM_AUTOFILL_USED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.EMAIL_TOOLTIP_DISMISSED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.EMAIL_USE_ADDRESS
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.EMAIL_USE_ALIAS
import com.duckduckgo.common.utils.plugins.pixel.PixelParamRemovalPlugin
import com.duckduckgo.common.utils.plugins.pixel.PixelParamRemovalPlugin.PixelParameter
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding

enum class AutofillPixelNames(override val pixelName: String) : Pixel.PixelName {
    AUTOFILL_ONBOARDING_SAVE_PROMPT_SHOWN("autofill_logins_save_login_inline_onboarding_displayed"),
    AUTOFILL_ONBOARDING_SAVE_PROMPT_DISMISSED("autofill_logins_save_login_inline_onboarding_dismissed"),
    AUTOFILL_ONBOARDING_SAVE_PROMPT_SAVED("autofill_logins_save_login_inline_onboarding_confirmed"),
    AUTOFILL_ONBOARDING_SAVE_PROMPT_EXCLUDE("autofill_logins_save_login_onboarding_exclude_site_confirmed"),

    AUTOFILL_SAVE_LOGIN_PROMPT_SHOWN("m_autofill_logins_save_login_inline_displayed"),
    AUTOFILL_SAVE_LOGIN_PROMPT_DISMISSED("m_autofill_logins_save_login_inline_dismissed"),
    AUTOFILL_SAVE_LOGIN_PROMPT_SAVED("m_autofill_logins_save_login_inline_confirmed"),

    AUTOFILL_SAVE_PASSWORD_PROMPT_SHOWN("m_autofill_logins_save_password_inline_displayed"),
    AUTOFILL_SAVE_PASSWORD_PROMPT_DISMISSED("m_autofill_logins_save_password_inline_dismissed"),
    AUTOFILL_SAVE_PASSWORD_PROMPT_SAVED("m_autofill_logins_save_password_inline_confirmed"),

    AUTOFILL_UPDATE_LOGIN_PROMPT_SHOWN("m_autofill_logins_update_password_inline_displayed"),
    AUTOFILL_UPDATE_LOGIN_PROMPT_DISMISSED("m_autofill_logins_update_password_inline_dismissed"),
    AUTOFILL_UPDATE_LOGIN_PROMPT_SAVED("m_autofill_logins_update_password_inline_confirmed"),

    AUTOFILL_SELECT_LOGIN_PROMPT_SHOWN("m_autofill_logins_fill_login_inline_manual_displayed"),
    AUTOFILL_SELECT_LOGIN_PROMPT_DISMISSED("m_autofill_logins_fill_login_inline_manual_dismissed"),
    AUTOFILL_SELECT_LOGIN_PROMPT_SELECTED("m_autofill_logins_fill_login_inline_manual_confirmed"),

    AUTOFILL_PASSWORD_GENERATION_PROMPT_SHOWN("m_autofill_logins_password_generation_prompt_displayed"),
    AUTOFILL_PASSWORD_GENERATION_PROMPT_DISMISSED("m_autofill_logins_password_generation_prompt_dismissed"),
    AUTOFILL_PASSWORD_GENERATION_ACCEPTED("m_autofill_logins_password_generation_prompt_confirmed"),

    AUTOFILL_SELECT_LOGIN_AUTOPROMPT_SHOWN("m_autofill_logins_fill_login_inline_autoprompt_displayed"),
    AUTOFILL_SELECT_LOGIN_AUTOPROMPT_DISMISSED("m_autofill_logins_autoprompt_dismissed"),
    AUTOFILL_SELECT_LOGIN_AUTOPROMPT_SELECTED("m_autofill_logins_fill_login_inline_autoprompt_confirmed"),

    AUTOFILL_AUTHENTICATION_TO_AUTOFILL_SHOWN("m_autofill_logins_fill_login_inline_authentication_device-displayed"),
    AUTOFILL_AUTHENTICATION_TO_AUTOFILL_AUTH_SUCCESSFUL("m_autofill_logins_fill_login_inline_authentication_device-auth_authenticated"),
    AUTOFILL_AUTHENTICATION_TO_AUTOFILL_AUTH_FAILURE("m_autofill_logins_fill_login_inline_authentication_device-auth_failed"),
    AUTOFILL_AUTHENTICATION_TO_AUTOFILL_AUTH_CANCELLED("m_autofill_logins_fill_login_inline_authentication_device-auth_cancelled"),

    AUTOFILL_DECLINE_PROMPT_TO_DISABLE_AUTOFILL_SNACKBAR_SHOWN("autofill_logins_save_disable_snackbar_shown"),
    AUTOFILL_DECLINE_PROMPT_TO_DISABLE_AUTOFILL_SNACKBAR_OPEN_SETTINGS("autofill_logins_save_disable_snackbar_open_settings"),

    AUTOFILL_DECLINE_PROMPT_TO_DISABLE_AUTOFILL_SHOWN("m_autofill_logins_save_disable-prompt_shown"),
    AUTOFILL_DECLINE_PROMPT_TO_DISABLE_AUTOFILL_KEEP_USING("m_autofill_logins_save_disable-prompt_autofill-kept"),
    AUTOFILL_DECLINE_PROMPT_TO_DISABLE_AUTOFILL_DISABLE("m_autofill_logins_save_disable-prompt_autofill-disabled"),
    AUTOFILL_DECLINE_PROMPT_TO_DISABLE_AUTOFILL_DISMISSED("m_autofill_logins_save_disable-prompt_dismissed"),

    AUTOFILL_ENABLE_AUTOFILL_TOGGLE_MANUALLY_ENABLED("m_autofill_logins_settings_enabled"),
    AUTOFILL_ENABLE_AUTOFILL_TOGGLE_MANUALLY_DISABLED("m_autofill_logins_settings_disabled"),

    AUTOFILL_MANAGEMENT_SCREEN_OPENED("m_autofill_management_opened"),
    AUTOFILL_DELETE_LOGIN("m_autofill_management_delete_login"),
    AUTOFILL_DELETE_ALL_LOGINS("m_autofill_management_delete_all_logins"),
    AUTOFILL_MANUALLY_UPDATE_CREDENTIAL("m_autofill_management_update_login"),
    AUTOFILL_MANUALLY_SAVE_CREDENTIAL("m_autofill_management_save_login"),
    AUTOFILL_COPY_USERNAME("m_autofill_management_copy_username"),
    AUTOFILL_COPY_PASSWORD("m_autofill_management_copy_password"),

    EMAIL_USE_ALIAS("email_filled_random"),
    EMAIL_USE_ADDRESS("email_filled_main"),
    EMAIL_TOOLTIP_DISMISSED("email_tooltip_dismissed"),

    EMAIL_PROTECTION_IN_CONTEXT_PROMPT_DISPLAYED("m_email_incontext_prompt_displayed"),
    EMAIL_PROTECTION_IN_CONTEXT_PROMPT_CONFIRMED("m_email_incontext_prompt_confirmed"),
    EMAIL_PROTECTION_IN_CONTEXT_PROMPT_DISMISSED("m_email_incontext_prompt_dismissed"),
    EMAIL_PROTECTION_IN_CONTEXT_PROMPT_NEVER_AGAIN("m_email_incontext_prompt_dismissed_persisted"),

    EMAIL_PROTECTION_IN_CONTEXT_MODAL_DISPLAYED("m_email_incontext_modal_displayed"),
    EMAIL_PROTECTION_IN_CONTEXT_MODAL_DISMISSED("m_email_incontext_modal_dismissed"),

    EMAIL_PROTECTION_IN_CONTEXT_MODAL_EXIT_EARLY_CANCEL("m_email_incontext_modal_exit_early_continue"),
    EMAIL_PROTECTION_IN_CONTEXT_MODAL_EXIT_EARLY_CONFIRM("m_email_incontext_modal_exit_early"),

    AUTOFILL_NEVER_SAVE_FOR_THIS_SITE_USER_SELECTED_FROM_SAVE_DIALOG("m_autofill_logins_save_login_exclude_site_confirmed"),
    AUTOFILL_NEVER_SAVE_FOR_THIS_SITE_CONFIRMATION_PROMPT_DISPLAYED("m_autofill_settings_reset_excluded_displayed"),
    AUTOFILL_NEVER_SAVE_FOR_THIS_SITE_CONFIRMATION_PROMPT_CONFIRMED("m_autofill_settings_reset_excluded_confirmed"),
    AUTOFILL_NEVER_SAVE_FOR_THIS_SITE_CONFIRMATION_PROMPT_DISMISSED("m_autofill_settings_reset_excluded_dismissed"),

    AUTOFILL_DEVICE_CAPABILITY_CAPABLE("m_autofill_device_capability_capable"),
    AUTOFILL_DEVICE_CAPABILITY_SECURE_STORAGE_UNAVAILABLE("m_autofill_device_capability_secure_storage_unavailable"),
    AUTOFILL_DEVICE_CAPABILITY_DEVICE_AUTH_DISABLED("m_autofill_device_capability_device_auth_disabled"),
    AUTOFILL_DEVICE_CAPABILITY_SECURE_STORAGE_UNAVAILABLE_AND_DEVICE_AUTH_DISABLED(
        "m_autofill_device_capability_secure_storage_unavailable_and_device_auth_disabled",
    ),
    AUTOFILL_DEVICE_CAPABILITY_UNKNOWN_ERROR("m_autofill_device_capability_unknown"),
    AUTOFILL_DEVICE_AUTH_ERROR_HARDWARE_UNAVAILABLE("autofill_device_auth_error_hardware_unavailable"),

    AUTOFILL_SURVEY_AVAILABLE_PROMPT_DISPLAYED("m_autofill_management_screen_visit_survey_available"),

    AUTOFILL_ENGAGEMENT_ACTIVE_USER("m_autofill_activeuser"),
    AUTOFILL_ENGAGEMENT_ENABLED_USER("m_autofill_enableduser"),
    AUTOFILL_ENGAGEMENT_ONBOARDED_USER("m_autofill_onboardeduser"),
    AUTOFILL_ENGAGEMENT_STACKED_LOGINS("m_autofill_logins_stacked"),
    AUTOFILL_TOGGLED_ON_SEARCH("m_autofill_toggled_on"),
    AUTOFILL_TOGGLED_OFF_SEARCH("m_autofill_toggled_off"),

    AUTOFILL_IMPORT_GOOGLE_PASSWORDS_MAIN_APP_SETTINGS_HIDDEN("autofill_import_google_passwords_main_app_settings_hidden"),
    AUTOFILL_IMPORT_GOOGLE_PASSWORDS_EMPTY_STATE_CTA_BUTTON_TAPPED("autofill_import_google_passwords_import_button_tapped"),
    AUTOFILL_IMPORT_GOOGLE_PASSWORDS_EMPTY_STATE_CTA_BUTTON_SHOWN("autofill_import_google_passwords_import_button_shown"),
    AUTOFILL_IMPORT_GOOGLE_PASSWORDS_EMPTY_STATE_CTA_BUTTON_DISMISSED("autofill_import_google_passwords_import_button_dismissed"),
    AUTOFILL_IMPORT_GOOGLE_PASSWORDS_OVERFLOW_MENU("autofill_import_google_passwords_overflow_menu_tapped"),
    AUTOFILL_IMPORT_GOOGLE_PASSWORDS_PREIMPORT_PROMPT_DISPLAYED("autofill_import_google_passwords_preimport_prompt_displayed"),
    AUTOFILL_IMPORT_GOOGLE_PASSWORDS_PREIMPORT_PROMPT_CONFIRMED("autofill_import_google_passwords_preimport_prompt_confirmed"),
    AUTOFILL_IMPORT_GOOGLE_PASSWORDS_RESULT_FAILURE_ERROR_PARSING("autofill_import_google_passwords_result_parsing"),
    AUTOFILL_IMPORT_GOOGLE_PASSWORDS_RESULT_FAILURE_WEBVIEW_CRASH("autofill_import_google_passwords_webview_crash"),
    AUTOFILL_IMPORT_GOOGLE_PASSWORDS_RESULT_FAILURE_USER_CANCELLED("autofill_import_google_passwords_result_user_cancelled"),
    AUTOFILL_IMPORT_GOOGLE_PASSWORDS_RESULT_SUCCESS("autofill_import_google_passwords_result_success"),

    AUTOFILL_SYNC_DESKTOP_PASSWORDS_CTA_BUTTON("m_autofill_logins_import_no_passwords"),
    AUTOFILL_SYNC_DESKTOP_PASSWORDS_OVERFLOW_MENU("m_autofill_logins_import"),
    AUTOFILL_IMPORT_PASSWORDS_GET_DESKTOP_BROWSER("m_autofill_logins_import_get_desktop"),
    AUTOFILL_IMPORT_PASSWORDS_SYNC_WITH_DESKTOP("m_autofill_logins_import_sync"),
    AUTOFILL_IMPORT_PASSWORDS_USER_TOOK_NO_ACTION("m_autofill_logins_import_no-action"),
    AUTOFILL_IMPORT_PASSWORDS_COPIED_DESKTOP_LINK("m_get_desktop_copy"),
    AUTOFILL_IMPORT_PASSWORDS_SHARED_DESKTOP_LINK("m_get_desktop_share"),
    AUTOFILL_IMPORT_PASSWORDS_USER_JOURNEY_SUCCESSFUL("m_autofill_logins_import_success"),
    AUTOFILL_IMPORT_PASSWORDS_USER_JOURNEY_UNSUCCESSFUL("m_autofill_logins_import_failure"),
    AUTOFILL_IMPORT_PASSWORDS_USER_JOURNEY_STARTED("m_autofill_logins_import_user_journey_started"),
    AUTOFILL_IMPORT_PASSWORDS_USER_JOURNEY_RESTARTED("m_autofill_logins_import_user_journey_restarted"),

    AUTOFILL_SITE_BREAKAGE_REPORT("autofill_logins_report_failure"),
    AUTOFILL_SITE_BREAKAGE_REPORT_AVAILABLE("autofill_logins_report_available"),
    AUTOFILL_SITE_BREAKAGE_REPORT_CONFIRMATION_DISPLAYED("autofill_logins_report_confirmation_displayed"),
    AUTOFILL_SITE_BREAKAGE_REPORT_CONFIRMATION_DISMISSED("autofill_logins_report_confirmation_dismissed"),
    AUTOFILL_SITE_BREAKAGE_REPORT_CONFIRMATION_CONFIRMED("autofill_logins_report_confirmation_confirmed"),

    AUTOFILL_SERVICE_ENABLED("autofill_extension_enabled"),
    AUTOFILL_SERVICE_ENABLED_DAU("autofill_extension_toggled_on"),
    AUTOFILL_SERVICE_DISABLED("autofill_extension_disabled"),
    AUTOFILL_SERVICE_DISABLED_DAU("autofill_extension_toggled_off"),
    AUTOFILL_SERVICE_SUGGESTION_CONFIRMED("autofill_extension_quicktype_confirmed"),
    AUTOFILL_SERVICE_PASSWORDS_OPEN("autofill_extension_passwords_opened"),
    AUTOFILL_SERVICE_PASSWORDS_DISMISSED("autofill_extension_passwords_dismissed"),
    AUTOFILL_SERVICE_PASSWORDS_DISMISSED_AUTH("autofill_extension_passwords_dismissed_auth"),
    AUTOFILL_SERVICE_PASSWORD_SELECTED("autofill_extension_password_selected"),
    AUTOFILL_SERVICE_PASSWORDS_SEARCH("autofill_extension_passwords_search"),
    AUTOFILL_SERVICE_PASSWORDS_SEARCH_INPUT("autofill_extension_passwords_search_input"), // when user types in the search bar first time
    AUTOFILL_SERVICE_CRASH("autofill_service_crash"),

    AUTOFILL_SETTINGS_OPENED("autofill_settings_opened"),
    AUTOFILL_NEVER_SAVE_FOR_THIS_SITE_OVERFLOW_MENU("autofill_reset_excluded_overflow_menu_tapped"),

    AUTOFILL_SYSTEM_AUTOFILL_USED("autofill_systemautofill_used"),
}

object AutofillPixelParameters {
    const val LAST_USED_PIXEL_KEY = "last_used"
}

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = PixelParamRemovalPlugin::class,
)
object AutofillPixelsRequiringDataCleaning : PixelParamRemovalPlugin {
    override fun names(): List<Pair<String, Set<PixelParameter>>> {
        return listOf(
            AUTOFILL_MANAGEMENT_SCREEN_OPENED.pixelName to PixelParameter.removeAtb(),

            EMAIL_USE_ALIAS.pixelName to PixelParameter.removeAll(),
            EMAIL_USE_ADDRESS.pixelName to PixelParameter.removeAll(),
            EMAIL_TOOLTIP_DISMISSED.pixelName to PixelParameter.removeAll(),

            AUTOFILL_ENGAGEMENT_ACTIVE_USER.pixelName to PixelParameter.removeAtb(),
            AUTOFILL_ENGAGEMENT_ENABLED_USER.pixelName to PixelParameter.removeAtb(),
            AUTOFILL_ENGAGEMENT_ONBOARDED_USER.pixelName to PixelParameter.removeAtb(),
            AUTOFILL_ENGAGEMENT_STACKED_LOGINS.pixelName to PixelParameter.removeAtb(),

            AUTOFILL_SYNC_DESKTOP_PASSWORDS_CTA_BUTTON.pixelName to PixelParameter.removeAtb(),
            AUTOFILL_SYNC_DESKTOP_PASSWORDS_OVERFLOW_MENU.pixelName to PixelParameter.removeAtb(),
            AUTOFILL_IMPORT_PASSWORDS_GET_DESKTOP_BROWSER.pixelName to PixelParameter.removeAtb(),
            AUTOFILL_IMPORT_PASSWORDS_SYNC_WITH_DESKTOP.pixelName to PixelParameter.removeAtb(),
            AUTOFILL_IMPORT_PASSWORDS_USER_TOOK_NO_ACTION.pixelName to PixelParameter.removeAtb(),
            AUTOFILL_IMPORT_PASSWORDS_COPIED_DESKTOP_LINK.pixelName to PixelParameter.removeAtb(),
            AUTOFILL_IMPORT_PASSWORDS_SHARED_DESKTOP_LINK.pixelName to PixelParameter.removeAtb(),
            AUTOFILL_IMPORT_PASSWORDS_USER_JOURNEY_SUCCESSFUL.pixelName to PixelParameter.removeAtb(),
            AUTOFILL_IMPORT_PASSWORDS_USER_JOURNEY_UNSUCCESSFUL.pixelName to PixelParameter.removeAtb(),
            AUTOFILL_IMPORT_PASSWORDS_USER_JOURNEY_STARTED.pixelName to PixelParameter.removeAtb(),
            AUTOFILL_IMPORT_PASSWORDS_USER_JOURNEY_RESTARTED.pixelName to PixelParameter.removeAtb(),

            AUTOFILL_SELECT_LOGIN_AUTOPROMPT_SELECTED.pixelName to PixelParameter.removeAtb(),
            AUTOFILL_SELECT_LOGIN_PROMPT_SELECTED.pixelName to PixelParameter.removeAtb(),

            AUTOFILL_SITE_BREAKAGE_REPORT.pixelName to PixelParameter.removeAtb(),
            AUTOFILL_SITE_BREAKAGE_REPORT.pixelName to PixelParameter.removeAtb(),
            AUTOFILL_SITE_BREAKAGE_REPORT.pixelName to PixelParameter.removeAtb(),
            AUTOFILL_SITE_BREAKAGE_REPORT.pixelName to PixelParameter.removeAtb(),
            AUTOFILL_SITE_BREAKAGE_REPORT.pixelName to PixelParameter.removeAtb(),
            AUTOFILL_SITE_BREAKAGE_REPORT_AVAILABLE.pixelName to PixelParameter.removeAtb(),
            AUTOFILL_SITE_BREAKAGE_REPORT_CONFIRMATION_DISPLAYED.pixelName to PixelParameter.removeAtb(),
            AUTOFILL_SITE_BREAKAGE_REPORT_CONFIRMATION_DISMISSED.pixelName to PixelParameter.removeAtb(),
            AUTOFILL_SITE_BREAKAGE_REPORT_CONFIRMATION_CONFIRMED.pixelName to PixelParameter.removeAtb(),

            AUTOFILL_ONBOARDING_SAVE_PROMPT_SHOWN.pixelName to PixelParameter.removeAtb(),
            AUTOFILL_ONBOARDING_SAVE_PROMPT_DISMISSED.pixelName to PixelParameter.removeAtb(),
            AUTOFILL_ONBOARDING_SAVE_PROMPT_SAVED.pixelName to PixelParameter.removeAtb(),
            AUTOFILL_ONBOARDING_SAVE_PROMPT_EXCLUDE.pixelName to PixelParameter.removeAtb(),

            AUTOFILL_DECLINE_PROMPT_TO_DISABLE_AUTOFILL_SNACKBAR_SHOWN.pixelName to PixelParameter.removeAtb(),
            AUTOFILL_DECLINE_PROMPT_TO_DISABLE_AUTOFILL_SNACKBAR_OPEN_SETTINGS.pixelName to PixelParameter.removeAtb(),

            AUTOFILL_SERVICE_ENABLED.pixelName to PixelParameter.removeAtb(),
            AUTOFILL_SERVICE_ENABLED_DAU.pixelName to PixelParameter.removeAtb(),
            AUTOFILL_SERVICE_DISABLED.pixelName to PixelParameter.removeAtb(),
            AUTOFILL_SERVICE_DISABLED_DAU.pixelName to PixelParameter.removeAtb(),
            AUTOFILL_SERVICE_SUGGESTION_CONFIRMED.pixelName to PixelParameter.removeAtb(),
            AUTOFILL_SERVICE_PASSWORDS_OPEN.pixelName to PixelParameter.removeAtb(),
            AUTOFILL_SERVICE_PASSWORDS_DISMISSED.pixelName to PixelParameter.removeAtb(),
            AUTOFILL_SERVICE_PASSWORDS_DISMISSED_AUTH.pixelName to PixelParameter.removeAtb(),
            AUTOFILL_SERVICE_PASSWORD_SELECTED.pixelName to PixelParameter.removeAtb(),
            AUTOFILL_SERVICE_PASSWORDS_SEARCH.pixelName to PixelParameter.removeAtb(),
            AUTOFILL_SERVICE_PASSWORDS_SEARCH_INPUT.pixelName to PixelParameter.removeAtb(),
            AUTOFILL_SERVICE_CRASH.pixelName to PixelParameter.removeAtb(),

            AUTOFILL_DEVICE_AUTH_ERROR_HARDWARE_UNAVAILABLE.pixelName to PixelParameter.removeAtb(),
            AUTOFILL_SETTINGS_OPENED.pixelName to PixelParameter.removeAtb(),
            AUTOFILL_NEVER_SAVE_FOR_THIS_SITE_OVERFLOW_MENU.pixelName to PixelParameter.removeAtb(),

            AUTOFILL_IMPORT_GOOGLE_PASSWORDS_MAIN_APP_SETTINGS_HIDDEN.pixelName to PixelParameter.removeAtb(),
            AUTOFILL_SYSTEM_AUTOFILL_USED.pixelName to PixelParameter.removeAtb(),

            AUTOFILL_IMPORT_GOOGLE_PASSWORDS_RESULT_FAILURE_WEBVIEW_CRASH.pixelName to PixelParameter.removeAtb(),
        )
    }
}
