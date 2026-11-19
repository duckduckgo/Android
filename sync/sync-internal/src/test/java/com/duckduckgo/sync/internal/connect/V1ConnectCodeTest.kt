/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.sync.internal.connect

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.sync.crypto.ConnectKeys
import com.duckduckgo.sync.crypto.SyncLib
import com.duckduckgo.sync.impl.API_CODE.GONE
import com.duckduckgo.sync.impl.API_CODE.NOT_FOUND
import com.duckduckgo.sync.impl.RecoveryCode
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.SyncApi
import com.duckduckgo.sync.impl.SyncAuthCode
import com.duckduckgo.sync.impl.SyncDeviceIds
import com.duckduckgo.sync.impl.ui.qrcode.SyncBarcodeUrlWrapper
import com.duckduckgo.sync.store.SyncStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class V1ConnectCodeTest {

    private val syncDeviceIds: SyncDeviceIds = mock()
    private val nativeLib: SyncLib = mock()
    private val syncApi: SyncApi = mock()
    private val syncStore: SyncStore = mock()
    private val syncCodeUrlWrapper: SyncBarcodeUrlWrapper = mock()
    private val syncAccountRepository: SyncAccountRepository = mock()

    private val v1ConnectCode = V1ConnectCode(
        syncDeviceIds = syncDeviceIds,
        nativeLib = nativeLib,
        syncApi = syncApi,
        syncStore = syncStore,
        syncCodeUrlWrapper = syncCodeUrlWrapper,
        syncAccountRepository = syncAccountRepository,
    )

    @Before
    fun setup() {
        whenever(syncDeviceIds.deviceId()).thenReturn(DEVICE_ID)
        whenever(syncCodeUrlWrapper.wrapCodeInUrl(any())).thenReturn(WRAPPED_CODE)
    }

    @Test
    fun `getConnectQR returns the URL-wrapped connect code and stores the connect keys`() {
        whenever(nativeLib.prepareForConnect()).thenReturn(ConnectKeys(0, publicKey = PUBLIC_KEY, secretKey = SECRET_KEY))

        val result = v1ConnectCode.getConnectQR() as Success

        assertEquals(CONNECT_CODE_B64, result.data.rawCode)
        assertEquals(WRAPPED_CODE, result.data.qrCode)
        verify(syncStore).deviceId = DEVICE_ID
        verify(syncStore).primaryKey = PUBLIC_KEY
        verify(syncStore).secretKey = SECRET_KEY
    }

    @Test
    fun `getConnectQR fails without storing keys when the native lib returns an error`() {
        whenever(nativeLib.prepareForConnect()).thenReturn(ConnectKeys(1, publicKey = PUBLIC_KEY, secretKey = SECRET_KEY))

        assertEquals(Error::class, v1ConnectCode.getConnectQR()::class)
        verify(syncStore, never()).deviceId = any()
        verify(syncStore, never()).primaryKey = any()
        verify(syncStore, never()).secretKey = any()
    }

    @Test
    fun `getConnectQR fails without touching the stored keys when already signed in`() {
        whenever(syncStore.isSignedIn()).thenReturn(true)

        assertEquals(Error::class, v1ConnectCode.getConnectQR()::class)
        verify(syncStore, never()).primaryKey = any()
        verify(syncStore, never()).secretKey = any()
        verifyNoInteractions(nativeLib)
    }

    @Test
    fun `pollConnectionKeys returns false while the keys are not there yet`() {
        whenever(syncApi.connectDevice(DEVICE_ID)).thenReturn(Error(code = NOT_FOUND.code, reason = "not found"))

        val result = v1ConnectCode.pollConnectionKeys() as Success

        assertFalse(result.data)
    }

    @Test
    fun `pollConnectionKeys fails when the keys expired`() {
        whenever(syncApi.connectDevice(DEVICE_ID)).thenReturn(Error(code = GONE.code, reason = "gone"))

        val result = v1ConnectCode.pollConnectionKeys() as Error

        assertEquals(GONE.code, result.code)
    }

    @Test
    fun `pollConnectionKeys passes through other API errors`() {
        val apiError = Error(code = 500, reason = "server error")
        whenever(syncApi.connectDevice(DEVICE_ID)).thenReturn(apiError)

        assertEquals(apiError, v1ConnectCode.pollConnectionKeys())
    }

    @Test
    fun `pollConnectionKeys logs in with the received recovery code`() {
        givenStoredConnectKeys()
        whenever(syncApi.connectDevice(DEVICE_ID)).thenReturn(Success(SEALED_RECOVERY_CODE))
        whenever(nativeLib.sealOpen(SEALED_RECOVERY_CODE, PUBLIC_KEY, SECRET_KEY)).thenReturn(RECOVERY_CODE_JSON)
        val recoveryCode = SyncAuthCode.Recovery(RecoveryCode(primaryKey = PRIMARY_KEY, userId = USER_ID))
        whenever(syncAccountRepository.processCode(recoveryCode)).thenReturn(Success(true))

        val result = v1ConnectCode.pollConnectionKeys()

        assertEquals(Success(true), result)
        verify(syncAccountRepository).processCode(recoveryCode)
    }

    @Test
    fun `pollConnectionKeys fails without logging in when the seal cannot be opened`() {
        givenStoredConnectKeys()
        whenever(syncApi.connectDevice(DEVICE_ID)).thenReturn(Success(SEALED_RECOVERY_CODE))
        whenever(nativeLib.sealOpen(SEALED_RECOVERY_CODE, PUBLIC_KEY, SECRET_KEY)).thenThrow(RuntimeException())

        assertEquals(Error::class, v1ConnectCode.pollConnectionKeys()::class)
        verifyNoInteractions(syncAccountRepository)
    }

    @Test
    fun `pollConnectionKeys fails without logging in when the recovery code is malformed`() {
        givenStoredConnectKeys()
        whenever(syncApi.connectDevice(DEVICE_ID)).thenReturn(Success(SEALED_RECOVERY_CODE))
        whenever(nativeLib.sealOpen(SEALED_RECOVERY_CODE, PUBLIC_KEY, SECRET_KEY)).thenReturn("{ malformed json")

        assertEquals(Error::class, v1ConnectCode.pollConnectionKeys()::class)
        verifyNoInteractions(syncAccountRepository)
    }

    private fun givenStoredConnectKeys() {
        whenever(syncStore.primaryKey).thenReturn(PUBLIC_KEY)
        whenever(syncStore.secretKey).thenReturn(SECRET_KEY)
    }

    private companion object {
        const val DEVICE_ID = "deviceId"
        const val USER_ID = "userId"
        const val PUBLIC_KEY = "publicKey"
        const val SECRET_KEY = "secretKey"
        const val PRIMARY_KEY = "primaryKey"
        const val SEALED_RECOVERY_CODE = "sealedRecoveryCode"
        const val WRAPPED_CODE = "https://duckduckgo.com/sync/pairing#code=wrapped"
        const val RECOVERY_CODE_JSON = """{"recovery":{"primary_key":"$PRIMARY_KEY","user_id":"$USER_ID"}}"""
        val CONNECT_CODE_B64: String = Base64.encodeToString(
            """{"connect":{"device_id":"$DEVICE_ID","secret_key":"$PUBLIC_KEY"}}""".toByteArray(),
            Base64.NO_WRAP,
        )
    }
}
