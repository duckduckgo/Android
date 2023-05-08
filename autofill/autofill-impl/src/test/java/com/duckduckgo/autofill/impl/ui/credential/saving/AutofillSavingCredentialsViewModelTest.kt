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
import com.duckduckgo.autofill.api.store.AutofillStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@ExperimentalCoroutinesApi
class AutofillSavingCredentialsViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val mockStore: AutofillStore = mock()
    private val testee = AutofillSavingCredentialsViewModel(coroutineTestRule.testDispatcherProvider).also { it.autofillStore = mockStore }

    @Test
    fun whenUserPromptedToSaveThenFlagSet() = runTest {
        testee.userPromptedToSaveCredentials()
        verify(mockStore).hasEverBeenPromptedToSaveLogin = true
    }
}
