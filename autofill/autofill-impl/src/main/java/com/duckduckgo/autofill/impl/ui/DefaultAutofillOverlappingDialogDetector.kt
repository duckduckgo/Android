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

import androidx.fragment.app.FragmentManager
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.api.CredentialAutofillPickerDialog
import com.duckduckgo.autofill.api.CredentialSavePickerDialog
import com.duckduckgo.autofill.api.CredentialUpdateExistingCredentialsDialog
import com.duckduckgo.autofill.api.EmailProtectionChooseEmailDialog
import com.duckduckgo.autofill.api.EmailProtectionInContextSignUpDialog
import com.duckduckgo.autofill.api.UseGeneratedPasswordDialog
import com.duckduckgo.autofill.api.dialog.AutofillOverlappingDialogDetector
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames
import com.duckduckgo.di.scopes.FragmentScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import timber.log.Timber

@ContributesBinding(FragmentScope::class)
class DefaultAutofillOverlappingDialogDetector @Inject constructor(
    private val pixel: Pixel,
) : AutofillOverlappingDialogDetector {

    override fun detectOverlappingDialogs(
        fragmentManager: FragmentManager,
        tag: String,
        urlMatch: Boolean,
    ) {
        val existingTags = detectVisibleFragments(fragmentManager)
        val formattedTags = existingTags.joinToString(",")
        Timber.v("Found %d existing autofill tags: [ %s ]", existingTags.size, formattedTags)

        if (existingTags.isNotEmpty()) {
            pixel.fire(
                AutofillPixelNames.AUTOFILL_OVERLAPPING_DIALOG,
                mapOf(
                    KEY_URL_MATCH to urlMatch.toString(),
                    KEY_NEW_DIALOG_TAG to tag,
                    KEY_EXISTING_DIALOG_TAGS to formattedTags,
                ),
            )
        }
    }

    private fun detectVisibleFragments(fragmentManager: FragmentManager): List<String> =
        autofillTags.mapNotNull { tag ->
            if (fragmentManager.findFragmentByTag(tag) != null) tag else null
        }

    companion object {
        private val autofillTags = listOf(
            CredentialSavePickerDialog.TAG,
            CredentialUpdateExistingCredentialsDialog.TAG,
            UseGeneratedPasswordDialog.TAG,
            CredentialAutofillPickerDialog.TAG,
            EmailProtectionChooseEmailDialog.TAG,
            EmailProtectionInContextSignUpDialog.TAG,
        )

        private const val KEY_URL_MATCH = "urlMatch"
        private const val KEY_NEW_DIALOG_TAG = "newDialogTag"
        private const val KEY_EXISTING_DIALOG_TAGS = "existingDialogTags"
    }
}
