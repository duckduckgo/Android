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

package com.duckduckgo.sync.impl.auditlog

import android.content.SharedPreferences
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.api.InMemorySharedPreferences
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RealNewLoginNotificationSettingTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val preferences = InMemorySharedPreferences()

    private val setting = RealNewLoginNotificationSetting(
        sharedPreferencesProvider = object : SharedPreferencesProvider {
            override fun getSharedPreferences(name: String, multiprocess: Boolean, migrate: Boolean): SharedPreferences = preferences
            override fun getEncryptedSharedPreferences(name: String, multiprocess: Boolean): SharedPreferences? = null
            override suspend fun getMigratedEncryptedSharedPreferences(name: String): SharedPreferences? = null
            override suspend fun getMigratedEncryptedSharedPreferences(origin: SharedPreferences, name: String): SharedPreferences? = null
        },
        dispatchers = coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun whenNothingStoredThenEnabled() = runTest {
        assertTrue(setting.isEnabled())
    }

    @Test
    fun whenTurnedOffThenDisabled() = runTest {
        setting.setEnabled(false)

        assertFalse(setting.isEnabled())
    }

    @Test
    fun whenTurnedOffThenOnThenEnabled() = runTest {
        setting.setEnabled(false)
        setting.setEnabled(true)

        assertTrue(setting.isEnabled())
    }

    @Test
    fun whenNothingStoredThenCardNotDismissed() = runTest {
        assertFalse(setting.isCardDismissed())
    }

    @Test
    fun whenCardDismissedThenStaysDismissed() = runTest {
        setting.dismissCard()

        assertTrue(setting.isCardDismissed())
    }
}
