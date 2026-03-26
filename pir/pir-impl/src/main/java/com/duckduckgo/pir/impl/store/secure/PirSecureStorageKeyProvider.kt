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

package com.duckduckgo.pir.impl.store.secure

import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/**
 * This class provides the usable decrypted keys to be used for encryption
 */
interface PirSecureStorageKeyProvider {
    suspend fun canAccessKeyStore(): Boolean

    /**
     * Ready to use key for L1 encryption
     */
    suspend fun getl1Key(): ByteArray
}

@ContributesBinding(AppScope::class)
class RealPirSecureStorageKeyProvider @Inject constructor(
    private val randomBytesGenerator: PirRandomBytesGenerator,
    private val secureStorageKeyRepository: PirSecureStorageKeyRepository,
) : PirSecureStorageKeyProvider {

    override suspend fun canAccessKeyStore(): Boolean =
        secureStorageKeyRepository.canUseEncryption()

    private val l1KeyMutex = Mutex()

    override suspend fun getl1Key(): ByteArray {
        l1KeyMutex.withLock {
            return innerGetL1Key()
        }
    }

    private suspend fun innerGetL1Key(): ByteArray {
        // If no key exists in the keystore, we generate a new one and store it
        return if (secureStorageKeyRepository.getL1Key() == null) {
            randomBytesGenerator.generateBytes(L1_PASSPHRASE_SIZE).also {
                secureStorageKeyRepository.setL1Key(it)
            }
        } else {
            secureStorageKeyRepository.getL1Key()!!
        }
    }

    companion object {
        private const val L1_PASSPHRASE_SIZE = 32
    }
}
