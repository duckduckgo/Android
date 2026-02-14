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

package com.duckduckgo.app.buildconfig

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.onboardingbranddesignupdate.OnboardingBrandDesignUpdateToggles
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.experiments.api.VariantManager
import com.duckduckgo.feature.toggles.api.Toggle
import dagger.Lazy
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config

/**
 * Note on test constraints:
 * `Build.VERSION.SDK_INT` defaults to a value < 30 in Robolectric, so `isAppReinstall()` takes
 * the early return path. Tests that need to exercise the full code path use
 * `@Config(sdk = [30])` to set SDK_INT = 30.
 */
@RunWith(AndroidJUnit4::class)
class RealAppBuildConfigIsAppReinstallTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule(StandardTestDispatcher())

    private val mockVariantManager: Lazy<VariantManager> = mock()
    private val mockSharedPreferencesProvider: SharedPreferencesProvider = mock()
    private val mockContext: Context = mock()
    private val mockOnboardingBrandDesignUpdateToggles: OnboardingBrandDesignUpdateToggles = mock()
    private val mockOnboardingBrandDesignUpdateTogglesLazy: Lazy<OnboardingBrandDesignUpdateToggles> = mock {
        on { get() }.thenReturn(mockOnboardingBrandDesignUpdateToggles)
    }

    private lateinit var testee: RealAppBuildConfig

    @Before
    fun setUp() {
        // Provide real SharedPreferences for tests that need SDK >= 30 paths.
        // For SDK < 30 tests, preferences are never accessed so this is harmless.
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val realPrefs = appContext.getSharedPreferences("com.duckduckgo.app.buildconfig.cache.test", Context.MODE_PRIVATE)
        realPrefs.edit().clear().commit()
        whenever(mockSharedPreferencesProvider.getSharedPreferences(any(), any<Boolean>(), any<Boolean>())).thenReturn(realPrefs)

        testee = RealAppBuildConfig(
            variantManager = mockVariantManager,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            sharedPreferencesProvider = mockSharedPreferencesProvider,
            context = mockContext,
            onboardingBrandDesignUpdateToggles = mockOnboardingBrandDesignUpdateTogglesLazy,
        )
    }

    @Test
    fun `isAppReinstall returns false when sdkInt is below 30`() = runTest(coroutineRule.testDispatcher) {
        val result = testee.isAppReinstall()

        assertFalse("isAppReinstall should return false when SDK_INT < 30", result)
    }

    @Test
    @Config(sdk = [30])
    fun `isAppReinstall exercises Mutex path when toggle enabled and cache is empty`() = runTest(coroutineRule.testDispatcher) {
        val mockToggle: Toggle = mock { on { isEnabled() }.thenReturn(true) }
        whenever(mockOnboardingBrandDesignUpdateToggles.isAppReinstallMutex()).thenReturn(mockToggle)

        val result = testee.isAppReinstall()

        assertFalse("isAppReinstall should return false for new install via Mutex path", result)
    }

    @Test
    @Config(sdk = [30])
    fun `isAppReinstall returns cached result on second call with Mutex enabled`() = runTest(coroutineRule.testDispatcher) {
        val mockToggle: Toggle = mock { on { isEnabled() }.thenReturn(true) }
        whenever(mockOnboardingBrandDesignUpdateToggles.isAppReinstallMutex()).thenReturn(mockToggle)

        val result1 = testee.isAppReinstall()
        val result2 = testee.isAppReinstall()

        assertEquals("Second call should return cached result", result1, result2)
    }
}
