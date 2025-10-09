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

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.duckchat.api.inputscreen.InputScreenActivityParams
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
    private val mockActivityContext: Context = mock()
    private val mockResources: Resources = mock()
    private val configuration = Configuration()

    private lateinit var inputScreenConfigResolver: InputScreenConfigResolverImpl

    @Before
    fun setup() {
        whenever(duckChatInternal.inputScreenBottomBarEnabled).thenReturn(inputScreenBottomBarEnabled)
        whenever(mockActivityContext.resources).thenReturn(mockResources)
        whenever(mockResources.configuration).thenReturn(configuration)
        configuration.orientation = Configuration.ORIENTATION_PORTRAIT
        inputScreenConfigResolver = InputScreenConfigResolverImpl(duckChatInternal, mockActivityContext)
    }

    @Test
    fun `when initialized then isTopOmnibar should default to true`() {
        assertTrue(inputScreenConfigResolver.isTopOmnibar)
    }

    @Test
    fun `when onInputScreenCreated called with null params then isTopOmnibar should remain true`() {
        val intent = Intent()

        inputScreenConfigResolver.onInputScreenCreated(intent)

        assertTrue(inputScreenConfigResolver.isTopOmnibar)
    }

    @Test
    fun `when onInputScreenCreated called with isTopOmnibar true then isTopOmnibar should be true`() {
        val intent =
            Intent().apply {
                putExtra("ACTIVITY_SERIALIZABLE_PARAMETERS_ARG", InputScreenActivityParams(query = "", isTopOmnibar = true, tabs = 1))
            }

        inputScreenConfigResolver.onInputScreenCreated(intent)

        assertTrue(inputScreenConfigResolver.isTopOmnibar)
    }

    @Test
    fun `when onInputScreenCreated called with isTopOmnibar false then isTopOmnibar should be false`() {
        val intent =
            Intent().apply {
                putExtra("ACTIVITY_SERIALIZABLE_PARAMETERS_ARG", InputScreenActivityParams(query = "", isTopOmnibar = false, tabs = 1))
            }

        inputScreenConfigResolver.onInputScreenCreated(intent)

        assertFalse(inputScreenConfigResolver.isTopOmnibar)
    }

    @Test
    fun `when isTopOmnibar is true then useTopBar should return true regardless of bottom bar feature`() {
        val intent =
            Intent().apply {
                putExtra("ACTIVITY_SERIALIZABLE_PARAMETERS_ARG", InputScreenActivityParams(query = "", isTopOmnibar = true, tabs = 1))
            }
        inputScreenConfigResolver.onInputScreenCreated(intent)

        inputScreenBottomBarEnabled.value = false
        assertTrue(inputScreenConfigResolver.useTopBar())

        inputScreenBottomBarEnabled.value = true
        assertTrue(inputScreenConfigResolver.useTopBar())
    }

    @Test
    fun `when isTopOmnibar is false and bottom bar feature disabled then useTopBar should return true`() {
        val intent =
            Intent().apply {
                putExtra("ACTIVITY_SERIALIZABLE_PARAMETERS_ARG", InputScreenActivityParams(query = "", isTopOmnibar = false, tabs = 1))
            }
        inputScreenConfigResolver.onInputScreenCreated(intent)
        inputScreenBottomBarEnabled.value = false

        assertTrue(inputScreenConfigResolver.useTopBar())
    }

    @Test
    fun `when isTopOmnibar is false and bottom bar feature enabled then useTopBar should return false`() {
        val intent =
            Intent().apply {
                putExtra("ACTIVITY_SERIALIZABLE_PARAMETERS_ARG", InputScreenActivityParams(query = "", isTopOmnibar = false, tabs = 1))
            }
        inputScreenConfigResolver.onInputScreenCreated(intent)
        inputScreenBottomBarEnabled.value = true

        assertFalse(inputScreenConfigResolver.useTopBar())
    }

    @Test
    fun `when landscape and isTopOmnibar true and bottom bar enabled then useTopBar returns true`() {
        configuration.orientation = Configuration.ORIENTATION_LANDSCAPE

        val intent = Intent().apply {
            putExtra(
                "ACTIVITY_SERIALIZABLE_PARAMETERS_ARG",
                InputScreenActivityParams(query = "", isTopOmnibar = true, tabs = 1),
            )
        }
        inputScreenConfigResolver.onInputScreenCreated(intent)
        inputScreenBottomBarEnabled.value = true

        assertTrue(inputScreenConfigResolver.useTopBar())
    }

    @Test
    fun `when landscape and isTopOmnibar true and bottom bar disabled then useTopBar returns true`() {
        configuration.orientation = Configuration.ORIENTATION_LANDSCAPE

        val intent = Intent().apply {
            putExtra(
                "ACTIVITY_SERIALIZABLE_PARAMETERS_ARG",
                InputScreenActivityParams(query = "", isTopOmnibar = true, tabs = 1),
            )
        }
        inputScreenConfigResolver.onInputScreenCreated(intent)
        inputScreenBottomBarEnabled.value = false

        assertTrue(inputScreenConfigResolver.useTopBar())
    }

    @Test
    fun `when landscape and isTopOmnibar false and bottom bar enabled then useTopBar returns true`() {
        configuration.orientation = Configuration.ORIENTATION_LANDSCAPE

        val intent = Intent().apply {
            putExtra(
                "ACTIVITY_SERIALIZABLE_PARAMETERS_ARG",
                InputScreenActivityParams(query = "", isTopOmnibar = false, tabs = 1),
            )
        }
        inputScreenConfigResolver.onInputScreenCreated(intent)
        inputScreenBottomBarEnabled.value = true

        assertTrue(inputScreenConfigResolver.useTopBar())
    }

    @Test
    fun `when landscape and isTopOmnibar false and bottom bar disabled then useTopBar returns true`() {
        configuration.orientation = Configuration.ORIENTATION_LANDSCAPE

        val intent = Intent().apply {
            putExtra(
                "ACTIVITY_SERIALIZABLE_PARAMETERS_ARG",
                InputScreenActivityParams(query = "", isTopOmnibar = false, tabs = 1),
            )
        }
        inputScreenConfigResolver.onInputScreenCreated(intent)
        inputScreenBottomBarEnabled.value = false

        assertTrue(inputScreenConfigResolver.useTopBar())
    }
}
