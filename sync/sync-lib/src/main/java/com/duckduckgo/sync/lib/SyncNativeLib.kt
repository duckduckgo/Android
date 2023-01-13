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

package com.duckduckgo.sync.lib

import android.content.Context
import android.util.Base64
import com.duckduckgo.library.loader.LibraryLoader
import kotlin.system.exitProcess
import timber.log.Timber

class SyncNativeLib constructor(context: Context) {

    init {
        try {
            Timber.v("Loading native SYNC library")
            LibraryLoader.loadLibrary(context, "ddgcrypto")
        } catch (ignored: Throwable) {
            Timber.e(ignored, "Error loading sync library")
            exitProcess(1)
        }
    }

    /**
     * Used to create data needed to create an account.  Once the server returns a JWT, store the primary and secret key.
     *
     * @param primaryKey OUT - store this.  In combination with user id, this is the recovery key.
     * @param secretKey OUT - store this. This is used to encrypt an decrypt e2e data.
     * @param protectedSecretKey OUT - do not store this.  Send to /sign up endpoint.
     * @param passwordHash OUT - do not store this.  Send to /signup endpoint.
     * @param userId IN
     * @param password IN
     */
    fun generateAccountKeys(
        userId: String,
        password: String
    ): Account {
        val primaryKey = ByteArray(32)
        val secretKey = ByteArray(32)
        val protectedSecretKey = ByteArray(72) // 32+16(mac)+24
        val passwordHash = ByteArray(32)

        Timber.v("SYNC PRE PK: ${primaryKey.encode()}")
        Timber.v("SYNC PRE SK: ${secretKey.encode()}")
        Timber.v("SYNC PRE PSK: ${protectedSecretKey.encode()}")
        Timber.v("SYNC PRE PH: ${passwordHash.encode()}")

        val result: Long = generateAccountKeys(primaryKey, secretKey, protectedSecretKey, passwordHash, userId, password)

        Timber.v("SYNC PK: ${primaryKey.encode()}")
        Timber.v("SYNC SK: ${secretKey.encode()}")
        Timber.v("SYNC PSK: ${protectedSecretKey.encode()}")
        Timber.v("SYNC PH: ${passwordHash.encode()}")

        return Account(
            result = result,
            primaryKey = primaryKey.encode(),
            secretKey = secretKey.encode(),
            protectedSecretKey = protectedSecretKey.encode(),
            passwordHash = passwordHash.encode(),
            userId = userId,
            password = password
        )
    }

    /**
     * Prepare keys for calling /login when using a recovery code.  Once the protected secret key has been retrieved, use `ddgSyncDecrypt` to extract the secret key, using the stretched primary key as the secret key for the decryption.
     *
     * @param passwordHash OUT
     * @param stretchedPrimaryKey OUT
     * @param primaryKey IN
     */
    fun prepareForLogin(
        primaryKey: String
    ): Login {
        val primarKeyByteArray = primaryKey.decode()
        val passwordHash = ByteArray(primarKeyByteArray.size)
        val stretchedPrimaryKey = ByteArray(primarKeyByteArray.size)

        val result: Long = prepareForLogin(passwordHash, stretchedPrimaryKey, primarKeyByteArray)

        Timber.v("SYNC PK: ${primaryKey}")
        Timber.v("SYNC PH: ${passwordHash.encode()}")
        Timber.v("SYNC SPK: ${stretchedPrimaryKey.encode()}")

        return Login(
            result = result,
            passwordHash = passwordHash.encode(),
            stretchedPrimaryKey = stretchedPrimaryKey.encode(),
            primaryKey = primaryKey,
        )
    }

    fun decrypt(
        encryptedData: String,
        secretKey: String,
    ): String {
        val encryptedDataByteArray = encryptedData.decode()
        val secretKeyByteArray = secretKey.decode()
        val decryptedData = ByteArray(encryptedDataByteArray.size-16-24) //TODO: validate if size is correct

        val result: Long = decrypt(decryptedData, encryptedDataByteArray, encryptedDataByteArray.size.toLong(), secretKeyByteArray)

        Timber.v("SYNC result: $result")
        Timber.v("SYNC decryptedData: ${decryptedData}")

        return decryptedData.encode()
    }

    fun ByteArray.encode(): String {
        return Base64.encodeToString(this, Base64.NO_WRAP)
    }

    fun String.decode(): ByteArray {
        return Base64.decode(this, Base64.NO_WRAP)
    }

    private external fun generateAccountKeys(
        primaryKey: ByteArray,
        secretKey: ByteArray,
        protectedSecretKey: ByteArray,
        passwordHash: ByteArray,
        userId: String,
        password: String,
    ): Long

    private external fun prepareForLogin(
        passwordHash: ByteArray,
        stretchedPrimaryKey: ByteArray,
        primaryKey: ByteArray,
    ): Long

    private external fun decrypt(
        rawBytes: ByteArray,
        encryptedBytes: ByteArray,
        encryptedBytesLength: Long, //TODO: test if necessary
        secretKey: ByteArray,
    ): Long
}

class Account(
    val result: Long,
    val primaryKey: String,
    val secretKey: String,
    val protectedSecretKey: String,
    val passwordHash: String,
    val userId: String,
    val password: String,
)

class Login(
    val result: Long,
    val passwordHash: String,
    val stretchedPrimaryKey: String,
    val primaryKey: String
)
