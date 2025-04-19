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

package com.duckduckgo.autofill.impl.deviceauth

import com.duckduckgo.autofill.impl.time.TimeProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AutofillTimeBasedAuthorizationGracePeriodTest {

    private val timeProvider: TimeProvider = mock()
    private val testee = AutofillTimeBasedAuthorizationGracePeriod(timeProvider)

    @Test
    fun whenNoInteractionsThenAuthRequired() {
        assertTrue(testee.isAuthRequired())
    }

    @Test
    fun whenLastSuccessfulAuthWasBeforeGracePeriodThenAuthRequired() {
        recordAuthorizationInDistantPast()
        timeProvider.reset()
        assertTrue(testee.isAuthRequired())
    }

    @Test
    fun whenLastSuccessfulAuthWasWithinGracePeriodThenAuthNotRequired() {
        recordAuthorizationWithinGracePeriod()
        timeProvider.reset()
        assertFalse(testee.isAuthRequired())
    }

    @Test
    fun whenLastSuccessfulAuthWasWithinGracePeriodButInvalidatedThenAuthRequired() {
        recordAuthorizationWithinGracePeriod()
        timeProvider.reset()
        testee.invalidate()
        assertTrue(testee.isAuthRequired())
    }

    @Test
    fun whenLastSuccessfulAuthWasBeforeGracePeriodButWithinExtendedAuthTimeThenAuthNotRequired() {
        recordAuthorizationInDistantPast()
        timeProvider.reset()
        testee.requestExtendedGracePeriod()
        assertFalse(testee.isAuthRequired())
    }

    @Test
    fun whenNoPreviousAuthButWithinExtendedAuthTimeThenAuthNotRequired() {
        testee.requestExtendedGracePeriod()
        assertFalse(testee.isAuthRequired())
    }

    @Test
    fun whenExtendedAuthTimeRequestedButTooLongAgoThenAuthRequired() {
        configureExtendedAuthRequestedInDistantPast()
        timeProvider.reset()
        assertTrue(testee.isAuthRequired())
    }

    @Test
    fun whenExtendedAuthTimeRequestedAndThenRemovedThenAuthRequired() {
        testee.requestExtendedGracePeriod()
        testee.removeRequestForExtendedGracePeriod()
        assertTrue(testee.isAuthRequired())
    }

    private fun configureExtendedAuthRequestedInDistantPast() {
        whenever(timeProvider.currentTimeMillis()).thenReturn(0)
        testee.requestExtendedGracePeriod()
    }

    private fun recordAuthorizationInDistantPast() {
        whenever(timeProvider.currentTimeMillis()).thenReturn(0)
        testee.recordSuccessfulAuthorization()
    }

    private fun recordAuthorizationWithinGracePeriod() {
        whenever(timeProvider.currentTimeMillis()).thenReturn(System.currentTimeMillis())
        testee.recordSuccessfulAuthorization()
    }

    private fun TimeProvider.reset() = whenever(this.currentTimeMillis()).thenReturn(System.currentTimeMillis())
}
