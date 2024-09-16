/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.autofill.impl.securestorage

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.securestorage.store.RealSecureStorageRepository
import com.duckduckgo.securestorage.store.SecureStorageRepository
import com.duckduckgo.securestorage.store.db.SecureStorageDatabase
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.flow.firstOrNull
import timber.log.Timber

@ContributesBinding(AppScope::class)
class RealSecureStorageRepositoryFactory @Inject constructor(
    private val secureStorageDatabaseFactory: SecureStorageDatabaseFactory,
) : SecureStorageRepository.Factory {
    override suspend fun get(): SecureStorageRepository? {
        val db = secureStorageDatabaseFactory.getDatabase()
        return if (db != null && db.contentsAreReadable()) {
            RealSecureStorageRepository(db.websiteLoginCredentialsDao(), db.neverSavedSitesDao())
        } else {
            null
        }
    }

    private suspend fun SecureStorageDatabase.contentsAreReadable(): Boolean {
        return kotlin.runCatching {
            websiteLoginCredentialsDao().websiteLoginCredentials().firstOrNull()
            true
        }.getOrElse {
            Timber.e("Secure storage database exists but is not readable")
            false
        }
    }
}
