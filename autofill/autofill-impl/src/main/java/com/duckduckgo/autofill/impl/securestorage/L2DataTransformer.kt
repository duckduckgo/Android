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

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.autofill.impl.securestorage.SecureStorageException.InternalSecureStorageException
import com.duckduckgo.autofill.impl.securestorage.encryption.EncryptionHelper
import com.duckduckgo.autofill.impl.securestorage.encryption.EncryptionHelper.EncryptedBytes
import com.duckduckgo.autofill.impl.securestorage.encryption.EncryptionHelper.EncryptedString
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import java.security.Key
import javax.inject.Inject

interface L2DataTransformer {
    suspend fun canProcessData(): Boolean

    @Throws(SecureStorageException::class)
    suspend fun encrypt(data: String): EncryptedString

    @Throws(SecureStorageException::class)
    suspend fun decrypt(
        data: String,
        iv: String,
    ): String
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealL2DataTransformer @Inject constructor(
    private val encryptionHelper: EncryptionHelper,
    private val secureStorageKeyProvider: SecureStorageKeyProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : L2DataTransformer {
    private val l2KeyDeferred: Deferred<Key> by lazy {
        appCoroutineScope.async(dispatcherProvider.io()) {
            secureStorageKeyProvider.getl2Key()
        }
    }

    suspend fun getL2Key(): Key = l2KeyDeferred.await()

    override suspend fun canProcessData(): Boolean = secureStorageKeyProvider.canAccessKeyStore()

    // get ByteArray -> encrypt -> encode to String
    override suspend fun encrypt(data: String): EncryptedString = encryptionHelper.encrypt(data.toByteArray(), getL2Key()).run {
        EncryptedString(
            this.data.transformToString(),
            this.iv.transformToString(),
        )
    }

    // decode to ByteArray -> decrypt -> get String
    override suspend fun decrypt(
        data: String,
        iv: String,
    ): String = encryptionHelper.decrypt(
        EncryptedBytes(
            data = data.transformToByteArray(),
            iv = iv.transformToByteArray(),
        ),
        getL2Key(),
    ).run { String(this) }

    private fun ByteArray.transformToString(): String = this.toByteString().base64()

    private fun String.transformToByteArray(): ByteArray =
        this.decodeBase64()?.toByteArray() ?: throw InternalSecureStorageException("Error while decoding string data to Base64")
}
