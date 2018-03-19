/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.browser.filechooser

import android.content.Intent
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FileChooserIntentBuilderTest {

    private lateinit var testee: FileChooserIntentBuilder

    @Before
    fun setup() {
        testee = FileChooserIntentBuilder()
    }

    @Test
    fun whenIntentBuiltThenReadUriPermissionFlagSet() {
        val output = testee.intent(emptyArray())
        assertTrue("Intent.FLAG_GRANT_READ_URI_PERMISSION flag not set on intent", output.hasFlagSet(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    @Test
    fun whenIntentBuiltThenAcceptTypeSetToAll() {
        val output = testee.intent(emptyArray())
        assertEquals("*/*", output.type)
    }

    @Test
    fun whenMultipleModeDisabledThenIntentExtraReturnsFalse() {
        val output = testee.intent(emptyArray(), canChooseMultiple = false)
        assertFalse(output.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false))
    }

    @Test
    fun whenRequestedTypesAreMissingThenShouldNotAddMimeTypeExtra() {
        val output = testee.intent(emptyArray())
        assertFalse(output.hasExtra(Intent.EXTRA_MIME_TYPES))
    }

    @Test
    fun whenRequestedTypesArePresentThenShouldAddMimeTypeExtra() {
        val output = testee.intent(arrayOf("image/png", "image/gif"))
        assertTrue(output.hasExtra(Intent.EXTRA_MIME_TYPES))
    }

    @Test
    fun whenUpperCaseTypesGivenThenNormalisedToLowercase() {
        val output = testee.intent(arrayOf("ImAgE/PnG"))
        assertEquals("image/png", output.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)[0])
    }

    @Test
    fun whenEmptyTypesGivenThenNotIncludedInOutput() {
        val output = testee.intent(arrayOf("image/png", "", " ", "image/gif"))
        val mimeTypes = output.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)
        assertEquals(2, mimeTypes.size)
        assertEquals("image/png", mimeTypes[0])
        assertEquals("image/gif", mimeTypes[1])
    }

    @Test
    fun whenMultipleModeEnabledThenIntentExtraReturnsTrue() {
        val output = testee.intent(emptyArray(), canChooseMultiple = true)
        assertTrue(output.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false))
    }

    private fun Intent.hasFlagSet(expectedFlag: Int): Boolean {
        val actual = flags and expectedFlag
        return expectedFlag == actual
    }
}