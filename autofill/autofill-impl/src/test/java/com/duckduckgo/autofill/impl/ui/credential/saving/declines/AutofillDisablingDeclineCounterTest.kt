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

package com.duckduckgo.autofill.impl.ui.credential.saving.declines

import com.duckduckgo.autofill.store.AutofillPrefsStore
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AutofillDisablingDeclineCounterTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val autofillPrefsStore: AutofillPrefsStore = mock()
    private lateinit var testee: AutofillDisablingDeclineCounter

    @Before
    fun before() {
        whenever(autofillPrefsStore.autofillDeclineCount).thenReturn(0)
        whenever(autofillPrefsStore.monitorDeclineCounts).thenReturn(true)
        whenever(autofillPrefsStore.autofillStateSetByUser).thenReturn(false)
    }

    @Test
    fun whenInitialisedThenNoPreviouslyStoredDomain() = runTest {
        initialiseDeclineCounter()
        assertNull(testee.currentSessionPreviousDeclinedDomain)
    }

    @Test
    fun whenNotMonitoringDeclineCountsThenShouldNotRecordNewDeclines() = runTest {
        whenever(autofillPrefsStore.monitorDeclineCounts).thenReturn(false)
        initialiseDeclineCounter()

        testee.userDeclinedToSaveCredentials("example.com")
        assertDeclineNotRecorded()
    }

    @Test
    fun whenUserEnabledAutofillThenShouldNotRecordNewDeclines() = runTest {
        whenever(autofillPrefsStore.autofillStateSetByUser).thenReturn(true)
        initialiseDeclineCounter()

        testee.userDeclinedToSaveCredentials("example.com")
        assertDeclineNotRecorded()
    }

    @Test
    fun whenMonitoringDeclineCountsThenShouldRecordNewDeclines() = runTest {
        whenever(autofillPrefsStore.monitorDeclineCounts).thenReturn(true)
        initialiseDeclineCounter()

        testee.userDeclinedToSaveCredentials("example.com")
        assertDeclineRecorded(expectedNewValue = 1)
    }

    @Test
    fun whenNewDomainMatchesOldDomainThenDeclineNotRecorded() = runTest {
        initialiseDeclineCounter()
        testee.currentSessionPreviousDeclinedDomain = "example.com"
        testee.userDeclinedToSaveCredentials("example.com")
        assertDeclineNotRecorded()
    }

    @Test
    fun whenNewDomainDoesNotMatchOldDomainThenDeclineRecorded() = runTest {
        initialiseDeclineCounter()
        testee.currentSessionPreviousDeclinedDomain = "foo.com"
        testee.userDeclinedToSaveCredentials("example.com")
        assertDeclineRecorded(expectedNewValue = 1)
    }

    @Test
    fun whenDeclineOnNewDomainWithNoPreviousDomainThenDomainStored() = runTest {
        initialiseDeclineCounter()
        testee.currentSessionPreviousDeclinedDomain = null
        testee.userDeclinedToSaveCredentials("example.com")
        assertEquals("example.com", testee.currentSessionPreviousDeclinedDomain)
    }

    @Test
    fun whenDeclineOnNewDomainWithAPreviousDomainThenDomainStored() = runTest {
        initialiseDeclineCounter()
        testee.currentSessionPreviousDeclinedDomain = "foo.com"
        testee.userDeclinedToSaveCredentials("example.com")
        assertEquals("example.com", testee.currentSessionPreviousDeclinedDomain)
    }

    @Test
    fun whenDeclineTotalCountBelowThresholdThenShouldNotOfferToDisable() = runTest {
        initialiseDeclineCounter()
        whenever(autofillPrefsStore.autofillDeclineCount).thenReturn(0)
        testee.userDeclinedToSaveCredentials("example.com")
        assertShouldNotPromptToDisableAutofill()
    }

    @Test
    fun whenDeclineIncreasesTotalCountAtThresholdAndNotPromptedBeforeThenShouldOfferToDisable() = runTest {
        initialiseDeclineCounter()
        configureNeverPromptedToDisableBefore()
        configureGlobalDeclineCountAtThreshold()
        testee.userDeclinedToSaveCredentials("a.com")
        assertShouldPromptToDisableAutofill()
    }

    @Test
    fun whenDeclineIncreasesTotalCountAtThresholdAndPromptedBeforeThenShouldNotOfferToDisable() = runTest {
        initialiseDeclineCounter()
        configurePromptedToDisableBefore()
        configureGlobalDeclineCountAtThreshold()
        testee.userDeclinedToSaveCredentials("a.com")
        assertShouldNotPromptToDisableAutofill()
    }

    private fun configureGlobalDeclineCountAtThreshold() {
        whenever(autofillPrefsStore.autofillDeclineCount).thenReturn(2)
    }

    private fun assertDeclineNotRecorded() {
        verify(autofillPrefsStore, never()).autofillDeclineCount = any()
    }

    private suspend fun assertShouldNotPromptToDisableAutofill() {
        assertFalse(testee.shouldPromptToDisableAutofill())
    }

    private suspend fun assertShouldPromptToDisableAutofill() {
        assertTrue(testee.shouldPromptToDisableAutofill())
    }

    @Suppress("SameParameterValue")
    private fun assertDeclineRecorded(expectedNewValue: Int) {
        verify(autofillPrefsStore).autofillDeclineCount = eq(expectedNewValue)
    }

    private fun TestScope.initialiseDeclineCounter() {
        testee = AutofillDisablingDeclineCounter(
            autofillPrefsStore = autofillPrefsStore,
            appCoroutineScope = this,
            dispatchers = coroutineTestRule.testDispatcherProvider,
        )
    }

    private fun configureNeverPromptedToDisableBefore() {
        whenever(autofillPrefsStore.timestampUserLastPromptedToDisableAutofill).thenReturn(null)
    }

    private fun configurePromptedToDisableBefore() {
        whenever(autofillPrefsStore.timestampUserLastPromptedToDisableAutofill).thenReturn(100)
    }
}
