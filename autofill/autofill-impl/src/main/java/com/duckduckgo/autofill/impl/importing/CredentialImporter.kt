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

import android.os.Parcelable
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.importing.CredentialImporter.ImportResult
import com.duckduckgo.autofill.impl.importing.CredentialImporter.ImportResult.Finished
import com.duckduckgo.autofill.impl.importing.CredentialImporter.ImportResult.InProgress
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

interface CredentialImporter {
    suspend fun import(
        importList: List<LoginCredentials>,
        originalImportListSize: Int,
    )

    fun getImportStatus(): Flow<ImportResult>

    sealed interface ImportResult : Parcelable {

        @Parcelize
        data object InProgress : ImportResult

        @Parcelize
        data class Finished(
            val savedCredentials: Int,
            val numberSkipped: Int,
        ) : ImportResult
    }
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class CredentialImporterImpl @Inject constructor(
    private val autofillStore: InternalAutofillStore,
    private val dispatchers: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : CredentialImporter {

    private val _importStatus = MutableSharedFlow<ImportResult>(replay = 1)

    override suspend fun import(
        importList: List<LoginCredentials>,
        originalImportListSize: Int,
    ) {
        appCoroutineScope.launch(dispatchers.io()) {
            doImportCredentials(importList, originalImportListSize)
        }
    }

    private suspend fun doImportCredentials(
        importList: List<LoginCredentials>,
        originalImportListSize: Int,
    ) {
        var skippedCredentials = originalImportListSize - importList.size

        _importStatus.emit(InProgress)

        val insertedIds = autofillStore.bulkInsert(importList)

        skippedCredentials += (importList.size - insertedIds.size)

        // Set the flag when at least one credential was successfully imported
        if (insertedIds.isNotEmpty()) {
            autofillStore.hasEverImportedPasswords = true
        }

        _importStatus.emit(Finished(savedCredentials = insertedIds.size, numberSkipped = skippedCredentials))
    }

    override fun getImportStatus(): Flow<ImportResult> = _importStatus
}
