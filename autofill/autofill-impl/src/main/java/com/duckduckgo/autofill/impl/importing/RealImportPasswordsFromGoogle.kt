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
import androidx.core.content.IntentCompat
import com.duckduckgo.autofill.api.ImportPasswordsFromGoogle
import com.duckduckgo.autofill.api.ImportPasswordsFromGoogle.ImportPasswordsResult
import com.duckduckgo.autofill.api.ImportPasswordsFromGoogle.ImportPasswordsStatus
import com.duckduckgo.autofill.impl.importing.CredentialImporter.ImportResult
import com.duckduckgo.autofill.impl.importing.capability.ImportGooglePasswordsCapabilityChecker
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordResult
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class RealImportPasswordsFromGoogle @Inject constructor(
    private val capabilityChecker: ImportGooglePasswordsCapabilityChecker,
    private val credentialImporter: CredentialImporter,
) : ImportPasswordsFromGoogle {

    override suspend fun isSupported(): Boolean = capabilityChecker.webViewCapableOfImporting()

    override fun parseResult(data: Intent?): ImportPasswordsResult {
        val result = data?.let {
            IntentCompat.getParcelableExtra(it, ImportGooglePasswordResult.RESULT_KEY_DETAILS, ImportGooglePasswordResult::class.java)
        }
        return when (result) {
            is ImportGooglePasswordResult.Success -> ImportPasswordsResult.Success
            is ImportGooglePasswordResult.UserCancelled -> ImportPasswordsResult.UserCancelled
            is ImportGooglePasswordResult.Error -> ImportPasswordsResult.Error
            null -> ImportPasswordsResult.Error
        }
    }

    override fun importStatus(): Flow<ImportPasswordsStatus> = credentialImporter.getImportStatus().map {
        when (it) {
            is ImportResult.InProgress -> ImportPasswordsStatus.InProgress
            is ImportResult.Finished -> ImportPasswordsStatus.Finished(imported = it.savedCredentials, skipped = it.numberSkipped)
        }
    }
}
