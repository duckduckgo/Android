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

package com.duckduckgo.autofill.impl.ui.credential.saving

import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_NEVER_SAVE_FOR_THIS_SITE_USER_SELECTED_FROM_SAVE_DIALOG
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_ONBOARDING_SAVE_PROMPT_DISMISSED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_ONBOARDING_SAVE_PROMPT_EXCLUDE
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_ONBOARDING_SAVE_PROMPT_SAVED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_ONBOARDING_SAVE_PROMPT_SHOWN
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SAVE_LOGIN_PROMPT_DISMISSED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SAVE_LOGIN_PROMPT_SAVED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SAVE_LOGIN_PROMPT_SHOWN
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SAVE_PASSWORD_PROMPT_DISMISSED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SAVE_PASSWORD_PROMPT_SAVED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SAVE_PASSWORD_PROMPT_SHOWN
import com.duckduckgo.autofill.impl.ui.credential.saving.AutofillSavingCredentialsDialogFragment.AutofillSavingPixelEventNames.Companion.pixelNameDialogAccepted
import com.duckduckgo.autofill.impl.ui.credential.saving.AutofillSavingCredentialsDialogFragment.AutofillSavingPixelEventNames.Companion.pixelNameDialogDismissed
import com.duckduckgo.autofill.impl.ui.credential.saving.AutofillSavingCredentialsDialogFragment.AutofillSavingPixelEventNames.Companion.pixelNameDialogExclude
import com.duckduckgo.autofill.impl.ui.credential.saving.AutofillSavingCredentialsDialogFragment.AutofillSavingPixelEventNames.Companion.pixelNameDialogShown
import com.duckduckgo.autofill.impl.ui.credential.saving.AutofillSavingCredentialsDialogFragment.AutofillSavingPixelEventNames.Companion.saveType
import com.duckduckgo.autofill.impl.ui.credential.saving.AutofillSavingCredentialsDialogFragment.CredentialSaveType.PasswordOnly
import com.duckduckgo.autofill.impl.ui.credential.saving.AutofillSavingCredentialsDialogFragment.CredentialSaveType.UsernameAndPassword
import com.duckduckgo.autofill.impl.ui.credential.saving.AutofillSavingCredentialsDialogFragment.CredentialSaveType.UsernameOnly
import org.junit.Assert.assertEquals
import org.junit.Test

class AutofillSavingPixelEventNamesTest {

    @Test
    fun whenSavingAcceptedWithUsernameAndPasswordThenCorrectPixelUsed() {
        assertEquals(pixelNameDialogAccepted(UsernameAndPassword, onboardingMode = false), AUTOFILL_SAVE_LOGIN_PROMPT_SAVED)
    }

    @Test
    fun whenSavingAcceptedWithPasswordOnlyThenCorrectPixelUsed() {
        assertEquals(pixelNameDialogAccepted(PasswordOnly, onboardingMode = false), AUTOFILL_SAVE_PASSWORD_PROMPT_SAVED)
    }

    @Test
    fun whenDialogShownWithUsernameAndPasswordThenCorrectPixelUsed() {
        assertEquals(pixelNameDialogShown(UsernameAndPassword, onboardingMode = false), AUTOFILL_SAVE_LOGIN_PROMPT_SHOWN)
    }

    @Test
    fun whenDialogShownWithPasswordOnlyThenCorrectPixelUsed() {
        assertEquals(pixelNameDialogShown(PasswordOnly, onboardingMode = false), AUTOFILL_SAVE_PASSWORD_PROMPT_SHOWN)
    }

    @Test
    fun whenDialogDismissedWithUsernameAndPasswordThenCorrectPixelUsed() {
        assertEquals(pixelNameDialogDismissed(UsernameAndPassword, onboardingMode = false), AUTOFILL_SAVE_LOGIN_PROMPT_DISMISSED)
    }

    @Test
    fun whenDialogDismissedWithPasswordOnlyThenCorrectPixelUsed() {
        assertEquals(pixelNameDialogDismissed(PasswordOnly, onboardingMode = false), AUTOFILL_SAVE_PASSWORD_PROMPT_DISMISSED)
    }

    @Test
    fun whenNeverForThisSiteClickedThenCorrectPixelUsed() {
        assertEquals(
            pixelNameDialogExclude(UsernameAndPassword, onboardingMode = false),
            AUTOFILL_NEVER_SAVE_FOR_THIS_SITE_USER_SELECTED_FROM_SAVE_DIALOG,
        )
    }

    @Test
    fun whenSavingAcceptedInOnboardingDialogThenCorrectPixelUsed() {
        assertEquals(pixelNameDialogAccepted(UsernameAndPassword, onboardingMode = true), AUTOFILL_ONBOARDING_SAVE_PROMPT_SAVED)
    }

    @Test
    fun whenDialogShownInOnboardingDialogThenCorrectPixelUsed() {
        assertEquals(pixelNameDialogShown(UsernameAndPassword, onboardingMode = true), AUTOFILL_ONBOARDING_SAVE_PROMPT_SHOWN)
    }

    @Test
    fun whenDialogDismissedInOnboardingDialogThenCorrectPixelUsed() {
        assertEquals(pixelNameDialogDismissed(UsernameAndPassword, onboardingMode = true), AUTOFILL_ONBOARDING_SAVE_PROMPT_DISMISSED)
    }

    @Test
    fun whenNeverForThisSiteClickedInOnboardingDialogThenCorrectPixelUsed() {
        assertEquals(pixelNameDialogExclude(UsernameAndPassword, onboardingMode = true), AUTOFILL_ONBOARDING_SAVE_PROMPT_EXCLUDE)
    }

    @Test
    fun whenUsernameAndPasswordProvidedThenSaveTypeIsUsernameAndPassword() {
        val loginCredentials = loginCredentials(username = "username", password = "password")
        assertEquals(loginCredentials.saveType(), UsernameAndPassword)
    }

    @Test
    fun whenUsernameOnlyProvidedThenSaveTypeIsUsernameOnly() {
        val loginCredentials = loginCredentials(username = "username", password = null)
        assertEquals(loginCredentials.saveType(), UsernameOnly)
    }

    @Test
    fun whenPassworldOnlyProvidedThenSaveTypeIsUsernameOnly() {
        val loginCredentials = loginCredentials(username = null, password = "password")
        assertEquals(loginCredentials.saveType(), PasswordOnly)
    }

    private fun loginCredentials(
        username: String? = null,
        password: String? = null,
    ): LoginCredentials {
        return LoginCredentials(id = 0, domain = "example.com", username = username, password = password)
    }
}
