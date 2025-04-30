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
import kotlinx.coroutines.test.runTest
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
        store = SyncSharedPrefsStore(
            sharedPrefsProvider,
            TestScope(),
            coroutineRule.testDispatcherProvider,
            createAsyncPreferences = true,
        )
    }

    @Test
    fun whenUserIdStoredThenValueUpdatedInPrefsStore() = runTest {
        assertNull(store.getUserId())
        store.setUserId("test_user")
        assertEquals("test_user", store.getUserId())
        store.setUserId(null)
        assertNull(store.getUserId())
    }

    @Test
    fun whenDeviceNameStoredThenValueUpdatedInPrefsStore() = runTest {
        assertNull(store.getDeviceName())
        store.setDeviceName("test_device")
        assertEquals("test_device", store.getDeviceName())
        store.setDeviceName(null)
        assertNull(store.getDeviceName())
    }

    @Test
    fun whenDeviceIdStoredThenValueUpdatedInPrefsStore() = runTest {
        assertNull(store.getDeviceId())
        store.setDeviceId("test_device_id")
        assertEquals("test_device_id", store.getDeviceId())
        store.setDeviceId(null)
        assertNull(store.getDeviceId())
    }

    @Test
    fun whenStoreCredentialsThenValuesUpdatedInPrefsStore() = runTest {
        assertNull(store.getUserId())
        assertNull(store.getDeviceName())
        assertNull(store.getDeviceId())
        assertNull(store.getPrimaryKey())
        assertNull(store.getSecretKey())
        assertNull(store.getToken())
        store.storeCredentials("userId", "deviceId", "deviceName", "primaryKey", "secretKey", "token")
        assertEquals("userId", store.getUserId())
        assertEquals("deviceName", store.getDeviceName())
        assertEquals("deviceId", store.getDeviceId())
        assertEquals("primaryKey", store.getPrimaryKey())
        assertEquals("secretKey", store.getSecretKey())
        assertEquals("token", store.getToken())
    }

    @Test
    fun whenIsSignedInThenReturnTrueIfUserHasAuthKeys() = runTest {
        store.storeCredentials("userId", "deviceId", "deviceName", "primaryKey", "secretKey", "token")

        assertTrue(store.isSignedIn())
    }

    @Test
    fun whenClearAllThenReturnRemoveAllKeys() = runTest {
        store.storeCredentials("userId", "deviceId", "deviceName", "primaryKey", "secretKey", "token")
        assertEquals("userId", store.getUserId())
        assertEquals("deviceName", store.getDeviceName())
        assertEquals("deviceId", store.getDeviceId())
        assertEquals("primaryKey", store.getPrimaryKey())
        assertEquals("secretKey", store.getSecretKey())
        assertEquals("token", store.getToken())

        store.clearAll()

        assertNull(store.getUserId())
        assertNull(store.getDeviceName())
        assertNull(store.getDeviceId())
        assertNull(store.getPrimaryKey())
        assertNull(store.getSecretKey())
        assertNull(store.getToken())
    }
}
