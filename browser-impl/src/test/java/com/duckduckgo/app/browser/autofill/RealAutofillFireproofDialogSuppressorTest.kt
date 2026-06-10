/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.app.browser.autofill

import com.duckduckgo.autofill.impl.RealAutofillFireproofDialogSuppressor
import com.duckduckgo.autofill.impl.time.TimeProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RealAutofillFireproofDialogSuppressorTest {

    private val timeProvider: TimeProvider = mock()
    private val testee = RealAutofillFireproofDialogSuppressor(timeProvider)

    @Before
    fun before() {
        configureTimeNow()
    }

    @Test
    fun whenNoInteractionsThenNotPreventingPrompts() {
        assertFalse(testee.isAutofillPreventingFireproofPrompts())
    }

    @Test
    fun whenSaveOrUpdateDialogVisibleThenPreventingPrompts() {
        testee.autofillSaveOrUpdateDialogVisibilityChanged(true)
        assertTrue(testee.isAutofillPreventingFireproofPrompts())
    }

    @Test
    fun whenSaveOrUpdateDialogDismissedRecentlyThenPreventingPrompts() {
        testee.autofillSaveOrUpdateDialogVisibilityChanged(true)
        testee.autofillSaveOrUpdateDialogVisibilityChanged(false)
        assertTrue(testee.isAutofillPreventingFireproofPrompts())
    }

    @Test
    fun whenSaveOrUpdateDialogDismissedAWhileBackThenPreventingPrompts() {
        testee.autofillSaveOrUpdateDialogVisibilityChanged(true)
        testee.autofillSaveOrUpdateDialogVisibilityChanged(false)
        configureTimeNow(System.currentTimeMillis() + 20_000)
        assertFalse(testee.isAutofillPreventingFireproofPrompts())
    }

    private fun configureTimeNow(timeMillis: Long = System.currentTimeMillis()) {
        whenever(timeProvider.currentTimeMillis()).thenReturn(timeMillis)
    }
}
