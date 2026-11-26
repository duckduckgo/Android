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

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.content.IntentCompat
import com.duckduckgo.autofill.api.ImportPasswords
import com.duckduckgo.autofill.api.ImportPasswords.ImportPasswordsParams
import com.duckduckgo.autofill.api.ImportPasswords.ImportPasswordsRequest
import com.duckduckgo.autofill.api.ImportPasswords.ImportPasswordsResult
import com.duckduckgo.autofill.api.ImportPasswords.ImportPasswordsStatus
import com.duckduckgo.autofill.impl.importing.CredentialImporter.ImportResult
import com.duckduckgo.autofill.impl.importing.capability.PasswordImportAvailability
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import dev.zacsweers.metro.ContributesBinding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class RealImportPasswords @Inject constructor(
    private val passwordImportAvailability: PasswordImportAvailability,
    private val credentialImporter: CredentialImporter,
    globalActivityStarter: GlobalActivityStarter,
) : ImportPasswords {

    private val contract = ImportPasswordsContract(globalActivityStarter)

    override suspend fun isSupported(): Boolean = passwordImportAvailability.canImport()

    override fun importContract(): ActivityResultContract<ImportPasswordsRequest, ImportPasswordsResult> = contract

    override suspend fun launchImport(
        params: ImportPasswordsParams,
        launcher: ActivityResultLauncher<ImportPasswordsRequest>,
    ) {
        launcher.launch(AvailabilityCheckedPasswordsRequest(isAvailable = isSupported(), params = params))
    }

    override fun importStatus(): Flow<ImportPasswordsStatus> = credentialImporter.getImportStatus().map { status ->
        when (status) {
            ImportResult.InProgress -> ImportPasswordsStatus.InProgress
            is ImportResult.Finished -> ImportPasswordsStatus.Finished(imported = status.savedCredentials, skipped = status.numberSkipped)
        }
    }
}

private class ImportPasswordsContract(
    private val globalActivityStarter: GlobalActivityStarter,
) : ActivityResultContract<ImportPasswordsRequest, ImportPasswordsResult>() {

    override fun getSynchronousResult(
        context: Context,
        input: ImportPasswordsRequest,
    ): SynchronousResult<ImportPasswordsResult>? {
        val isAvailable = (input as? AvailabilityCheckedPasswordsRequest)?.isAvailable == true
        return when {
            !isAvailable -> SynchronousResult(ImportPasswordsResult.Unavailable)
            buildIntent(context, input) == null -> SynchronousResult(ImportPasswordsResult.Unavailable)
            else -> null
        }
    }

    override fun createIntent(
        context: Context,
        input: ImportPasswordsRequest,
    ): Intent {
        return checkNotNull(buildIntent(context, input)) {
            "getSynchronousResult should return a result when there is no intent"
        }
    }

    override fun parseResult(
        resultCode: Int,
        intent: Intent?,
    ): ImportPasswordsResult {
        val result = intent?.let {
            IntentCompat.getParcelableExtra(it, ImportPasswordsHostResult.RESULT_KEY, ImportPasswordsHostResult::class.java)
        }
        return when (result) {
            ImportPasswordsHostResult.Success -> ImportPasswordsResult.Success
            ImportPasswordsHostResult.UserCancelled, null -> ImportPasswordsResult.UserCancelled
            ImportPasswordsHostResult.TransientError -> ImportPasswordsResult.Error.Transient
            ImportPasswordsHostResult.PermanentError -> ImportPasswordsResult.Error.Permanent
        }
    }

    private fun buildIntent(
        context: Context,
        request: ImportPasswordsRequest,
    ): Intent? {
        return globalActivityStarter.startIntent(context, ImportPasswordsHostScreen(request.params.launchSource))
    }
}

private data class AvailabilityCheckedPasswordsRequest(
    val isAvailable: Boolean,
    override val params: ImportPasswordsParams,
) : ImportPasswordsRequest
