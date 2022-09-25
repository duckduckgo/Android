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

package com.duckduckgo.autofill.ui.credential.updating

import com.duckduckgo.autofill.domain.app.LoginCredentials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutofillUpdatingExistingCredentialViewModelTest {

    private val testee = AutofillUpdatingExistingCredentialViewModel()

    @Test
    fun whenCredentialsAreEmptyThenEmptyStringReturned() {
        val credentials = credentials(password = "")
        val actual = testee.convertPasswordToMaskedView(credentials)
        assertTrue(actual.isEmpty())
    }

    @Test
    fun whenCredentialsAreNotEmptyThenMaskedCharactersReturned() {
        val credentials = credentials(password = "hello")
        val actual = testee.convertPasswordToMaskedView(credentials)
        assertEquals("•••••", actual)
    }

    private fun credentials(password: String?): LoginCredentials {
        return LoginCredentials(password = password, username = null, domain = null)
    }
}
