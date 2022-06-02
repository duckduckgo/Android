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

package com.duckduckgo.mobile.android.vpn.prefs

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import java.util.UUID

class VpnSharedPreferencesProviderImplTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().context.applicationContext
    private lateinit var prefs: SharedPreferences
    private lateinit var vpnPreferencesProvider: VpnSharedPreferencesProvider
    private lateinit var NAME: String

    @Before
    fun setup() {
        NAME = UUID.randomUUID().toString()
        prefs = context.getSharedPreferences(NAME, MODE_PRIVATE)
        vpnPreferencesProvider = VpnSharedPreferencesProviderImpl(context)
    }

    @Test
    fun whenGetMultiprocessPreferencesThenMigrateToHarmony() {
        prefs.edit(commit = true) { putBoolean("bool", true) }
        prefs.edit(commit = true) { putString("string", "true") }
        prefs.edit(commit = true) { putInt("int", 1) }
        prefs.edit(commit = true) { putFloat("float", 1f) }
        prefs.edit(commit = true) { putLong("long", 1L) }

        val harmony = vpnPreferencesProvider.getSharedPreferences(NAME, multiprocess = true, migrate = true)

        assertEquals(true, harmony.getBoolean("bool", false))
        assertEquals("true", harmony.getString("string", "false"))
        assertEquals(1, harmony.getInt("int", 0))
        assertEquals(1f, harmony.getFloat("float", 0f))
        assertEquals(1L, harmony.getLong("long", 0L))
    }

    @Test
    fun whenGetMultiprocessPreferencesAndMigrateIsFalseThenDoNotMigrateToHarmony() {
        prefs.edit(commit = true) { putBoolean("bool", true) }
        prefs.edit(commit = true) { putString("string", "true") }
        prefs.edit(commit = true) { putInt("int", 1) }
        prefs.edit(commit = true) { putFloat("float", 1f) }
        prefs.edit(commit = true) { putLong("long", 1L) }

        val harmony = vpnPreferencesProvider.getSharedPreferences(NAME, multiprocess = true)

        assertNotEquals(true, harmony.getBoolean("bool", false))
        assertNotEquals("true", harmony.getString("string", "false"))
        assertNotEquals(1, harmony.getInt("int", 0))
        assertNotEquals(1f, harmony.getFloat("float", 0f))
        assertNotEquals(1L, harmony.getLong("long", 0L))
    }
}
