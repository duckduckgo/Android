/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.securestorage.impl

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.securestorage.impl.encryption.PasswordGenerator
import com.duckduckgo.securestorage.store.SecureStorageKeyStore
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

/**
 * This class provides the usable decrypted keys to be used in various levels on encryption
 */
interface SecureStorageKeyManager {
    /**
     * Ready to use key for L1 encryption
     */
    val l1Key: ByteArray

    /**
     * Ready to use key for L2 encryption
     */
    val l2Key: ByteArray
}

@ContributesBinding(AppScope::class)
class RealSecureStorageKeyManager @Inject constructor(
    private val passwordGenerator: PasswordGenerator,
    private val secureStorageKeyStore: SecureStorageKeyStore
) : SecureStorageKeyManager {

    private val _l1Key: ByteArray by lazy {
        // If no key exists in the keystore, we generate a new one and store it
        if (secureStorageKeyStore.l1Key == null) {
            passwordGenerator.generatePassword().also {
                secureStorageKeyStore.l1Key = it
            }
        } else {
            secureStorageKeyStore.l1Key!!
        }
    }

    override val l1Key: ByteArray
        get() = _l1Key
    override val l2Key: ByteArray
        get() = TODO("Not yet implemented")
}
