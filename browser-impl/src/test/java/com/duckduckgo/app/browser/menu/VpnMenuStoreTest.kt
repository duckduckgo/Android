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

package com.duckduckgo.app.browser.menu

import android.content.SharedPreferences
import android.content.SharedPreferences.Editor
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class VpnMenuStoreTest {

    private val mockSharedPreferencesProvider: SharedPreferencesProvider = mock()
    private val mockSharedPreferences: SharedPreferences = mock()
    private val mockEditor: Editor = mock()

    private lateinit var testee: RealVpnMenuStore

    @Before
    fun setUp() {
        whenever(mockSharedPreferencesProvider.getSharedPreferences(RealVpnMenuStore.FILENAME)).thenReturn(mockSharedPreferences)
        whenever(mockSharedPreferences.edit()).thenReturn(mockEditor)
        testee = RealVpnMenuStore(mockSharedPreferencesProvider)
    }

    @Test
    fun `when vpnMenuNotSubscribedShownCount is 0 then canShowVpnMenuForNotSubscribed returns true`() {
        whenever(mockSharedPreferences.getInt(RealVpnMenuStore.KEY_VPN_MENU_NOT_SUBSCRIBED_SHOWN_COUNT, 0)).thenReturn(0)

        assertTrue(testee.canShowVpnMenuForNotSubscribed())
    }

    @Test
    fun `when vpnMenuNotSubscribedShownCount is 3 then canShowVpnMenuForNotSubscribed returns true`() {
        whenever(mockSharedPreferences.getInt(RealVpnMenuStore.KEY_VPN_MENU_NOT_SUBSCRIBED_SHOWN_COUNT, 0)).thenReturn(3)

        assertTrue(testee.canShowVpnMenuForNotSubscribed())
    }

    @Test
    fun `when vpnMenuNotSubscribedShownCount is 4 then canShowVpnMenuForNotSubscribed returns false`() {
        whenever(mockSharedPreferences.getInt(RealVpnMenuStore.KEY_VPN_MENU_NOT_SUBSCRIBED_SHOWN_COUNT, 0)).thenReturn(4)

        assertFalse(testee.canShowVpnMenuForNotSubscribed())
    }

    @Test
    fun `when vpnMenuNotSubscribedShownCount is 5 then canShowVpnMenuForNotSubscribed returns false`() {
        whenever(mockSharedPreferences.getInt(RealVpnMenuStore.KEY_VPN_MENU_NOT_SUBSCRIBED_SHOWN_COUNT, 0)).thenReturn(5)

        assertFalse(testee.canShowVpnMenuForNotSubscribed())
    }

    @Test
    fun `when incrementVpnMenuShownCount is called then count is incremented by 1`() {
        whenever(mockSharedPreferences.getInt(RealVpnMenuStore.KEY_VPN_MENU_NOT_SUBSCRIBED_SHOWN_COUNT, 0)).thenReturn(2)

        testee.incrementVpnMenuShownCount()

        verify(mockEditor).putInt(RealVpnMenuStore.KEY_VPN_MENU_NOT_SUBSCRIBED_SHOWN_COUNT, 3)
    }

    @Test
    fun `when vpnMenuNotSubscribedShownCount getter is called then returns correct value from preferences`() {
        whenever(mockSharedPreferences.getInt(RealVpnMenuStore.KEY_VPN_MENU_NOT_SUBSCRIBED_SHOWN_COUNT, 0)).thenReturn(2)

        assertEquals(2, testee.vpnMenuNotSubscribedShownCount)
    }

    @Test
    fun `when vpnMenuNotSubscribedShownCount setter is called then saves value to preferences`() {
        testee.vpnMenuNotSubscribedShownCount = 3

        verify(mockEditor).putInt(RealVpnMenuStore.KEY_VPN_MENU_NOT_SUBSCRIBED_SHOWN_COUNT, 3)
    }

    @Test
    fun `verify MAX_VPN_MENU_SHOWN_COUNT is 4`() {
        assertEquals(4, RealVpnMenuStore.MAX_VPN_MENU_SHOWN_COUNT)
    }
}
