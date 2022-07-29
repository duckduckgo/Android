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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LatinCharacterValidatorTest {

    private val testee = LatinCharacterValidator()

    @Test
    fun whenLowercaseLetterAStartOfRangeThenIsClassedAsLetter() {
        assertTrue(testee.isLetter('a'))
    }

    @Test
    fun whenLowercaseLetterAtEndOfRangeThenIsClassedAsLetter() {
        assertTrue(testee.isLetter('z'))
    }

    @Test
    fun whenUpperLetterAtStartOfRangeThenIsClassedAsLetter() {
        assertTrue(testee.isLetter('A'))
    }

    @Test
    fun whenUpperLetterAtEndOfRangeThenIsClassedAsLetter() {
        assertTrue(testee.isLetter('Z'))
    }

    @Test
    fun whenNumberThenNotClassedAsLetter() {
        assertFalse(testee.isLetter('1'))
    }

    @Test
    fun whenCharacterThenNotClassedAsLetter() {
        assertFalse(testee.isLetter('$'))
    }

    @Test
    fun whenNonLatinCharacterThenNotClassedAsLetter() {
        assertFalse(testee.isLetter('à'))
    }

}
