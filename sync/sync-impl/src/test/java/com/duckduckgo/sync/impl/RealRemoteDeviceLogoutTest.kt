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

package com.duckduckgo.sync.impl

import com.duckduckgo.sync.TestSyncFixtures.token
import com.duckduckgo.sync.impl.AccountErrorCodes.GENERIC_ERROR
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.pixels.SyncAccountOperation
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.store.SyncStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class RealRemoteDeviceLogoutTest {

    private val syncStore: SyncStore = mock()
    private val syncApi: SyncApi = mock()
    private val syncPixels: SyncPixels = mock()

    private val testee = RealRemoteDeviceLogout(syncStore, syncApi, syncPixels)

    @Test
    fun whenLogoutSucceedsThenReturnSuccess() {
        whenever(syncStore.token).thenReturn(token)
        whenever(syncApi.logout(token, OTHER_DEVICE_ID)).thenReturn(Success(Logout(OTHER_DEVICE_ID)))

        val result = testee.logout(OTHER_DEVICE_ID)

        assertEquals(Success(true), result)
        verifyNoInteractions(syncPixels)
    }

    @Test
    fun whenLogoutFailsThenFireRemoveDeviceErrorPixelAndReturnGenericError() {
        val error = Error(code = 400, reason = "bad request")
        whenever(syncStore.token).thenReturn(token)
        whenever(syncApi.logout(token, OTHER_DEVICE_ID)).thenReturn(error)

        val result = testee.logout(OTHER_DEVICE_ID)

        assertEquals(error.copy(code = GENERIC_ERROR.code), result)
        verify(syncPixels).fireSyncAccountErrorPixel(error, SyncAccountOperation.REMOVE_DEVICE)
    }

    @Test
    fun whenLogoutFailsWithErrorPixelsOffThenNoPixelFired() {
        whenever(syncStore.token).thenReturn(token)
        whenever(syncApi.logout(token, OTHER_DEVICE_ID)).thenReturn(Error(code = 400, reason = "bad request"))

        testee.logout(OTHER_DEVICE_ID, reportAccountErrorPixel = false)

        verifyNoInteractions(syncPixels)
    }

    @Test
    fun whenTokenMissingWithErrorPixelsOffThenNoPixelFired() {
        whenever(syncStore.token).thenReturn(null)

        testee.logout(OTHER_DEVICE_ID, reportAccountErrorPixel = false)

        verifyNoInteractions(syncPixels)
    }

    @Test
    fun whenTokenMissingThenFireLogoutErrorPixelWithoutCallingApi() {
        whenever(syncStore.token).thenReturn(null)

        val result = testee.logout(OTHER_DEVICE_ID)

        assertTrue(result is Error)
        verify(syncPixels).fireSyncAccountErrorPixel(any(), eq(SyncAccountOperation.LOGOUT))
        verifyNoInteractions(syncApi)
    }

    @Test
    fun whenDeviceIdBlankThenSkipWithoutCallingApiOrFiringPixels() {
        whenever(syncStore.token).thenReturn(token)

        val result = testee.logout(" ")

        assertTrue(result is Error)
        verifyNoInteractions(syncApi)
        verifyNoInteractions(syncPixels)
    }

    private companion object {
        const val OTHER_DEVICE_ID = "other-device-id"
    }
}
