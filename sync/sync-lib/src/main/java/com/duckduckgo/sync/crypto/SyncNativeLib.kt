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

package com.duckduckgo.sync.crypto

import android.content.Context
import android.util.Base64
import com.duckduckgo.library.loader.LibraryLoader
import java.util.*
import kotlin.system.exitProcess
import okio.ByteString.Companion.encode
import timber.log.Timber

interface SyncLib {
    fun generateAccountKeys(
        userId: String,
        password: String = UUID.randomUUID().toString(),
    ): AccountKeys

    fun prepareForLogin(primaryKey: String): LoginKeys
    fun decrypt(
        encryptedData: String,
        secretKey: String,
    ): DecryptResult

    fun encrypt(
        rawData: String,
        secretKey: String,
    ): EncryptResult
}

class SyncNativeLib constructor(context: Context) : SyncLib {

    init {
        try {
            Timber.v("Loading native SYNC library")
            LibraryLoader.loadLibrary(context, "ddgcrypto")
        } catch (ignored: Throwable) {
            Timber.e(ignored, "Error loading sync library")
            exitProcess(1)
        }
    }

    override fun generateAccountKeys(
        userId: String,
        password: String,
    ): AccountKeys {
        val primaryKey = ByteArray(getPrimaryKeySize())
        val secretKey = ByteArray(getSecretKeySize())
        val protectedSecretKey = ByteArray(getProtectedSecretKeySize())
        val passwordHash = ByteArray(getPasswordHashSize())

        val result: Long =
            generateAccountKeys(
                primaryKey,
                secretKey,
                protectedSecretKey,
                passwordHash,
                userId,
                password,
            )

        return AccountKeys(
            result = result,
            primaryKey = primaryKey.encode(),
            secretKey = secretKey.encode(),
            protectedSecretKey = protectedSecretKey.encode(),
            passwordHash = passwordHash.encode(),
            userId = userId,
            password = password,
        )
    }

    override fun prepareForLogin(primaryKey: String): LoginKeys {
        val primarKeyByteArray = primaryKey.decode()
        val passwordHash = ByteArray(getPasswordHashSize())
        val stretchedPrimaryKey = ByteArray(getStretchedPrimaryKeySize())

        val result: Long = prepareForLogin(passwordHash, stretchedPrimaryKey, primarKeyByteArray)

        return LoginKeys(
            result = result,
            passwordHash = passwordHash.encode(),
            stretchedPrimaryKey = stretchedPrimaryKey.encode(),
            primaryKey = primaryKey,
        )
    }

    override fun decrypt(
        encryptedData: String,
        secretKey: String,
    ): DecryptResult {
        val encryptedDataByteArray = encryptedData.decode()
        val secretKeyByteArray = secretKey.decode()
        val decryptedData = ByteArray(encryptedDataByteArray.size - getEncryptedExtraBytes())

        val result: Long = decrypt(decryptedData, encryptedDataByteArray, secretKeyByteArray)

        return DecryptResult(
            result = result,
            decryptedData = decryptedData.encode(),
        )
    }

    override fun encrypt(
        rawData: String,
        secretKey: String,
    ): EncryptResult {
        val rawDataByteArray = rawData.decode()
        val secretKeyByteArray = secretKey.decode()
        val encryptedDataByteArray = ByteArray(rawDataByteArray.size + getEncryptedExtraBytes())

        val result: Long = encrypt(encryptedDataByteArray, rawDataByteArray, secretKeyByteArray)

        return EncryptResult(
            result = result,
            encryptedData = encryptedDataByteArray.encode(),
        )
    }

    private fun ByteArray.encode(): String {
        return Base64.encodeToString(this, Base64.NO_WRAP)
    }

    private fun String.decode(): ByteArray {
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

    private external fun encrypt(
        encryptedBytes: ByteArray,
        rawBytes: ByteArray,
        secretKey: ByteArray,
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

class AccountKeys(
    val result: Long,
    val primaryKey: String,
    val secretKey: String,
    val protectedSecretKey: String,
    val passwordHash: String,
    val userId: String,
    val password: String,
)

class LoginKeys(
    val result: Long,
    val passwordHash: String,
    val stretchedPrimaryKey: String,
    val primaryKey: String,
)

class DecryptResult(
    val result: Long,
    val decryptedData: String,
)

class EncryptResult(
    val result: Long,
    val encryptedData: String,
)
