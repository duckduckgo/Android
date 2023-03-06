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

    fun seal(message: String, publicKey: String): String

    fun sealOpen(
        cypherTextBytes: String,
        primaryKey: String,
        secretKey: String,
    ): String
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
            primaryKey = primaryKey.encodeKey(),
            secretKey = secretKey.encodeKey(),
            protectedSecretKey = protectedSecretKey.encodeKey(),
            passwordHash = passwordHash.encodeKey(),
            userId = userId,
            password = password,
        )
    }

    override fun prepareForLogin(primaryKey: String): LoginKeys {
        val primarKeyByteArray = primaryKey.decodeKey()
        val passwordHash = ByteArray(getPasswordHashSize())
        val stretchedPrimaryKey = ByteArray(getStretchedPrimaryKeySize())

        val result: Long = prepareForLogin(passwordHash, stretchedPrimaryKey, primarKeyByteArray)

        return LoginKeys(
            result = result,
            passwordHash = passwordHash.encodeKey(),
            stretchedPrimaryKey = stretchedPrimaryKey.encodeKey(),
            primaryKey = primaryKey,
        )
    }

    override fun decrypt(
        encryptedData: String,
        secretKey: String,
    ): DecryptResult {
        Timber.d("SYNC decrypt: $encryptedData with $secretKey")
        val encryptedDataByteArray = encryptedData.decodeKey()
        val secretKeyByteArray = secretKey.decodeKey()
        val decryptedData = ByteArray(encryptedDataByteArray.size - getEncryptedExtraBytes())

        val result: Long = decrypt(decryptedData, encryptedDataByteArray, secretKeyByteArray)

        return DecryptResult(
            result = result,
            decryptedData = decryptedData.encodeKey(),
        )
    }

    override fun encrypt(
        rawData: String,
        secretKey: String,
    ): EncryptResult {
        Timber.d("SYNC encrypt: $rawData with $secretKey")
        val rawDataByteArray = rawData.decodeKey()
        val secretKeyByteArray = secretKey.decodeKey()
        val encryptedDataByteArray = ByteArray(rawDataByteArray.size + getEncryptedExtraBytes())

        val result: Long = encrypt(encryptedDataByteArray, rawDataByteArray, secretKeyByteArray)

        return EncryptResult(
            result = result,
            encryptedData = encryptedDataByteArray.encodeKey(),
        )
    }

    override fun seal(
        message: String,
        publicKey: String,
    ): String {
        val messageBytes = message.decodeText()
        val publicKeyBytes = publicKey.decodeKey()
        val sealedData = ByteArray(messageBytes.size + getSealBytes())

        val result: Int = seal(sealedData, publicKeyBytes, messageBytes)

        return sealedData.encodeKey()
    }

    override fun sealOpen(
        cypherText: String,
        primaryKey: String,
        secretKey: String,
    ): String {
        val primaryKeyBytes = primaryKey.decodeKey()
        val secretKeyBytes = secretKey.decodeKey()
        val cypherTextBytes = cypherText.decodeKey()
        val rawBytes = ByteArray(cypherTextBytes.size - getSealBytes())

        val result: Int = sealOpen(cypherTextBytes, primaryKeyBytes, secretKeyBytes, rawBytes)

        return rawBytes.encodeText()
    }

    private fun ByteArray.encodeKey(): String {
        return Base64.encodeToString(this, Base64.NO_WRAP)
    }

    private fun String.decodeKey(): ByteArray {
        return Base64.decode(this, Base64.NO_WRAP)
    }

    private fun ByteArray.encodeText(): String {
        return String(this)
    }

    private fun String.decodeText(): ByteArray {
        return this.toByteArray()
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

    private external fun seal(
        sealedBytes: ByteArray,
        primaryKey: ByteArray,
        messageBytes: ByteArray,
    ): Int

    private external fun sealOpen(
        cyphertext: ByteArray,
        primaryKey: ByteArray,
        secretKey: ByteArray,
        rawBytes: ByteArray,
    ): Int

    private external fun getPrimaryKeySize(): Int
    private external fun getSecretKeySize(): Int
    private external fun getProtectedSecretKeySize(): Int
    private external fun getPasswordHashSize(): Int
    private external fun getStretchedPrimaryKeySize(): Int
    private external fun getEncryptedExtraBytes(): Int
    private external fun getSealBytes(): Int
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
