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

package com.duckduckgo.autofill.pixel

import com.duckduckgo.app.statistics.pixels.Pixel

enum class AutofillPixelNames(override val pixelName: String) : Pixel.PixelName {
    AUTOFILL_SAVE_LOGIN_PROMPT_SHOWN("m_autofill_logins_save_login_inline_displayed"),
    AUTOFILL_SAVE_LOGIN_PROMPT_DISMISSED("m_autofill_logins_save_login_inline_dismissed"),
    AUTOFILL_SAVE_LOGIN_PROMPT_SAVED("m_autofill_logins_save_login_inline_confirmed"),

    AUTOFILL_UPDATE_LOGIN_PROMPT_SHOWN("m_autofill_logins_update_password_inline_displayed"),
    AUTOFILL_UPDATE_LOGIN_PROMPT_DISMISSED("m_autofill_logins_update_password_inline_dismissed"),
    AUTOFILL_UPDATE_LOGIN_PROMPT_SAVED("m_autofill_logins_update_password_inline_confirmed"),

    AUTOFILL_SELECT_LOGIN_PROMPT_SHOWN("m_autofill_logins_fill_login_inline_displayed"),
    AUTOFILL_SELECT_LOGIN_PROMPT_DISMISSED("m_autofill_logins_fill_login_inline_dismissed"),
    AUTOFILL_SELECT_LOGIN_PROMPT_SELECTED("m_autofill_logins_fill_login_inline_confirmed"),

    AUTOFILL_SELECT_LOGIN_AUTOPROMPT_SHOWN("m_autofill_logins_fill_login_inline_autoprompt_displayed"),
    AUTOFILL_SELECT_LOGIN_AUTOPROMPT_DISMISSED("m_autofill_logins_autoprompt_dismissed"),
    AUTOFILL_SELECT_LOGIN_AUTOPROMPT_SELECTED("m_autofill_logins_fill_login_inline_autoprompt_confirmed"),

    AUTOFILL_AUTHENTICATION_TO_AUTOFILL_SHOWN("m_autofill_authentication_to_autofill_shown"),
    AUTOFILL_AUTHENTICATION_TO_AUTOFILL_AUTH_SUCCESSFUL("m_autofill_authentication_to_autofill_success"),
    AUTOFILL_AUTHENTICATION_TO_AUTOFILL_AUTH_FAILURE("m_autofill_authentication_to_autofill_failure"),
    AUTOFILL_AUTHENTICATION_TO_AUTOFILL_AUTH_CANCELLED("m_autofill_authentication_to_autofill_cancelled"),

    AUTOFILL_AUTHENTICATION_TO_CREDENTIAL_MANAGEMENT_SHOWN("m_autofill_authentication_to_creds_management_shown"),
    AUTOFILL_AUTHENTICATION_TO_CREDENTIAL_MANAGEMENT_SUCCESSFUL("m_autofill_authentication_to_creds_management_success"),
    AUTOFILL_AUTHENTICATION_TO_CREDENTIAL_MANAGEMENT_FAILURE("m_autofill_authentication_to_creds_management_failure"),
    AUTOFILL_AUTHENTICATION_TO_CREDENTIAL_MANAGEMENT_CANCELLED("m_autofill_authentication_to_creds_management_cancelled"),

    AUTOFILL_DECLINE_PROMPT_TO_DISABLE_AUTOFILL_SHOWN("m_autofill_decline_prompt_to_disable_autofill_shown"),
    AUTOFILL_DECLINE_PROMPT_TO_DISABLE_AUTOFILL_KEEP_USING("m_autofill_decline_prompt_to_disable_autofill_kept"),
    AUTOFILL_DECLINE_PROMPT_TO_DISABLE_AUTOFILL_DISABLE("m_autofill_decline_prompt_to_disable_autofill_disabled"),
    AUTOFILL_DECLINE_PROMPT_TO_DISABLE_AUTOFILL_DISMISSED("m_autofill_decline_prompt_to_disable_autofill_dismissed"),

    AUTOFILL_ENABLE_AUTOFILL_TOGGLE_MANUALLY_ENABLED("m_autofill_enable_autofill_toggle_manually_enabled"),
    AUTOFILL_ENABLE_AUTOFILL_TOGGLE_MANUALLY_DISABLED("m_autofill_enable_autofill_toggle_manually_disabled"),

    AUTOFILL_DEVICE_CAPABILITY_CAPABLE("m_autofill_device_capability_capable"),
    AUTOFILL_DEVICE_CAPABILITY_SECURE_STORAGE_UNAVAILABLE("m_autofill_device_capability_secure_storage_unavailable"),
    AUTOFILL_DEVICE_CAPABILITY_DEVICE_AUTH_DISABLED("m_autofill_device_capability_device_auth_disabled"),
    AUTOFILL_DEVICE_CAPABILITY_SECURE_STORAGE_UNAVAILABLE_AND_DEVICE_AUTH_DISABLED(
        "m_autofill_device_capability_secure_storage_unavailable_and_device_auth_disabled"
    ),
    AUTOFILL_DEVICE_CAPABILITY_UNKNOWN_ERROR("m_autofill_device_capability_unknown")
}
