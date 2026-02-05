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

package com.duckduckgo.app.data.store.impl

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.anrs.api.CrashLogger
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.data.store.impl.SharedPreferencesProviderImpl
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import java.util.UUID

class SharedPreferencesProviderImplTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var prefs: SharedPreferences
    private lateinit var preferencesProvider: SharedPreferencesProvider
    private lateinit var name: String

    private val crashLogger = object : CrashLogger {
        override fun logCrash(crash: CrashLogger.Crash) {
            TODO("Not implemented yet")
        }
    }

    @SuppressLint("DenyListedApi")
    @Before
    fun setup() {
        name = UUID.randomUUID().toString()
        prefs = EncryptedSharedPreferences.create(
            context,
            name,
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        preferencesProvider = SharedPreferencesProviderImpl(
            context,
            coroutineRule.testDispatcherProvider,
            { mock() },
            { mock() },
            { crashLogger },
        )
    }

    @After
    fun teardown() {
        context.deleteSharedPreferences(name)
        context.deleteSharedPreferences("$name.harmony")
    }

    @Test
    fun whenGetEncryptedMultiprocessPreferencesThenMigrateToHarmony() = runTest {
        putAllTypes(prefs)

        preferencesProvider.getMigratedEncryptedSharedPreferences(name).let { harmony ->
            assertEquals(true, harmony?.getBoolean("bool", false))
            assertEquals("true", harmony?.getString("string", "false"))
            assertEquals(1, harmony?.getInt("int", 0))
            assertEquals(1f, harmony?.getFloat("float", 0f))
            assertEquals(1L, harmony?.getLong("long", 0L))
        }
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
