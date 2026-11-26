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

package com.duckduckgo.duckchat.impl.ui.nativeinput.views

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.isVisible
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.common.utils.plugins.view_model.ViewModelFactoryPlugin
import com.duckduckgo.common.utils.plugins.view_model.ViewViewModelFactoryPluginPoint
import com.duckduckgo.di.android.AndroidInjector
import com.duckduckgo.di.android.HasInjector
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState.InputContext
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState.ToggleSelection
import com.duckduckgo.duckchat.api.nativeinput.NativeInputStateProvider
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.models.Tool
import com.duckduckgo.duckchat.impl.nativeinput.NativeInputHost
import com.duckduckgo.duckchat.impl.nativeinput.NewChatHintCommands
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class OptionsViewTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val activityController = Robolectric.buildActivity(OptionsTestActivity::class.java).setup()
    private val activity = activityController.get()
    private val inputState = MutableStateFlow(
        NativeInputState.zero().copy(toggleSelection = ToggleSelection.DUCK_AI, inputContext = InputContext.DUCK_AI),
    )
    private val host: NativeInputHost = mock()
    private val isMergedMenu = MutableStateFlow(false)
    private val selectedTool = MutableStateFlow<Tool?>(null)
    private val visibleTools = MutableStateFlow(Tool.entries.toSet())
    private val viewModel: OptionsViewModel = mock<OptionsViewModel>().also {
        whenever(it.isMergedMenu).thenReturn(isMergedMenu)
        whenever(it.selectedTool).thenReturn(selectedTool)
        whenever(it.visibleTools).thenReturn(visibleTools)
        whenever(it.toolSelectionCleared).thenReturn(emptyFlow())
        whenever(it.shouldShowPickers).thenReturn(true)
    }
    private lateinit var view: OptionsView

    @After
    fun tearDown() {
        if (::view.isInitialized) shadowOf(view).callOnDetachedFromWindow()
        activityController.pause().stop().destroy()
    }

    @Test
    fun whenMergedMenuOffThenToolsButtonOpensMenuWithCustomizeResponses() {
        attach()

        assertTrue(toolsButton().isVisible)
        toolsButton().performClick()
        assertTrue(latestMenu().hasText(activity.getString(R.string.duckChatOptionsMenuCustomizeResponses)))
    }

    @Test
    fun whenMergedMenuOnThenToolsButtonAndItsCustomizeResponsesRowAreGone() {
        isMergedMenu.value = true
        attach()

        assertFalse(toolsButton().isVisible)
    }

    @Test
    fun whenFlagTurnsOnAfterAttachThenToolsButtonHides() {
        attach()
        assertTrue(toolsButton().isVisible)

        isMergedMenu.value = true

        assertFalse(toolsButton().isVisible)
    }

    @Test
    fun whenMergedMenuOffAtTopCompactLevelThenChipOpensToolsMenuInsteadOfTurningToolOff() {
        selectedTool.value = Tool.WEB_SEARCH
        attach()
        view.setCompactLevel(AdaptiveBottomRowLayout.LEVEL_MERGED_TOOLS)

        assertFalse(chip().findViewById<View>(R.id.optionsChipClose).isVisible)
        chip().performClick()

        assertTrue(latestMenu().hasText(activity.getString(R.string.duckChatOptionsMenuWebSearch)))
        verify(host, never()).toolSelected(null)
    }

    @Test
    fun whenMergedMenuOnAtTopCompactLevelThenChipKeepsCloseIconAndTurnsToolOff() {
        isMergedMenu.value = true
        selectedTool.value = Tool.WEB_SEARCH
        attach()
        view.setCompactLevel(AdaptiveBottomRowLayout.LEVEL_MERGED_TOOLS)

        assertTrue(chip().findViewById<View>(R.id.optionsChipClose).isVisible)
        chip().performClick()

        verify(viewModel).onToolDeselectedByUser(Tool.WEB_SEARCH)
        verify(host).toolSelected(null)
        assertNull(shadowOf(activity.application).latestPopupWindow)
    }

    @Test
    fun whenMergedMenuOnThenReservedWidthIsOnlyTheChip() {
        isMergedMenu.value = true
        attach()
        assertEquals(0, view.worstCaseWidth(AdaptiveBottomRowLayout.LEVEL_FULL))

        selectedTool.value = Tool.IMAGE_GENERATION
        val button = activity.resources.getDimensionPixelSize(R.dimen.nativeInputButtonSize)
        isMergedMenu.value = false
        val withButton = view.worstCaseWidth(AdaptiveBottomRowLayout.LEVEL_FULL)
        isMergedMenu.value = true

        assertEquals(withButton - button, view.worstCaseWidth(AdaptiveBottomRowLayout.LEVEL_FULL))
        assertEquals(withButton - button, view.worstCaseWidth(AdaptiveBottomRowLayout.LEVEL_MERGED_TOOLS))
    }

    private fun toolsButton(): View = view.getChildAt(0)

    private fun chip(): View = view.getChildAt(1)

    private fun latestMenu(): ViewGroup = shadowOf(activity.application).latestPopupWindow!!.contentView as ViewGroup

    private fun View.hasText(text: String): Boolean = when (this) {
        is TextView -> this.text == text
        is ViewGroup -> (0 until childCount).any { getChildAt(it).hasText(text) }
        else -> false
    }

    private fun attach() {
        val provider: NativeInputStateProvider = mock()
        whenever(provider.state).thenReturn(inputState)
        val plugin: ViewModelFactoryPlugin = mock()
        whenever(plugin.create(any<Class<OptionsViewModel>>())).thenReturn(viewModel)
        val factory = ViewViewModelFactory(ViewViewModelFactoryPluginPoint(setOf(plugin)))
        val root = FrameLayout(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
        }
        view = OptionsView(activity, host).apply {
            viewModelFactory = factory
            nativeInputStateProvider = provider
            newChatHintCommands = NewChatHintCommands()
        }
        root.addView(FrameLayout(activity).apply { addView(view) })
        shadowOf(view).callOnAttachedToWindow()
    }

    class OptionsTestActivity : ComponentActivity(), HasInjector {
        override fun onCreate(savedInstanceState: android.os.Bundle?) {
            setTheme(com.duckduckgo.mobile.android.R.style.Theme_DuckDuckGo_Light)
            super.onCreate(savedInstanceState)
        }

        override fun injectorFactoryFor(key: Class<*>): AndroidInjector.Factory<*, *> =
            object : AndroidInjector.Factory<OptionsView, OptionsInjector> {
                override fun create(instance: OptionsView) = OptionsInjector()
            }
    }

    class OptionsInjector : AndroidInjector<OptionsView> {
        override fun inject(instance: OptionsView) = Unit
    }
}
