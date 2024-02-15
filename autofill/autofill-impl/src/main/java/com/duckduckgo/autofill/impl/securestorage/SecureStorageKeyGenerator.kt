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

import android.security.keystore.KeyProperties
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import java.security.Key
import javax.crypto.KeyGenerator
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Provider

interface SecureStorageKeyGenerator {
    fun generateKey(): Key
    fun generateKeyFromKeyMaterial(keyMaterial: ByteArray): Key
    fun generateKeyFromPassword(
        password: String,
        salt: ByteArray,
    ): Key
}

@ContributesBinding(AppScope::class)
class RealSecureStorageKeyGenerator @Inject constructor(
    @Named("DerivedKeySecretFactoryFor26Up") private val derivedKeySecretFactory: Provider<DerivedKeySecretFactory>,
) : SecureStorageKeyGenerator {
    private val keyGenerator: KeyGenerator by lazy {
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES).also {
            it.init(SIZE)
        }
    }

    override fun generateKey(): Key = keyGenerator.generateKey()

    override fun generateKeyFromKeyMaterial(keyMaterial: ByteArray): Key = SecretKeySpec(
        keyMaterial,
        KeyProperties.KEY_ALGORITHM_AES,
    )

    override fun generateKeyFromPassword(
        password: String,
        salt: ByteArray,
    ): Key =
        derivedKeySecretFactory.get().getKey(
            PBEKeySpec(
                password.toCharArray(),
                salt,
                ITERATIONS_26_UP,
                SIZE,
            ),
        ).run {
            SecretKeySpec(this.encoded, KeyProperties.KEY_ALGORITHM_AES)
        }

    companion object {
        private const val ITERATIONS_26_UP = 100_000
        private const val SIZE = 256
    }
}
