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

package com.duckduckgo.autofill.ui.credential.management

import com.duckduckgo.autofill.AutofillDomainFormatter
import com.duckduckgo.autofill.domain.app.LoginCredentials
import com.duckduckgo.di.scopes.FragmentScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface InitialExtractor {
    fun extractInitial(loginCredentials: LoginCredentials): Char
    fun extractInitialFromTitle(loginCredentials: LoginCredentials): Char?
    fun extractInitialFromDomain(loginCredentials: LoginCredentials): Char?
}

@ContributesBinding(FragmentScope::class)
class CredentialInitialExtractor @Inject constructor(
    private val domainFormatter: AutofillDomainFormatter,
    private val characterValidator: AutofillCharacterValidator
) : InitialExtractor {

    override fun extractInitial(loginCredentials: LoginCredentials): Char {
        val initial = extractInitialFromTitle(loginCredentials) ?: extractInitialFromDomain(loginCredentials)

        if (initial == null || !characterValidator.isLetter(initial)) {
            return INITIAL_CHAR_FOR_NON_LETTERS
        }

        return initial.uppercaseChar()
    }

    override fun extractInitialFromTitle(loginCredentials: LoginCredentials): Char? {
        return loginCredentials.domainTitle?.firstOrNull()?.uppercaseChar()
    }

    override fun extractInitialFromDomain(loginCredentials: LoginCredentials): Char? {
        return domainFormatter.extractDomain(loginCredentials.domain)?.firstOrNull()?.uppercaseChar()
    }

    companion object {
        const val INITIAL_CHAR_FOR_NON_LETTERS = '#'
    }
}
