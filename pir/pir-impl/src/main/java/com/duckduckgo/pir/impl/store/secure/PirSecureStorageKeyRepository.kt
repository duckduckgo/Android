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
import dagger.SingleInstanceIn
import javax.inject.Inject

interface PirSecureStorageKeyRepository {
    /**
     * Key used for L1 encryption
     */
    suspend fun getL1Key(): ByteArray?
    suspend fun setL1Key(value: ByteArray?)

    /**
     * This method can be checked if the keystore has support for encryption
     *
     * @return `true` if keystore encryption is supported and `false` otherwise
     */
    suspend fun canUseEncryption(): Boolean
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(
    scope = AppScope::class,
    boundType = PirSecureStorageKeyRepository::class,
)
class RealPirSecureStorageKeyRepository @Inject constructor(
    private val keyStore: PirSecureStorageKeyStore,
) : PirSecureStorageKeyRepository {
    override suspend fun getL1Key(): ByteArray? = keyStore.getKey(KEY_L1KEY)
    override suspend fun setL1Key(value: ByteArray?) {
        keyStore.updateKey(KEY_L1KEY, value)
    }

    override suspend fun canUseEncryption(): Boolean = keyStore.canUseEncryption()

    companion object {
        private const val KEY_L1KEY = "KEY_L1KEY"
    }
}
