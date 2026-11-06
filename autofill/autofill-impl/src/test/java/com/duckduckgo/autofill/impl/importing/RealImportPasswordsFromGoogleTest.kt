/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.autofill.impl.importing

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.autofill.api.ImportPasswordsFromGoogle.ImportPasswordsResult
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordResult
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordResult.Companion.RESULT_KEY_DETAILS
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordsWebFlowViewModel.UserCannotImportReason.ErrorParsingCsv
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordsWebFlowViewModel.UserCannotImportReason.WebViewCrash
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@RunWith(AndroidJUnit4::class)
class RealImportPasswordsFromGoogleTest {

    private val testee = RealImportPasswordsFromGoogle(
        capabilityChecker = mock(),
        credentialImporter = mock(),
    )

    @Test
    fun whenResultIsSuccessThenSuccessIsReturned() {
        assertEquals(ImportPasswordsResult.Success, testee.parseResult(intentWith(ImportGooglePasswordResult.Success)))
    }

    @Test
    fun whenResultIsUserCancelledThenUserCancelledIsReturned() {
        val result = testee.parseResult(intentWith(ImportGooglePasswordResult.UserCancelled(stage = "stage")))

        assertEquals(ImportPasswordsResult.UserCancelled, result)
    }

    @Test
    fun whenWebViewCrashedThenErrorIsTransient() {
        val result = testee.parseResult(intentWith(ImportGooglePasswordResult.Error(WebViewCrash)))

        assertEquals(ImportPasswordsResult.Error.Transient, result)
    }

    @Test
    fun whenCsvCouldNotBeParsedThenErrorIsPermanent() {
        val result = testee.parseResult(intentWith(ImportGooglePasswordResult.Error(ErrorParsingCsv)))

        assertEquals(ImportPasswordsResult.Error.Permanent, result)
    }

    @Test
    fun whenNoResultDetailsThenErrorIsTransient() {
        assertEquals(ImportPasswordsResult.Error.Transient, testee.parseResult(Intent()))
    }

    @Test
    fun whenNoIntentThenErrorIsTransient() {
        assertEquals(ImportPasswordsResult.Error.Transient, testee.parseResult(null))
    }

    private fun intentWith(result: ImportGooglePasswordResult): Intent =
        Intent().putExtra(RESULT_KEY_DETAILS, result)
}
