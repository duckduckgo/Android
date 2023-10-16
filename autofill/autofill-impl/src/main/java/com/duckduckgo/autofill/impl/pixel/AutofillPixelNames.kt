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

import com.duckduckgo.app.global.plugins.pixel.PixelRequiringDataCleaningPlugin
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.EMAIL_TOOLTIP_DISMISSED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.EMAIL_USE_ADDRESS
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.EMAIL_USE_ALIAS
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

    EMAIL_USE_ALIAS("email_filled_random"),
    EMAIL_USE_ADDRESS("email_filled_main"),
    EMAIL_TOOLTIP_DISMISSED("email_tooltip_dismissed"),

    SYSTEM_AUTOFILL_USED("m_autofill_system_autofillservice_autofilled"),
}

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = PixelRequiringDataCleaningPlugin::class,
)
object AutofillPixelsRequiringDataCleaning : PixelRequiringDataCleaningPlugin {
    override fun names(): List<String> {
        return listOf(
            EMAIL_USE_ALIAS.pixelName,
            EMAIL_USE_ADDRESS.pixelName,
            EMAIL_TOOLTIP_DISMISSED.pixelName,
        )
    }
}
