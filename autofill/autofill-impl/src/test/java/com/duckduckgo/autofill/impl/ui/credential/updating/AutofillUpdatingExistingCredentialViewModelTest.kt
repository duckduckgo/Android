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

package com.duckduckgo.autofill.impl.ui.credential.updating

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutofillUpdatingExistingCredentialViewModelTest {

    private val testee = AutofillUpdatingExistingCredentialViewModel()

    @Test
    fun whenUsernameIsShortThenNoEllipsizing() {
        val result = testee.ellipsizeIfNecessary("foo")
        result.assertDoesNotEndInEllipsis()
        assertEquals("foo", result)
    }

    @Test
    fun whenUsernameIsExactlyOnLimitThenNoEllipsizing() {
        val usernameExactlyAsLongAsLimit = "A".repeat(50)
        val result = testee.ellipsizeIfNecessary(usernameExactlyAsLongAsLimit)
        result.assertDoesNotEndInEllipsis()
        assertEquals(usernameExactlyAsLongAsLimit, result)
    }

    @Test
    fun whenUsernameIsLongerThanLimitThenEllipsizing() {
        val usernameLongerThanLimit = "A".repeat(51)
        val result = testee.ellipsizeIfNecessary(usernameLongerThanLimit)
        result.assertEndsInEllipsis()
        assertEquals(50, result.length)
    }

    private fun String.assertEndsInEllipsis() {
        assertTrue(endsWith(Typography.ellipsis))
    }

    private fun String.assertDoesNotEndInEllipsis() {
        assertFalse(endsWith(Typography.ellipsis))
    }
}
