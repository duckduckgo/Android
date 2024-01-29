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

package com.duckduckgo.sync.impl

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.api.SyncCrypto
import com.duckduckgo.sync.crypto.SyncLib
import com.duckduckgo.sync.impl.error.SyncOperationErrorRecorder
import com.duckduckgo.sync.store.SyncStore
import com.duckduckgo.sync.store.model.SyncOperationErrorType
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class RealSyncCrypto @Inject constructor(
    private val nativeLib: SyncLib,
    private val syncStore: SyncStore,
    private val syncOperationErrorRecorder: SyncOperationErrorRecorder,
) : SyncCrypto {
    override fun encrypt(text: String): String {
        val encryptResult = kotlin.runCatching {
            nativeLib.encryptData(text, syncStore.secretKey.orEmpty())
        }.getOrElse {
            syncOperationErrorRecorder.record(SyncOperationErrorType.DATA_ENCRYPT)
            throw it
        }

        return if (encryptResult.result != 0) {
            syncOperationErrorRecorder.record(SyncOperationErrorType.DATA_ENCRYPT)
            throw Exception("Failed to encrypt data")
        } else {
            encryptResult.encryptedData
        }
    }

    override fun decrypt(data: String): String {
        if (data.isEmpty()) return data
        val decryptResult = kotlin.runCatching {
            nativeLib.decryptData(data, syncStore.secretKey.orEmpty())
        }.getOrElse {
            syncOperationErrorRecorder.record(SyncOperationErrorType.DATA_DECRYPT)
            throw it
        }

        return if (decryptResult.result != 0) {
            syncOperationErrorRecorder.record(SyncOperationErrorType.DATA_DECRYPT)
            throw Exception("Failed to decrypt data")
        } else {
            decryptResult.decryptedData
        }
    }
}
