/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.autofill.impl.importing

import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface ImportedCredentialValidator {
    fun isValid(loginCredentials: LoginCredentials): Boolean
}

@ContributesBinding(AppScope::class)
class DefaultImportedCredentialValidator @Inject constructor() : ImportedCredentialValidator {

    override fun isValid(loginCredentials: LoginCredentials): Boolean {
        with(loginCredentials) {
            if (domain?.startsWith(APP_PASSWORD_PREFIX) == true) return false

            if (allFieldsEmpty()) {
                return false
            }

            return true
        }
    }

    private fun LoginCredentials.allFieldsEmpty(): Boolean {
        return domain.isNullOrBlank() &&
            username.isNullOrBlank() &&
            password.isNullOrBlank() &&
            domainTitle.isNullOrBlank() &&
            notes.isNullOrBlank()
    }

    companion object {
        private const val APP_PASSWORD_PREFIX = "android://"
    }
}
