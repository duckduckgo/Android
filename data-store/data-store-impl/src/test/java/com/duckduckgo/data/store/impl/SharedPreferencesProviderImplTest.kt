/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.data.store.impl

import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.system.ErrnoException
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.anrs.api.CrashLogger
import com.duckduckgo.anrs.api.CrashLogger.Crash
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import java.util.UUID
import java.util.concurrent.Executor

@RunWith(AndroidJUnit4::class)
class SharedPreferencesProviderImplTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().context.applicationContext
    private lateinit var prefs: SharedPreferences
    private lateinit var vpnPreferencesProvider: SharedPreferencesProvider
    private lateinit var name: String

    private val crashLogger = object : CrashLogger {
        val crashes = mutableListOf<Crash>()
        override fun logCrash(crash: Crash) {
            crashes.add(crash)
        }
    }

    @SuppressLint("DenyListedApi")
    @Before
    fun setup() {
        name = UUID.randomUUID().toString()
        prefs = context.getSharedPreferences(name, MODE_PRIVATE)
        vpnPreferencesProvider = SharedPreferencesProviderImpl(
            context = context,
            dispatcherProvider = coroutinesTestRule.testDispatcherProvider,
            pixelLazy = { mock() },
            dataStoreProviderFeatureLazy = { mock() },
            crashLogger = { crashLogger },
        )
    }

    @After
    fun teardown() {
        context.deleteSharedPreferences(name)
        context.deleteSharedPreferences("$name.harmony")
    }

    @Test
    fun whenGetMultiprocessPreferencesThenMigrateToHarmony() {
        putAllTypes(prefs)

        val harmony = vpnPreferencesProvider.getSharedPreferences(name, multiprocess = true, migrate = true)

        assertEquals(true, harmony.getBoolean("bool", false))
        assertEquals("true", harmony.getString("string", "false"))
        assertEquals(1, harmony.getInt("int", 0))
        assertEquals(1f, harmony.getFloat("float", 0f))
        assertEquals(1L, harmony.getLong("long", 0L))
    }

    @Test
    fun whenGetMultiprocessPreferencesAndMigrateIsFalseThenDoNotMigrateToHarmony() {
        putAllTypes(prefs)

        val harmony = vpnPreferencesProvider.getSharedPreferences(name, multiprocess = true)

        // Data should not have been migrated yet
        assertFalse(harmony.getBoolean("bool", false))
        assertEquals("false", harmony.getString("string", "false"))
        assertEquals(0, harmony.getInt("int", 0))
        assertEquals(0f, harmony.getFloat("float", 0f))
        assertEquals(0L, harmony.getLong("long", 0L))
    }

    @Test
    fun whenSafeSharedPreferencesFails_thenCrashIsLoggedAndDefaultReturned() {
        val brokenPrefs = object : SharedPreferences by prefs {
            override fun getString(key: String?, defValue: String?): String? {
                throw ErrnoException("fsync", 5)
            }
        }

        val sameThreadExecutor = Executor { it.run() }
        val safe = SafeSharedPreferences(brokenPrefs, crashLogger, crashLoggerExecutor = sameThreadExecutor)

        val result = safe.getString("key", "fallback")
        assertEquals("fallback", result)

        assertTrue(crashLogger.crashes.any { it.shortName == "shared-prefs" })
    }

    @Test
    fun whenSafeSharedPreferencesWorksNormally_thenNoCrashLogged() {
        val sameThreadExecutor = Executor { it.run() }
        val safe = SafeSharedPreferences(prefs, crashLogger, crashLoggerExecutor = sameThreadExecutor)

        safe.edit(commit = true) {
            putBoolean("bool", true)
            putInt("int", 42)
        }

        assertEquals(true, safe.getBoolean("bool", false))
        assertEquals(42, safe.getInt("int", 0))
        assertTrue(crashLogger.crashes.isEmpty())
    }

    private fun putAllTypes(prefs: SharedPreferences) {
        prefs.edit(commit = true) {
            putBoolean("bool", true)
            putString("string", "true")
            putInt("int", 1)
            putFloat("float", 1f)
            putLong("long", 1L)
        }
    }
}
