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
import com.duckduckgo.securestorage.impl.encryption.EncryptionHelper
import com.duckduckgo.securestorage.impl.encryption.EncryptionHelper.EncryptedString
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

interface L2DataTransformer {
    fun canProcessData(): Boolean

    fun encrypt(data: String): EncryptedString

    fun decrypt(
        data: String,
        iv: String
    ): String
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealL2DataTransformer @Inject constructor(
    private val encryptionHelper: EncryptionHelper,
    private val secureStorageKeyProvider: SecureStorageKeyProvider
) : L2DataTransformer {
    private val l2Key by lazy {
        secureStorageKeyProvider.getl2Key()
    }

    override fun canProcessData(): Boolean = secureStorageKeyProvider.canAccessKeyStore()

    override fun encrypt(data: String): EncryptedString = encryptionHelper.encrypt(data, l2Key)

    override fun decrypt(
        data: String,
        iv: String
    ): String = encryptionHelper.decrypt(
        EncryptedString(
            data = data,
            iv = iv
        ),
        l2Key
    )
}
