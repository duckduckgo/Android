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
import com.duckduckgo.autofill.impl.importing.PasswordImporter.ImportResult
import com.duckduckgo.autofill.impl.importing.PasswordImporter.ImportResult.Finished
import com.duckduckgo.autofill.impl.importing.PasswordImporter.ImportResult.InProgress
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.parcelize.Parcelize

interface PasswordImporter {
    suspend fun importPasswords(importList: List<LoginCredentials>): String
    fun getImportStatus(jobId: String): Flow<ImportResult>

    sealed interface ImportResult : Parcelable {

        @Parcelize
        data class InProgress(
            val savedCredentialIds: List<Long>,
            val duplicatedPasswords: List<LoginCredentials>,
            val importListSize: Int,
            val jobId: String,
        ) : ImportResult

        @Parcelize
        data class Finished(
            val savedCredentialIds: List<Long>,
            val duplicatedPasswords: List<LoginCredentials>,
            val importListSize: Int,
            val jobId: String,
        ) : ImportResult
    }
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class PasswordImporterImpl @Inject constructor(
    private val existingPasswordMatchDetector: ExistingPasswordMatchDetector,
    private val autofillStore: InternalAutofillStore,
    private val dispatchers: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : PasswordImporter {

    private val _importStatus = MutableSharedFlow<ImportResult>(replay = 1)
    private val mutex = Mutex()

    override suspend fun importPasswords(importList: List<LoginCredentials>): String {
        val jobId = UUID.randomUUID().toString()

        mutex.withLock {
            appCoroutineScope.launch(dispatchers.io()) {
                doImportPasswords(importList, jobId)
            }
        }

        return jobId
    }

    private suspend fun doImportPasswords(
        importList: List<LoginCredentials>,
        jobId: String,
    ) {
        val savedCredentialIds = mutableListOf<Long>()
        val duplicatedPasswords = mutableListOf<LoginCredentials>()

        _importStatus.emit(InProgress(savedCredentialIds, duplicatedPasswords, importList.size, jobId))

        importList.forEach {
            if (!existingPasswordMatchDetector.alreadyExists(it)) {
                val insertedId = autofillStore.saveCredentials(it.domain!!, it)?.id

                if (insertedId != null) {
                    savedCredentialIds.add(insertedId)
                }
            } else {
                duplicatedPasswords.add(it)
            }

            _importStatus.emit(InProgress(savedCredentialIds, duplicatedPasswords, importList.size, jobId))
        }

        _importStatus.emit(Finished(savedCredentialIds, duplicatedPasswords, importList.size, jobId))
    }

    override fun getImportStatus(jobId: String): Flow<ImportResult> {
        return _importStatus.filter { result ->
            when (result) {
                is InProgress -> result.jobId == jobId
                is Finished -> result.jobId == jobId
            }
        }
    }
}
