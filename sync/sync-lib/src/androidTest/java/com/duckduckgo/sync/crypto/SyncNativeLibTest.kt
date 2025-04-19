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

package com.duckduckgo.sync.crypto

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import okio.ByteString.Companion.decodeBase64
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncNativeLibTest {

    @Test
    fun whenGeneratingAccountKeysThenPrimaryIsDeterministic() {
        val syncNativeLib = SyncNativeLib(InstrumentationRegistry.getInstrumentation().targetContext)
        val account = syncNativeLib.generateAccountKeys(aUserId, aPassword)
        val firstPK = account.primaryKey
        val account2 = syncNativeLib.generateAccountKeys(aUserId, aPassword)
        val secondPK = account2.primaryKey

        assertEquals(firstPK, secondPK)
    }

    @Test
    fun whenGeneratingAccountKeysThenPrimaryIs32Bytes() {
        val syncNativeLib = SyncNativeLib(InstrumentationRegistry.getInstrumentation().targetContext)
        val account = syncNativeLib.generateAccountKeys(aUserId, aPassword)
        val primaryKey = account.primaryKey

        assertEquals(32, primaryKey.decodeBase64()?.size)
    }

    @Test
    fun whenGeneratingAccountKeysThenSecretKeyIsNonDeterministic() {
        val syncNativeLib = SyncNativeLib(InstrumentationRegistry.getInstrumentation().targetContext)
        val account = syncNativeLib.generateAccountKeys(aUserId, aPassword)
        val firstSK = account.secretKey
        val account2 = syncNativeLib.generateAccountKeys(aUserId, aPassword)
        val secondSK = account2.secretKey

        assertNotEquals(firstSK, secondSK)
    }

    @Test
    fun whenGeneratingAccountKeysThenProtectedkeyIs72Bytes() {
        val syncNativeLib = SyncNativeLib(InstrumentationRegistry.getInstrumentation().targetContext)
        val account = syncNativeLib.generateAccountKeys(aUserId, aPassword)
        val protectedKey = account.protectedSecretKey

        assertEquals(72, protectedKey.decodeBase64()?.size)
    }

    @Test
    fun testWhenGivenRecoveryKeyThenPasswordHashMatches() {
        val syncNativeLib = SyncNativeLib(InstrumentationRegistry.getInstrumentation().targetContext)
        val accountKeys = syncNativeLib.generateAccountKeys(aUserId, aPassword)
        val prepareForLogin = syncNativeLib.prepareForLogin(accountKeys.primaryKey)

        assertEquals(accountKeys.passwordHash, prepareForLogin.passwordHash)
    }

    @Test
    fun testWhenGivenRecoveryKeyThenCanExtractSecretKey() {
        val syncNativeLib = SyncNativeLib(InstrumentationRegistry.getInstrumentation().targetContext)
        val accountKeys = syncNativeLib.generateAccountKeys(aUserId, aPassword)
        val prepareForLogin = syncNativeLib.prepareForLogin(accountKeys.primaryKey)

        val decryptedSecretKey = syncNativeLib.decrypt(accountKeys.protectedSecretKey, prepareForLogin.stretchedPrimaryKey)

        assertEquals(accountKeys.secretKey, decryptedSecretKey.decryptedData)
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    fun whenEncryptingThenResultIsSuccess() {
        val syncNativeLib = SyncNativeLib(InstrumentationRegistry.getInstrumentation().targetContext)
        val accountKeys = syncNativeLib.generateAccountKeys(aUserId, aPassword)

        val whatToEncrypt = "bookmark"

        val encryptedResult = syncNativeLib.encryptData(whatToEncrypt, accountKeys.primaryKey)

        assertEquals(0, encryptedResult.result)
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    fun whenDataIsEncryptedItDecryptsProperly() {
        val syncNativeLib = SyncNativeLib(InstrumentationRegistry.getInstrumentation().targetContext)
        val accountKeys = syncNativeLib.generateAccountKeys(aUserId, aPassword)

        val whatToEncrypt = "bookmark"

        val encryptedResult = syncNativeLib.encryptData(whatToEncrypt, accountKeys.primaryKey)
        val decryptedResult = syncNativeLib.decryptData(encryptedResult.encryptedData, accountKeys.primaryKey)

        assertEquals(0, decryptedResult.result)
        assertEquals(whatToEncrypt, decryptedResult.decryptedData)
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    fun whenPrepareForConnectThenResultSuccess() {
        val syncNativeLib = SyncNativeLib(InstrumentationRegistry.getInstrumentation().targetContext)
        val prepareForConnect = syncNativeLib.prepareForConnect()
        assertEquals(0, prepareForConnect.result)
    }

    @Test
    fun whenSealThenSuccess() {
        val syncNativeLib = SyncNativeLib(InstrumentationRegistry.getInstrumentation().targetContext)
        val prepareForConnect = syncNativeLib.prepareForConnect()
        val result = syncNativeLib.seal("hola-1324-2342", prepareForConnect.publicKey)
        val result2 = syncNativeLib.sealOpen(result, prepareForConnect.publicKey, prepareForConnect.secretKey)
        assertEquals("hola-1324-2342", result2)
    }

    @Test
    fun whenDecodeEncodeThenReturnSameValue() {
        val decoded = "hola-1213-1231-123".toByteArray()
        assertEquals("hola-1213-1231-123", String(decoded))
    }

    companion object {
        private const val aUserId = "user"
        private const val aPassword = "password"
    }
}
