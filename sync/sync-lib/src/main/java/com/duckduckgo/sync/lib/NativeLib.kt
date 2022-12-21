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
import okio.ByteString.Companion.decodeBase64
import kotlin.system.exitProcess
import timber.log.Timber

class NativeLib constructor(
    private val context: Context,
) {

    /**
     * A native method that is implemented by the 'lib' native library, which is packaged with this
     * application.
     */
    external fun stringFromJNI(): String

    init {
        try {
            Timber.v("Loading native SYNC library")
            System.loadLibrary("ddgcrypto")
        } catch (ignored: Throwable) {
            Timber.e(ignored, "Error loading netguard library")
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
    fun generateAccountKeys(): Account {
        val primaryKey = ByteArray(32)
        val secretKey = ByteArray(32)
        val protectedSecretKey = ByteArray(64)
        val passwordHash = ByteArray(32)

        Timber.v("SYNC PRE PK: ${primaryKey.encode()}")
        Timber.v("SYNC PRE SK: ${secretKey.encode()}")
        Timber.v("SYNC PRE PSK: ${protectedSecretKey.encode()}")
        Timber.v("SYNC PRE PH: ${passwordHash.encode()}")

        val result: Long = generateAccountKeys(primaryKey, secretKey, protectedSecretKey, passwordHash, "test", "password")

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
            userId = "test1234",
            password = "password"
        )
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
