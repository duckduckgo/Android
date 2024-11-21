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

package com.duckduckgo.autofill.impl.ui.credential.management.importpassword

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.impl.engagement.store.AutofillEngagementBucketing
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordsWebFlowViewModel.UserCannotImportReason
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordsWebFlowViewModel.UserCannotImportReason.ErrorParsingCsv
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_IMPORT_GOOGLE_PASSWORDS_EMPTY_STATE_CTA_BUTTON_TAPPED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_IMPORT_GOOGLE_PASSWORDS_OVERFLOW_MENU
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_IMPORT_GOOGLE_PASSWORDS_PREIMPORT_PROMPT_CONFIRMED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_IMPORT_GOOGLE_PASSWORDS_PREIMPORT_PROMPT_DISPLAYED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_IMPORT_GOOGLE_PASSWORDS_RESULT_FAILURE_ERROR_PARSING
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_IMPORT_GOOGLE_PASSWORDS_RESULT_FAILURE_USER_CANCELLED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_IMPORT_GOOGLE_PASSWORDS_RESULT_SUCCESS
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SYNC_DESKTOP_PASSWORDS_CTA_BUTTON
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SYNC_DESKTOP_PASSWORDS_OVERFLOW_MENU
import com.duckduckgo.di.scopes.FragmentScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface ImportPasswordsPixelSender {
    fun onImportPasswordsDialogDisplayed()
    fun onImportPasswordsDialogImportButtonClicked()
    fun onUserCancelledImportPasswordsDialog()
    fun onUserCancelledImportWebFlow(stage: String)
    fun onImportSuccessful(savedCredentials: Int, numberSkipped: Int)
    fun onImportFailed(reason: UserCannotImportReason)
    fun onImportPasswordsButtonTapped()
    fun onImportPasswordsOverflowMenuTapped()
    fun onImportPasswordsViaDesktopSyncButtonTapped()
    fun onImportPasswordsViaDesktopSyncOverflowMenuTapped()
}

@ContributesBinding(FragmentScope::class)
class ImportPasswordsPixelSenderImpl @Inject constructor(
    private val pixel: Pixel,
    private val engagementBucketing: AutofillEngagementBucketing,
) : ImportPasswordsPixelSender {

    override fun onImportPasswordsDialogDisplayed() {
        pixel.fire(AUTOFILL_IMPORT_GOOGLE_PASSWORDS_PREIMPORT_PROMPT_DISPLAYED)
    }

    override fun onImportPasswordsDialogImportButtonClicked() {
        pixel.fire(AUTOFILL_IMPORT_GOOGLE_PASSWORDS_PREIMPORT_PROMPT_CONFIRMED)
    }

    override fun onUserCancelledImportPasswordsDialog() {
        val params = mapOf(CANCELLATION_STAGE_KEY to PRE_IMPORT_DIALOG_STAGE)
        pixel.fire(AUTOFILL_IMPORT_GOOGLE_PASSWORDS_RESULT_FAILURE_USER_CANCELLED, params)
    }

    override fun onUserCancelledImportWebFlow(stage: String) {
        val params = mapOf(CANCELLATION_STAGE_KEY to stage)
        pixel.fire(AUTOFILL_IMPORT_GOOGLE_PASSWORDS_RESULT_FAILURE_USER_CANCELLED, params)
    }

    override fun onImportSuccessful(savedCredentials: Int, numberSkipped: Int) {
        val savedCredentialsBucketed = engagementBucketing.bucketNumberOfCredentials(savedCredentials)
        val skippedCredentialsBucketed = engagementBucketing.bucketNumberOfCredentials(numberSkipped)
        val params = mapOf(
            "saved_credentials" to savedCredentialsBucketed,
            "skipped_credentials" to skippedCredentialsBucketed,
        )
        pixel.fire(AUTOFILL_IMPORT_GOOGLE_PASSWORDS_RESULT_SUCCESS, params)
    }

    override fun onImportFailed(reason: UserCannotImportReason) {
        val pixelName = when (reason) {
            ErrorParsingCsv -> AUTOFILL_IMPORT_GOOGLE_PASSWORDS_RESULT_FAILURE_ERROR_PARSING
        }
        pixel.fire(pixelName)
    }

    override fun onImportPasswordsButtonTapped() {
        pixel.fire(AUTOFILL_IMPORT_GOOGLE_PASSWORDS_EMPTY_STATE_CTA_BUTTON_TAPPED)
    }

    override fun onImportPasswordsOverflowMenuTapped() {
        pixel.fire(AUTOFILL_IMPORT_GOOGLE_PASSWORDS_OVERFLOW_MENU)
    }

    override fun onImportPasswordsViaDesktopSyncButtonTapped() {
        pixel.fire(AUTOFILL_SYNC_DESKTOP_PASSWORDS_CTA_BUTTON)
    }

    override fun onImportPasswordsViaDesktopSyncOverflowMenuTapped() {
        pixel.fire(AUTOFILL_SYNC_DESKTOP_PASSWORDS_OVERFLOW_MENU)
    }

    companion object {
        private const val CANCELLATION_STAGE_KEY = "stage"
        private const val PRE_IMPORT_DIALOG_STAGE = "pre-import-dialog"
    }
}
