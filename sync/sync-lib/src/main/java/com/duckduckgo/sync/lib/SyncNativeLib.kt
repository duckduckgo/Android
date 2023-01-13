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

    fun generateAccountKeys(
        userId: String,
        password: String
    ): Account {
        val primaryKey = ByteArray(getPrimaryKeySize())
        val secretKey = ByteArray(getSecretKeySize())
        val protectedSecretKey = ByteArray(getProtectedSecretKeySize())
        val passwordHash = ByteArray(getPasswordHashSize())

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

    fun prepareForLogin(
        primaryKey: String
    ): Login {
        val primarKeyByteArray = primaryKey.decode()
        val passwordHash = ByteArray(getPasswordHashSize())
        val stretchedPrimaryKey = ByteArray(getStretchedPrimaryKeySize())

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
        val decryptedData = ByteArray(encryptedDataByteArray.size-getEncryptedExtraBytes())

        val result: Long = decrypt(decryptedData, encryptedDataByteArray, secretKeyByteArray)

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
        secretKey: ByteArray,
    ): Long

    private external fun getPrimaryKeySize(): Int
    private external fun getSecretKeySize(): Int
    private external fun getProtectedSecretKeySize(): Int
    private external fun getPasswordHashSize(): Int
    private external fun getStretchedPrimaryKeySize(): Int
    private external fun getEncryptedExtraBytes(): Int
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
