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
import logcat.LogPriority.ERROR
import logcat.LogPriority.VERBOSE
import logcat.asLog
import logcat.logcat
import java.util.*
import kotlin.system.exitProcess

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

    fun prepareForConnect(): ConnectKeys

    fun seal(message: String, publicKey: String): String

    fun sealOpen(
        cypherTextBytes: String,
        primaryKey: String,
        secretKey: String,
    ): String

    fun decryptData(
        rawData: String,
        primaryKey: String,
    ): DecryptResult

    fun encryptData(
        rawData: String,
        primaryKey: String,
    ): EncryptResult

    companion object {
        fun create(context: Context): SyncLib = SyncNativeLibImpl { SyncNativeLib(context) }
    }
}

// this wrapper class ensures the SyncNativeLib() is created only when any of the SyncLib methods is called
// This is to avoid loading "ddgcrypto" library at process creation (instance creation) when in reality is not needed
internal class SyncNativeLibImpl constructor(syncNativeLibProvider: () -> SyncLib) : SyncLib {
    private val synLib: SyncLib by lazy { syncNativeLibProvider.invoke() }

    override fun generateAccountKeys(
        userId: String,
        password: String,
    ): AccountKeys = synLib.generateAccountKeys(userId, password)

    override fun prepareForLogin(primaryKey: String): LoginKeys = synLib.prepareForLogin(primaryKey)

    override fun decrypt(
        encryptedData: String,
        secretKey: String,
    ): DecryptResult = synLib.decrypt(encryptedData, secretKey)

    override fun prepareForConnect(): ConnectKeys = synLib.prepareForConnect()

    override fun seal(
        message: String,
        publicKey: String,
    ): String = synLib.seal(message, publicKey)

    override fun sealOpen(
        cypherTextBytes: String,
        primaryKey: String,
        secretKey: String,
    ): String = synLib.sealOpen(cypherTextBytes, primaryKey, secretKey)

    override fun decryptData(
        rawData: String,
        primaryKey: String,
    ): DecryptResult = synLib.decryptData(rawData, primaryKey)

    override fun encryptData(
        rawData: String,
        primaryKey: String,
    ): EncryptResult = synLib.encryptData(rawData, primaryKey)
}

internal class SyncNativeLib constructor(context: Context) : SyncLib {

    init {
        try {
            logcat(VERBOSE) { "Loading native SYNC library" }
            LibraryLoader.loadLibrary(context, "ddgcrypto")
        } catch (ignored: Throwable) {
            logcat(ERROR) { "Error loading sync library: ${ignored.asLog()}" }
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

        val result: Int =
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

        val result: Int = prepareForLogin(passwordHash, stretchedPrimaryKey, primarKeyByteArray)

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
        val encryptedDataByteArray = encryptedData.decodeKey()
        val secretKeyByteArray = secretKey.decodeKey()
        val decryptedData = ByteArray(encryptedDataByteArray.size - getEncryptedExtraBytes())

        val result: Int = decrypt(decryptedData, encryptedDataByteArray, secretKeyByteArray)

        return DecryptResult(
            result = result,
            decryptedData = decryptedData.encodeKey(),
        )
    }

    override fun prepareForConnect(): ConnectKeys {
        val publicKey = ByteArray(getPublicKeyBytes())
        val privateKey = ByteArray(getPrivateKeyBytes())

        val result: Int =
            prepareForConnect(
                publicKey,
                privateKey,
            )

        return ConnectKeys(
            result = result,
            publicKey = publicKey.encodeKey(),
            secretKey = privateKey.encodeKey(),
        )
    }

    override fun encryptData(
        rawData: String,
        primaryKey: String,
    ): EncryptResult {
        val rawDataByteArray = rawData.decodeText()
        val secretKeyByteArray = primaryKey.decodeKey()
        val encryptedDataByteArray = ByteArray(rawDataByteArray.size + getEncryptedExtraBytes())

        val result: Int = encrypt(encryptedDataByteArray, rawDataByteArray, secretKeyByteArray)

        return EncryptResult(
            result = result,
            encryptedData = encryptedDataByteArray.encodeKey(),
        )
    }

    override fun decryptData(
        encryptedData: String,
        primaryKey: String,
    ): DecryptResult {
        val encryptedDataByteArray = encryptedData.decodeKey()
        val secretKeyByteArray = primaryKey.decodeKey()
        val decryptedData = ByteArray(encryptedDataByteArray.size - getEncryptedExtraBytes())

        val result: Int = decrypt(decryptedData, encryptedDataByteArray, secretKeyByteArray)

        return DecryptResult(
            result = result,
            decryptedData = decryptedData.encodeText(),
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
    ): Int

    private external fun prepareForConnect(
        publicKey: ByteArray,
        secretKey: ByteArray,
    ): Int

    private external fun prepareForLogin(
        passwordHash: ByteArray,
        stretchedPrimaryKey: ByteArray,
        primaryKey: ByteArray,
    ): Int

    private external fun encrypt(
        encryptedBytes: ByteArray,
        rawBytes: ByteArray,
        secretKey: ByteArray,
    ): Int

    private external fun decrypt(
        rawBytes: ByteArray,
        encryptedBytes: ByteArray,
        secretKey: ByteArray,
    ): Int

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
    private external fun getPublicKeyBytes(): Int
    private external fun getPrivateKeyBytes(): Int
    private external fun getSealBytes(): Int
}

interface SyncCryptoResult {
    val result: Int
}

class AccountKeys(
    override val result: Int,
    val primaryKey: String,
    val secretKey: String,
    val protectedSecretKey: String,
    val passwordHash: String,
    val userId: String,
    val password: String,
) : SyncCryptoResult

class ConnectKeys(
    override val result: Int,
    val publicKey: String,
    val secretKey: String,
) : SyncCryptoResult

class LoginKeys(
    override val result: Int,
    val passwordHash: String,
    val stretchedPrimaryKey: String,
    val primaryKey: String,
) : SyncCryptoResult

class DecryptResult(
    override val result: Int,
    val decryptedData: String,
) : SyncCryptoResult

class EncryptResult(
    override val result: Int,
    val encryptedData: String,
) : SyncCryptoResult
