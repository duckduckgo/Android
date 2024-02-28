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
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.EMAIL_TOOLTIP_DISMISSED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.EMAIL_USE_ADDRESS
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.EMAIL_USE_ALIAS
import com.duckduckgo.common.utils.plugins.pixel.PixelParamRemovalPlugin
import com.duckduckgo.common.utils.plugins.pixel.PixelParamRemovalPlugin.PixelParameter
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding

enum class AutofillPixelNames(override val pixelName: String) : Pixel.PixelName {
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

    AUTOFILL_DECLINE_PROMPT_TO_DISABLE_AUTOFILL_SHOWN("m_autofill_logins_save_disable-prompt_shown"),
    AUTOFILL_DECLINE_PROMPT_TO_DISABLE_AUTOFILL_KEEP_USING("m_autofill_logins_save_disable-prompt_autofill-kept"),
    AUTOFILL_DECLINE_PROMPT_TO_DISABLE_AUTOFILL_DISABLE("m_autofill_logins_save_disable-prompt_autofill-disabled"),
    AUTOFILL_DECLINE_PROMPT_TO_DISABLE_AUTOFILL_DISMISSED("m_autofill_logins_save_disable-prompt_dismissed"),

    AUTOFILL_ENABLE_AUTOFILL_TOGGLE_MANUALLY_ENABLED("m_autofill_logins_settings_enabled"),
    AUTOFILL_ENABLE_AUTOFILL_TOGGLE_MANUALLY_DISABLED("m_autofill_logins_settings_disabled"),

    MENU_ACTION_AUTOFILL_PRESSED("m_nav_autofill_menu_item_pressed"),
    SETTINGS_AUTOFILL_MANAGEMENT_OPENED("m_autofill_settings_opened"),

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
}

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = PixelParamRemovalPlugin::class,
)
object AutofillPixelsRequiringDataCleaning : PixelParamRemovalPlugin {
    override fun names(): List<Pair<String, Set<PixelParameter>>> {
        return listOf(
            EMAIL_USE_ALIAS.pixelName to PixelParameter.removeAll(),
            EMAIL_USE_ADDRESS.pixelName to PixelParameter.removeAll(),
            EMAIL_TOOLTIP_DISMISSED.pixelName to PixelParameter.removeAll(),
        )
    }
}
