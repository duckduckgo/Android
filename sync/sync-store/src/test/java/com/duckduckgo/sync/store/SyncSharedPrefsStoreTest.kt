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

package com.duckduckgo.sync.store

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncSharedPrefsStoreTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var store: SyncSharedPrefsStore
    private val sharedPrefsProvider =
        TestSharedPrefsProvider(InstrumentationRegistry.getInstrumentation().context)

    @Before
    fun setUp() {
        store = SyncSharedPrefsStore(sharedPrefsProvider, TestScope(), coroutineRule.testDispatcherProvider)
    }

    @Test
    fun whenUserIdStoredThenValueUpdatedInPrefsStore() {
        assertNull(store.userId)
        store.userId = "test_user"
        assertEquals("test_user", store.userId)
        store.userId = null
        assertNull(store.userId)
    }

    @Test
    fun whenDeviceNameStoredThenValueUpdatedInPrefsStore() {
        assertNull(store.deviceName)
        store.deviceName = "test_device"
        assertEquals("test_device", store.deviceName)
        store.deviceName = null
        assertNull(store.deviceName)
    }

    @Test
    fun whenDeviceIdStoredThenValueUpdatedInPrefsStore() {
        assertNull(store.deviceId)
        store.deviceId = "test_device_id"
        assertEquals("test_device_id", store.deviceId)
        store.deviceId = null
        assertNull(store.deviceId)
    }

    @Test
    fun whenStoreCredentialsThenValuesUpdatedInPrefsStore() {
        assertNull(store.userId)
        assertNull(store.deviceName)
        assertNull(store.deviceId)
        assertNull(store.primaryKey)
        assertNull(store.secretKey)
        assertNull(store.token)
        store.storeCredentials("userId", "deviceId", "deviceName", "primaryKey", "secretKey", "token")
        assertEquals("userId", store.userId)
        assertEquals("deviceName", store.deviceName)
        assertEquals("deviceId", store.deviceId)
        assertEquals("primaryKey", store.primaryKey)
        assertEquals("secretKey", store.secretKey)
        assertEquals("token", store.token)
    }

    @Test
    fun whenIsSignedInThenReturnTrueIfUserHasAuthKeys() {
        store.storeCredentials("userId", "deviceId", "deviceName", "primaryKey", "secretKey", "token")

        assertTrue(store.isSignedIn())
    }

    @Test
    fun whenClearAllThenReturnRemoveAllKeys() {
        store.storeCredentials("userId", "deviceId", "deviceName", "primaryKey", "secretKey", "token")
        assertEquals("userId", store.userId)
        assertEquals("deviceName", store.deviceName)
        assertEquals("deviceId", store.deviceId)
        assertEquals("primaryKey", store.primaryKey)
        assertEquals("secretKey", store.secretKey)
        assertEquals("token", store.token)

        store.clearAll()

        assertNull(store.userId)
        assertNull(store.deviceName)
        assertNull(store.deviceId)
        assertNull(store.primaryKey)
        assertNull(store.secretKey)
        assertNull(store.token)
    }
}
