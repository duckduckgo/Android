/*
 * Copyright (c) 2025 DuckDuckGo
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

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import com.duckduckgo.autofill.api.AutofillEventListener
import com.duckduckgo.autofill.api.AutofillFragmentResultsPlugin
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google.ImportFromGooglePasswordsDialog.ImportPasswordsDialog
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import logcat.logcat

@ContributesMultibinding(AppScope::class)
class ResultHandlerImportPasswords @Inject constructor() : AutofillFragmentResultsPlugin {

    override fun processResult(
        result: Bundle,
        context: Context,
        tabId: String,
        fragment: Fragment,
        autofillCallback: AutofillEventListener,
    ) {
        logcat { "Autofill: processing import passwords result for tab $tabId" }
        if (result.getBoolean(ImportPasswordsDialog.KEY_IMPORT_SUCCESS)) {
            logcat { "Autofill: refresh after import passwords success" }
            autofillCallback.onNewPasswordsImported()
        } else {
            logcat { "Autofill: import didn't succeed; returning a 'no credential' response" }
            val originalUrl = result.getString(ImportPasswordsDialog.KEY_URL) ?: return
            autofillCallback.onNoCredentialsChosenForAutofill(originalUrl)
        }
    }

    override fun resultKey(tabId: String): String {
        return ImportPasswordsDialog.resultKey(tabId)
    }
}
