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

import com.duckduckgo.autofill.domain.app.LoginCredentials
import com.duckduckgo.autofill.ui.credential.management.CredentialInitialExtractor.Companion.INITIAL_CHAR_FOR_NON_LETTERS
import com.duckduckgo.di.scopes.FragmentScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface CredentialListSorter {
    fun sort(credentials: List<LoginCredentials>): List<LoginCredentials>
}

@ContributesBinding(FragmentScope::class)
class CredentialListSorterByTitleAndDomain @Inject constructor(
    private val initialExtractor: InitialExtractor,
    private val characterValidator: AutofillCharacterValidator
) : CredentialListSorter {

    override fun sort(credentials: List<LoginCredentials>): List<LoginCredentials> {
        return credentials.sortedWith(compareBy(titleOrDomainSort()))
    }

    private fun titleOrDomainSort(): (LoginCredentials) -> Comparable<String>? = {
        val sb = StringBuilder()

        // if we don't have a usable title or domain, return the placeholder so it's sorted first
        if (!it.canUseDomainInSort() && !it.canUseTitleInSort()) {
            sb.append(INITIAL_CHAR_FOR_NON_LETTERS)
        }

        // if we can use the title, use it
        if (it.canUseTitleInSort()) {
            addLetterOrPlaceholder(initialExtractor.extractInitialFromTitle(it), sb)
        }

        addLetterOrPlaceholder(initialExtractor.extractInitialFromDomain(it), sb)

        sb.toString()
    }

    private fun addLetterOrPlaceholder(initial: Char?, sb: StringBuilder) {
        if (initial == null) return

        if (characterValidator.isLetter(initial)) {
            sb.append(initial.toString())
        } else {
            sb.append(INITIAL_CHAR_FOR_NON_LETTERS)
            sb.append(initial.toString())
        }
    }

    private fun LoginCredentials.canUseTitleInSort(): Boolean {
        return initialExtractor.extractInitialFromTitle(this) != null
    }

    private fun LoginCredentials.canUseDomainInSort(): Boolean {
        return initialExtractor.extractInitialFromDomain(this) != null
    }
}
