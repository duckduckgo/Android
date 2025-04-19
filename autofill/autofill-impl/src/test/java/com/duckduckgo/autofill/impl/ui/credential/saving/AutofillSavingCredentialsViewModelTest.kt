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

import app.cash.turbine.test
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.autofill.impl.store.NeverSavedSiteRepository
import com.duckduckgo.autofill.impl.ui.credential.saving.declines.AutofillDeclineCounter
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AutofillSavingCredentialsViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val mockStore: InternalAutofillStore = mock()
    private val neverSavedSiteRepository: NeverSavedSiteRepository = mock()
    private val autofillDeclineCounter: AutofillDeclineCounter = mock<AutofillDeclineCounter>()

    private lateinit var testee: AutofillSavingCredentialsViewModel

    @Test
    fun whenUserDeclineCounterActiveAndCounterLessThanTwoThenExpandedDialogShown() = runTest {
        initialiseWithValues(declineCount = 1, isDeclineCounterActive = true)
        testee.viewState.test {
            testee.userPromptedToSaveCredentials()
            val viewState = awaitItem()
            assertTrue(viewState.expandedDialog)
        }
    }

    @Test
    fun whenUserDeclineCounterNotActiveThenDoNotShownExpandedVersion() = runTest {
        initialiseWithValues(declineCount = 1, isDeclineCounterActive = false)
        testee.viewState.test {
            testee.userPromptedToSaveCredentials()
            val viewState = awaitItem()
            assertFalse(viewState.expandedDialog)
        }
    }

    @Test
    fun whenCounterAboveThresholdThenDoNotShownExpandedVersion() = runTest {
        initialiseWithValues(declineCount = 3, isDeclineCounterActive = true)
        testee.viewState.test {
            testee.userPromptedToSaveCredentials()
            val viewState = awaitItem()
            assertFalse(viewState.expandedDialog)
        }
    }

    @Test
    fun whenUserPromptedToSaveThenFlagSet() = runTest {
        initialiseWithValues(declineCount = 1, isDeclineCounterActive = true)
        testee.userPromptedToSaveCredentials()
        verify(mockStore).hasEverBeenPromptedToSaveLogin = true
    }

    @Test
    fun whenUserSpecifiesNeverToSaveCurrentSiteThenSitePersisted() = runTest {
        initialiseWithValues(declineCount = 1, isDeclineCounterActive = true)
        val url = "https://example.com"
        testee.addSiteToNeverSaveList(url)
        verify(neverSavedSiteRepository).addToNeverSaveList(eq(url))
    }

    private suspend fun initialiseWithValues(declineCount: Int, isDeclineCounterActive: Boolean) {
        whenever(autofillDeclineCounter.declineCount()).thenReturn(declineCount)
        whenever(autofillDeclineCounter.isDeclineCounterActive()).thenReturn(isDeclineCounterActive)
        testee = AutofillSavingCredentialsViewModel(
            neverSavedSiteRepository = neverSavedSiteRepository,
            dispatchers = coroutineTestRule.testDispatcherProvider,
            autofillStore = mockStore,
            autofillDeclineCounter = autofillDeclineCounter,
        )
    }
}
