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

package com.duckduckgo.autofill.impl.ui.credential.management.sorting

import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.urlmatcher.AutofillUrlMatcher
import com.duckduckgo.di.scopes.FragmentScope
import com.squareup.anvil.annotations.ContributesBinding
import logcat.LogPriority.VERBOSE
import logcat.logcat
import java.lang.Character.*
import java.text.Normalizer
import javax.inject.Inject

interface InitialExtractor {
    fun extractInitial(loginCredentials: LoginCredentials): String
    fun extractInitialFromTitle(loginCredentials: LoginCredentials): String?
    fun extractInitialFromDomain(loginCredentials: LoginCredentials): String?
}

@ContributesBinding(FragmentScope::class)
class CredentialInitialExtractor @Inject constructor(
    private val autofillUrlMatcher: AutofillUrlMatcher,
) : InitialExtractor {

    override fun extractInitial(loginCredentials: LoginCredentials): String {
        val rawInitial =
            extractInitialFromTitle(loginCredentials) ?: extractInitialFromDomain(loginCredentials) ?: return INITIAL_CHAR_FOR_NON_LETTERS

        return when (val type = getCharacterType(rawInitial)) {
            LOWERCASE_LETTER,
            UPPERCASE_LETTER,
            OTHER_LETTER,
            -> {
                Normalizer.normalize(rawInitial, Normalizer.Form.NFKD).firstOrNull()?.toString() ?: INITIAL_CHAR_FOR_NON_LETTERS
            }
            DECIMAL_DIGIT_NUMBER -> INITIAL_CHAR_FOR_NON_LETTERS
            else -> {
                logcat(VERBOSE) { "Rejecting type $type for $rawInitial" }
                INITIAL_CHAR_FOR_NON_LETTERS
            }
        }
    }

    private fun getCharacterType(rawInitial: String): Byte {
        val initial = rawInitial.firstOrNull() ?: return UNASSIGNED
        return getType(initial).toByte()
    }

    override fun extractInitialFromTitle(loginCredentials: LoginCredentials): String? {
        return loginCredentials.domainTitle?.firstOrNull()?.uppercaseChar()?.toString()
    }

    override fun extractInitialFromDomain(loginCredentials: LoginCredentials): String? {
        return autofillUrlMatcher.extractUrlPartsForAutofill(loginCredentials.domain).userFacingETldPlus1?.firstOrNull()?.uppercaseChar()?.toString()
    }

    companion object {
        const val INITIAL_CHAR_FOR_NON_LETTERS = "#"
    }
}
