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

package com.duckduckgo.duckchat.impl.inputscreen.ui

import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import androidx.appcompat.app.AppCompatActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.duckchat.api.inputscreen.InputScreenActivityParams
import com.duckduckgo.duckchat.api.inputscreen.InputScreenBrowserButtonsConfig
import com.duckduckgo.duckchat.impl.DuckChatInternal
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class InputScreenConfigResolverTest {
    private val duckChatInternal: DuckChatInternal = mock()
    private val inputScreenBottomBarEnabled = MutableStateFlow(false)
    private val showMainButtonsInInputScreen = MutableStateFlow(true)
    private val mockAppCompatActivity: AppCompatActivity = mock()
    private val mockResources: Resources = mock()
    private val configuration = Configuration()

    private lateinit var inputScreenConfigResolver: InputScreenConfigResolverImpl

    @Before
    fun setup() {
        whenever(duckChatInternal.inputScreenBottomBarEnabled).thenReturn(inputScreenBottomBarEnabled)
        whenever(duckChatInternal.showMainButtonsInInputScreen).thenReturn(showMainButtonsInInputScreen)
        whenever(mockAppCompatActivity.resources).thenReturn(mockResources)
        whenever(mockResources.configuration).thenReturn(configuration)
        configuration.orientation = Configuration.ORIENTATION_PORTRAIT
    }

    @Test
    fun `when intent has null params then isTopOmnibar should default to true`() {
        val intent = Intent()
        whenever(mockAppCompatActivity.intent).thenReturn(intent)
        inputScreenConfigResolver = InputScreenConfigResolverImpl(duckChatInternal, mockAppCompatActivity)

        assertTrue(inputScreenConfigResolver.isTopOmnibar)
    }

    @Test
    fun `when intent has isTopOmnibar true then isTopOmnibar should be true`() {
        val intent = createIntent(
            isTopOmnibar = true,
        )
        whenever(mockAppCompatActivity.intent).thenReturn(intent)
        inputScreenConfigResolver = InputScreenConfigResolverImpl(duckChatInternal, mockAppCompatActivity)

        assertTrue(inputScreenConfigResolver.isTopOmnibar)
    }

    @Test
    fun `when intent has isTopOmnibar false then isTopOmnibar should be false`() {
        val intent = createIntent(
            isTopOmnibar = false,
        )
        whenever(mockAppCompatActivity.intent).thenReturn(intent)
        inputScreenConfigResolver = InputScreenConfigResolverImpl(duckChatInternal, mockAppCompatActivity)

        assertFalse(inputScreenConfigResolver.isTopOmnibar)
    }

    @Test
    fun `when isTopOmnibar is true then useTopBar should return true regardless of bottom bar feature`() {
        val intent = createIntent(
            isTopOmnibar = true,
        )
        whenever(mockAppCompatActivity.intent).thenReturn(intent)
        inputScreenConfigResolver = InputScreenConfigResolverImpl(duckChatInternal, mockAppCompatActivity)

        inputScreenBottomBarEnabled.value = false
        assertTrue(inputScreenConfigResolver.useTopBar())

        inputScreenBottomBarEnabled.value = true
        assertTrue(inputScreenConfigResolver.useTopBar())
    }

    @Test
    fun `when isTopOmnibar is false and bottom bar feature disabled then useTopBar should return true`() {
        val intent = createIntent(
            isTopOmnibar = false,
        )
        whenever(mockAppCompatActivity.intent).thenReturn(intent)
        inputScreenConfigResolver = InputScreenConfigResolverImpl(duckChatInternal, mockAppCompatActivity)
        inputScreenBottomBarEnabled.value = false

        assertTrue(inputScreenConfigResolver.useTopBar())
    }

    @Test
    fun `when isTopOmnibar is false and bottom bar feature enabled then useTopBar should return false`() {
        val intent = createIntent(
            isTopOmnibar = false,
        )
        whenever(mockAppCompatActivity.intent).thenReturn(intent)
        inputScreenConfigResolver = InputScreenConfigResolverImpl(duckChatInternal, mockAppCompatActivity)
        inputScreenBottomBarEnabled.value = true

        assertFalse(inputScreenConfigResolver.useTopBar())
    }

    @Test
    fun `when landscape and isTopOmnibar true and bottom bar enabled then useTopBar returns true`() {
        configuration.orientation = Configuration.ORIENTATION_LANDSCAPE

        val intent = createIntent(
            isTopOmnibar = true,
        )
        whenever(mockAppCompatActivity.intent).thenReturn(intent)
        inputScreenConfigResolver = InputScreenConfigResolverImpl(duckChatInternal, mockAppCompatActivity)
        inputScreenBottomBarEnabled.value = true

        assertTrue(inputScreenConfigResolver.useTopBar())
    }

    @Test
    fun `when landscape and isTopOmnibar true and bottom bar disabled then useTopBar returns true`() {
        configuration.orientation = Configuration.ORIENTATION_LANDSCAPE

        val intent = createIntent(
            isTopOmnibar = true,
        )
        whenever(mockAppCompatActivity.intent).thenReturn(intent)
        inputScreenConfigResolver = InputScreenConfigResolverImpl(duckChatInternal, mockAppCompatActivity)
        inputScreenBottomBarEnabled.value = false

        assertTrue(inputScreenConfigResolver.useTopBar())
    }

    @Test
    fun `when landscape and isTopOmnibar false and bottom bar enabled then useTopBar returns true`() {
        configuration.orientation = Configuration.ORIENTATION_LANDSCAPE

        val intent = createIntent(
            isTopOmnibar = false,
        )
        whenever(mockAppCompatActivity.intent).thenReturn(intent)
        inputScreenConfigResolver = InputScreenConfigResolverImpl(duckChatInternal, mockAppCompatActivity)
        inputScreenBottomBarEnabled.value = true

        assertTrue(inputScreenConfigResolver.useTopBar())
    }

    @Test
    fun `when landscape and isTopOmnibar false and bottom bar disabled then useTopBar returns true`() {
        configuration.orientation = Configuration.ORIENTATION_LANDSCAPE

        val intent = createIntent(
            isTopOmnibar = false,
        )
        whenever(mockAppCompatActivity.intent).thenReturn(intent)
        inputScreenConfigResolver = InputScreenConfigResolverImpl(duckChatInternal, mockAppCompatActivity)
        inputScreenBottomBarEnabled.value = false

        assertTrue(inputScreenConfigResolver.useTopBar())
    }

    @Test
    fun `when showMainButtonsInInputScreen enabled and browserButtonsConfig is Enabled then mainButtonsEnabled returns true`() {
        val intent = createIntent(
            isTopOmnibar = true,
            browserButtonsConfig = InputScreenBrowserButtonsConfig.Enabled(tabs = 1),
        )
        whenever(mockAppCompatActivity.intent).thenReturn(intent)
        showMainButtonsInInputScreen.value = true
        inputScreenConfigResolver = InputScreenConfigResolverImpl(duckChatInternal, mockAppCompatActivity)

        assertTrue(inputScreenConfigResolver.mainButtonsEnabled())
    }

    @Test
    fun `when showMainButtonsInInputScreen enabled and browserButtonsConfig is Disabled then mainButtonsEnabled returns false`() {
        val intent = createIntent(
            isTopOmnibar = true,
            browserButtonsConfig = InputScreenBrowserButtonsConfig.Disabled(),
        )
        whenever(mockAppCompatActivity.intent).thenReturn(intent)
        showMainButtonsInInputScreen.value = true
        inputScreenConfigResolver = InputScreenConfigResolverImpl(duckChatInternal, mockAppCompatActivity)

        assertFalse(inputScreenConfigResolver.mainButtonsEnabled())
    }

    @Test
    fun `when showMainButtonsInInputScreen disabled and browserButtonsConfig is Enabled then mainButtonsEnabled returns false`() {
        val intent = createIntent(
            isTopOmnibar = true,
            browserButtonsConfig = InputScreenBrowserButtonsConfig.Enabled(tabs = 1),
        )
        whenever(mockAppCompatActivity.intent).thenReturn(intent)
        showMainButtonsInInputScreen.value = false
        inputScreenConfigResolver = InputScreenConfigResolverImpl(duckChatInternal, mockAppCompatActivity)

        assertFalse(inputScreenConfigResolver.mainButtonsEnabled())
    }

    @Test
    fun `when showMainButtonsInInputScreen disabled and browserButtonsConfig is Disabled then mainButtonsEnabled returns false`() {
        val intent = createIntent(
            isTopOmnibar = true,
            browserButtonsConfig = InputScreenBrowserButtonsConfig.Disabled(),
        )
        whenever(mockAppCompatActivity.intent).thenReturn(intent)
        showMainButtonsInInputScreen.value = false
        inputScreenConfigResolver = InputScreenConfigResolverImpl(duckChatInternal, mockAppCompatActivity)

        assertFalse(inputScreenConfigResolver.mainButtonsEnabled())
    }

    @Test
    fun `when shouldShowInstalledApps called with showInstalledApps true then returns true`() {
        val intent = createIntent(showInstalledApps = true)
        whenever(mockAppCompatActivity.intent).thenReturn(intent)
        inputScreenConfigResolver = InputScreenConfigResolverImpl(duckChatInternal, mockAppCompatActivity)

        assertTrue(inputScreenConfigResolver.shouldShowInstalledApps())
    }

    @Test
    fun `when shouldShowInstalledApps called with showInstalledApps false then returns false`() {
        val intent = createIntent(showInstalledApps = false)
        whenever(mockAppCompatActivity.intent).thenReturn(intent)
        inputScreenConfigResolver = InputScreenConfigResolverImpl(duckChatInternal, mockAppCompatActivity)

        assertFalse(inputScreenConfigResolver.shouldShowInstalledApps())
    }

    private fun createIntent(
        query: String = "",
        isTopOmnibar: Boolean = true,
        browserButtonsConfig: InputScreenBrowserButtonsConfig = InputScreenBrowserButtonsConfig.Disabled(),
        showInstalledApps: Boolean = false,
    ) = Intent().apply {
        putExtra(
            "ACTIVITY_SERIALIZABLE_PARAMETERS_ARG",
            InputScreenActivityParams(
                query = query,
                isTopOmnibar = isTopOmnibar,
                browserButtonsConfig = browserButtonsConfig,
                showInstalledApps = showInstalledApps,
            ),
        )
    }
}
