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

package com.duckduckgo.sync.impl.pixels

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.sync.impl.SyncAccountRepository
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SwitchedAccountDetector @Inject constructor(
    private val accountRepository: SyncAccountRepository,
    private val syncPixels: SyncPixels,
    private val dispatchers: DispatcherProvider,
) {
    @Volatile
    private var primaryKeyBeforePairing = ""

    suspend fun onPairingStarted() {
        primaryKeyBeforePairing = currentPrimaryKey()
    }

    suspend fun onPaired() {
        if (primaryKeyBeforePairing.isNotBlank() && primaryKeyBeforePairing != currentPrimaryKey()) {
            syncPixels.fireUserSwitchedAccount()
        }
    }

    private suspend fun currentPrimaryKey(): String = withContext(dispatchers.io()) {
        accountRepository.getAccountInfo().primaryKey
    }
}
