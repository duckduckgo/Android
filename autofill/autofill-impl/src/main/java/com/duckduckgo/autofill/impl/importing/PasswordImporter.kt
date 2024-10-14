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

package com.duckduckgo.autofill.impl.importing

import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.importing.PasswordImporter.ImportResult
import com.duckduckgo.autofill.impl.importing.PasswordImporter.ImportResult.Finished
import com.duckduckgo.autofill.impl.importing.PasswordImporter.ImportResult.InProgress
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext

interface PasswordImporter {
    suspend fun importPasswords(importList: List<LoginCredentials>)
    fun getImportStatus(): Flow<ImportResult>

    sealed interface ImportResult {
        data class InProgress(
            val savedCredentialIds: List<Long>,
            val duplicatedPasswords: List<LoginCredentials>,
            val importListSize: Int,
        ) : ImportResult

        data class Finished(
            val savedCredentialIds: List<Long>,
            val duplicatedPasswords: List<LoginCredentials>,
            val importListSize: Int,
        ) : ImportResult
    }
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class PasswordImporterImpl @Inject constructor(
    private val existingPasswordMatchDetector: ExistingPasswordMatchDetector,
    private val autofillStore: InternalAutofillStore,
    private val dispatchers: DispatcherProvider,
) : PasswordImporter {

    private val _importStatus = MutableSharedFlow<ImportResult>(replay = 1)

    override suspend fun importPasswords(importList: List<LoginCredentials>) {
        return withContext(dispatchers.io()) {
            val savedCredentialIds = mutableListOf<Long>()
            val duplicatedPasswords = mutableListOf<LoginCredentials>()

            importList.forEach {
                if (!existingPasswordMatchDetector.alreadyExists(it)) {
                    val insertedId = autofillStore.saveCredentials(it.domain!!, it)?.id

                    if (insertedId != null) {
                        savedCredentialIds.add(insertedId)
                    }
                } else {
                    duplicatedPasswords.add(it)
                }

                _importStatus.emit(InProgress(savedCredentialIds, duplicatedPasswords, importList.size))
            }

            _importStatus.emit(Finished(savedCredentialIds, duplicatedPasswords, importList.size))
        }
    }

    override fun getImportStatus(): Flow<ImportResult> {
        return _importStatus
    }
}
