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

package com.duckduckgo.persistentstorage.dummy

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.persistentstorage.api.PersistentStorage
import com.duckduckgo.persistentstorage.api.PersistentStorageAvailability
import com.duckduckgo.persistentstorage.api.PersistentStorageKey
import com.duckduckgo.persistentstorage.api.PersistentStorageUnavailableException
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import logcat.logcat
import javax.inject.Inject

/**
 * Dummy implementation of [PersistentStorage] for build types without Google Play Services (e.g., F-Droid).
 */
@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class DummyPersistentStorage @Inject constructor() : PersistentStorage {

    override suspend fun checkAvailability(): PersistentStorageAvailability {
        logcat { "Block Store: dummy impl - returning Unavailable" }
        return PersistentStorageAvailability.Unavailable
    }

    override suspend fun store(key: PersistentStorageKey, value: ByteArray): Result<Unit> {
        logcat { "Block Store: dummy impl - store called for ${key.key}, returning failure" }
        return Result.failure(PersistentStorageUnavailableException())
    }

    override suspend fun clear(key: PersistentStorageKey): Result<Unit> {
        logcat { "Block Store: dummy impl - clear called for ${key.key}, returning failure" }
        return Result.failure(PersistentStorageUnavailableException())
    }

    override suspend fun retrieve(key: PersistentStorageKey): Result<ByteArray?> {
        logcat { "Block Store: dummy impl - retrieve called for ${key.key}, returning failure" }
        return Result.failure(PersistentStorageUnavailableException())
    }
}
