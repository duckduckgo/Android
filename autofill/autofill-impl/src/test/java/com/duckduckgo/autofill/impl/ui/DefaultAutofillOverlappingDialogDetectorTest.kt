/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.autofill.impl.ui

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.COUNT
import com.duckduckgo.autofill.api.CredentialSavePickerDialog
import com.duckduckgo.autofill.api.UseGeneratedPasswordDialog
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_OVERLAPPING_DIALOG
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class DefaultAutofillOverlappingDialogDetectorTest {

    private val pixel: Pixel = mock()
    private val fragmentManager: FragmentManager = mock()

    private val testee: DefaultAutofillOverlappingDialogDetector = DefaultAutofillOverlappingDialogDetector(pixel)

    @Test
    fun whenNoFragmentsShowingThenNoPixelSent() {
        whenever(fragmentManager.findFragmentByTag(any())).thenReturn(null)
        testee.detectOverlappingDialogs(fragmentManager, NEW_DIALOG_TAG, urlMatch = true)
        verifyNoInteractions(pixel)
    }

    @Test
    fun whenSingleFragmentShowingThenIncludedInPixelSent() {
        val urlMatch = true
        val existingTags = listOf(CredentialSavePickerDialog.TAG)
        configureFragmentShowing(existingTags)

        testee.detectOverlappingDialogs(fragmentManager, NEW_DIALOG_TAG, urlMatch)
        verifyOverlapPixelSent(NEW_DIALOG_TAG, existingTags.joinToString(","), urlMatch)
    }

    @Test
    fun whenMultipleFragmentsShowingThenAllIncludedInPixelSent() {
        val urlMatch = true
        val existingTags = listOf(CredentialSavePickerDialog.TAG, UseGeneratedPasswordDialog.TAG)
        configureFragmentShowing(existingTags)

        testee.detectOverlappingDialogs(fragmentManager, NEW_DIALOG_TAG, urlMatch)
        verifyOverlapPixelSent(NEW_DIALOG_TAG, existingTags.joinToString(","), urlMatch)
    }

    @Test
    fun whenNotAUrlMatchThenPixelParamSetCorrectly() {
        val urlMatch = false
        val existingTags = listOf(CredentialSavePickerDialog.TAG)
        configureFragmentShowing(existingTags)

        testee.detectOverlappingDialogs(fragmentManager, NEW_DIALOG_TAG, urlMatch)
        verifyOverlapPixelSent(NEW_DIALOG_TAG, existingTags.joinToString(","), urlMatch)
    }

    private fun verifyOverlapPixelSent(
        newDialogTag: String,
        existingTags: String,
        urlMatch: Boolean,
    ) {
        val expectedMap = mapOf(
            "urlMatch" to urlMatch.toString(),
            "newDialogTag" to newDialogTag,
            "existingDialogTags" to existingTags,
        )
        verify(pixel).fire(eq(AUTOFILL_OVERLAPPING_DIALOG), eq(expectedMap), any(), eq(COUNT))
    }

    private fun configureFragmentShowing(tags: List<String>) {
        tags.forEach { tag ->
            whenever(fragmentManager.findFragmentByTag(tag)).thenReturn(DUMMY_FRAGMENT)
        }
    }

    companion object {
        private val DUMMY_FRAGMENT = Fragment()
        private const val NEW_DIALOG_TAG = "dummy-tag-to-show"
    }
}
