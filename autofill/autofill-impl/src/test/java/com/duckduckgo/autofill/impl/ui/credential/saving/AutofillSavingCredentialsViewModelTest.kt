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

import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.store.AutofillStore
import com.duckduckgo.autofill.impl.R
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class AutofillSavingCredentialsViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val mockStore: AutofillStore = mock()
    private val testee = AutofillSavingCredentialsViewModel(coroutineTestRule.testDispatcherProvider).also { it.autofillStore = mockStore }

    @Test
    fun whenShowingOnboardingThenTitleResourceIsAlwaysOnboardingTitle() {
        whenever(mockStore.showOnboardingWhenOfferingToSaveLogin).thenReturn(true)
        val expectedResource = R.string.saveLoginDialogFirstTimeOnboardingExplanationTitle

        assertEquals(expectedResource, testee.determineTextResources(usernamePresent()).title)
        assertEquals(expectedResource, testee.determineTextResources(usernameMissing()).title)
    }

    @Test
    fun whenShowingOnboardingAndUsernamePresentThenCtaButtonResourceIsSaveLogin() {
        whenever(mockStore.showOnboardingWhenOfferingToSaveLogin).thenReturn(true)
        val expectedResource = R.string.saveLoginDialogButtonSave
        assertEquals(expectedResource, testee.determineTextResources(usernamePresent()).ctaButton)
    }

    @Test
    fun whenShowingOnboardingAndUsernameMissingThenCtaButtonResourceIsSaveLogin() {
        whenever(mockStore.showOnboardingWhenOfferingToSaveLogin).thenReturn(true)
        val expectedResource = R.string.saveLoginDialogButtonSave
        assertEquals(expectedResource, testee.determineTextResources(usernameMissing()).ctaButton)
    }

    @Test
    fun whenUserPromptedToSaveThenFlagSet() = runTest {
        testee.userPromptedToSaveCredentials()
        verify(mockStore).hasEverBeenPromptedToSaveLogin = true
    }

    private fun usernamePresent() = loginCredentialsWithUsername(username = "foo")
    private fun usernameMissing() = loginCredentialsWithUsername(username = null)

    private fun loginCredentialsWithUsername(username: String?): LoginCredentials {
        return LoginCredentials(username = username, password = "bar", domain = "example.com")
    }
}
