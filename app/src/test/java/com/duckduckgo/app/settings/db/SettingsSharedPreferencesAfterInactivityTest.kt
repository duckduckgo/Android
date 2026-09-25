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

package com.duckduckgo.app.settings.db

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import com.duckduckgo.app.onboardingbranddesignupdate.OnboardingBrandDesignUpdateToggles
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@RunWith(AndroidJUnit4::class)
class SettingsSharedPreferencesAfterInactivityTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences: SharedPreferences = context.getSharedPreferences(SettingsSharedPreferences.FILENAME, Context.MODE_PRIVATE)
    private val testee = SettingsSharedPreferences(
        context,
        mock<AppBuildConfig>(),
        dagger.Lazy<OnboardingBrandDesignUpdateToggles> { mock() },
    )

    @Before
    fun setUp() {
        preferences.edit(commit = true) { clear() }
    }

    @After
    fun tearDown() {
        preferences.edit(commit = true) { clear() }
    }

    @Test
    fun whenIdleTimeoutIsSetThenFlowEmitsPersistedSelection() = runTest {
        testee.userSelectedIdleThresholdSecondsFlow.test {
            assertNull(awaitItem())

            testee.userSelectedIdleThresholdSeconds = 600L

            assertEquals(600L, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
