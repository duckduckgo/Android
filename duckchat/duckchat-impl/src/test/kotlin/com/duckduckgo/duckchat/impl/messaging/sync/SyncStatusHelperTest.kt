/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.duckchat.impl.messaging.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.sync.api.DeviceSyncState
import com.duckduckgo.sync.api.DeviceSyncState.ConnectedDevice
import com.duckduckgo.sync.api.DeviceSyncState.SyncAccountState.SignedIn
import com.duckduckgo.sync.api.DeviceSyncState.SyncAccountState.SignedOut
import com.duckduckgo.sync.api.DeviceSyncState.Type
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class SyncStatusHelperTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val mockDeviceSyncState: DeviceSyncState = mock()

    private val testee = SyncStatusHelper(
        deviceSyncState = mockDeviceSyncState,
        dispatcherProvider = coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun `when signed out and feature disabled then all account fields are null and syncAvailable is false`() = runTest {
        whenever(mockDeviceSyncState.isFeatureEnabled()).thenReturn(false)
        whenever(mockDeviceSyncState.getAccountState()).thenReturn(SignedOut)

        val payload = testee.buildSyncStatusPayload()

        assertSyncAvailable(payload, false)
        assertAllAccountFieldsAreNull(payload)
    }

    @Test
    fun `when signed out and feature enabled then all account fields are null and syncAvailable is true`() = runTest {
        whenever(mockDeviceSyncState.isFeatureEnabled()).thenReturn(true)
        whenever(mockDeviceSyncState.getAccountState()).thenReturn(SignedOut)

        val payload = testee.buildSyncStatusPayload()

        assertSyncAvailable(payload, true)
        assertAllAccountFieldsAreNull(payload)
    }

    @Test
    fun `when signed in with mobile device then all fields are populated`() = runTest {
        whenever(mockDeviceSyncState.isFeatureEnabled()).thenReturn(true)
        val device = ConnectedDevice(
            deviceId = "device123",
            deviceName = "MyPhone",
            deviceType = Type.MOBILE,
            thisDevice = true,
        )
        whenever(mockDeviceSyncState.getAccountState()).thenReturn(
            SignedIn("user456", listOf(device)),
        )

        val payload = testee.buildSyncStatusPayload()

        assertSyncAvailable(payload, true)
        assertAccountFieldsPopulated(payload, "user456", "device123", "MyPhone", "mobile")
    }

    @Test
    fun `when signed in with desktop device then deviceType is desktop`() = runTest {
        whenever(mockDeviceSyncState.isFeatureEnabled()).thenReturn(true)
        val device = ConnectedDevice(
            deviceId = "device789",
            deviceName = "MyLaptop",
            deviceType = Type.DESKTOP,
            thisDevice = true,
        )
        whenever(mockDeviceSyncState.getAccountState()).thenReturn(SignedIn("user456", listOf(device)))

        val payload = testee.buildSyncStatusPayload()

        assertSyncAvailable(payload, true)
        assertAccountFieldsPopulated(payload, "user456", "device789", "MyLaptop", "desktop")
    }

    @Test
    fun `when signed in with unknown device type then deviceType is unknown`() = runTest {
        whenever(mockDeviceSyncState.isFeatureEnabled()).thenReturn(true)
        val device = ConnectedDevice(
            deviceId = "device999",
            deviceName = "UnknownDevice",
            deviceType = Type.UNKNOWN,
            thisDevice = true,
        )
        whenever(mockDeviceSyncState.getAccountState()).thenReturn(SignedIn("user456", listOf(device)))

        val payload = testee.buildSyncStatusPayload()

        assertSyncAvailable(payload, true)
        assertAccountFieldsPopulated(payload, "user456", "device999", "UnknownDevice", "unknown")
    }

    @Test
    fun `when signed in but thisDevice not found then userId is populated but device fields are null`() = runTest {
        whenever(mockDeviceSyncState.isFeatureEnabled()).thenReturn(true)
        val otherDevice = ConnectedDevice(
            deviceId = "otherDevice",
            deviceName = "OtherPhone",
            deviceType = Type.MOBILE,
            thisDevice = false,
        )
        whenever(mockDeviceSyncState.getAccountState()).thenReturn(SignedIn("user789", listOf(otherDevice)))

        val payload = testee.buildSyncStatusPayload()

        assertSyncAvailable(payload, true)
        assertEquals("user789", payload.getString("userId"))
        assertDeviceFieldsAreNull(payload)
    }

    @Test
    fun `when signed in with empty devices list then userId is populated but device fields are null`() = runTest {
        whenever(mockDeviceSyncState.isFeatureEnabled()).thenReturn(true)
        whenever(mockDeviceSyncState.getAccountState()).thenReturn(SignedIn("user999", emptyList()))
        val payload = testee.buildSyncStatusPayload()

        assertSyncAvailable(payload, true)
        assertEquals("user999", payload.getString("userId"))
        assertDeviceFieldsAreNull(payload)
    }

    @Test
    fun `when signed in with multiple devices then finds thisDevice correctly`() = runTest {
        whenever(mockDeviceSyncState.isFeatureEnabled()).thenReturn(true)
        val otherDevice1 = ConnectedDevice(
            deviceId = "other1",
            deviceName = "Other1",
            deviceType = Type.MOBILE,
            thisDevice = false,
        )
        val thisDevice = ConnectedDevice(
            deviceId = "thisOne",
            deviceName = "ThisDevice",
            deviceType = Type.MOBILE,
            thisDevice = true,
        )
        val otherDevice2 = ConnectedDevice(
            deviceId = "other2",
            deviceName = "Other2",
            deviceType = Type.DESKTOP,
            thisDevice = false,
        )
        whenever(mockDeviceSyncState.getAccountState()).thenReturn(
            SignedIn("user111", listOf(otherDevice1, thisDevice, otherDevice2)),
        )

        val payload = testee.buildSyncStatusPayload()

        assertSyncAvailable(payload, true)
        assertAccountFieldsPopulated(payload, "user111", "thisOne", "ThisDevice", "mobile")
    }

    private fun assertSyncAvailable(payload: JSONObject, expected: Boolean) {
        assertEquals(expected, payload.getBoolean("syncAvailable"))
    }

    private fun assertAllAccountFieldsAreNull(payload: JSONObject) {
        assertTrue(payload.isNull("userId"))
        assertTrue(payload.isNull("deviceId"))
        assertTrue(payload.isNull("deviceName"))
        assertTrue(payload.isNull("deviceType"))
    }

    private fun assertDeviceFieldsAreNull(payload: JSONObject) {
        assertTrue(payload.isNull("deviceId"))
        assertTrue(payload.isNull("deviceName"))
        assertTrue(payload.isNull("deviceType"))
    }

    private fun assertAccountFieldsPopulated(
        payload: JSONObject,
        userId: String,
        deviceId: String,
        deviceName: String,
        deviceType: String,
    ) {
        assertEquals(userId, payload.getString("userId"))
        assertEquals(deviceId, payload.getString("deviceId"))
        assertEquals(deviceName, payload.getString("deviceName"))
        assertEquals(deviceType, payload.getString("deviceType"))
    }
}
