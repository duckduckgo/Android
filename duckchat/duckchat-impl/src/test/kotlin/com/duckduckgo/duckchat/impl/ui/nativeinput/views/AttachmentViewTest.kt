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

import android.net.Uri
import android.widget.FrameLayout
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
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState.ToggleSelection
import com.duckduckgo.duckchat.api.nativeinput.NativeInputStateProvider
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.nativeinput.NativeInputHost
import com.duckduckgo.duckchat.impl.ui.AttachmentViewModel
import com.duckduckgo.duckchat.impl.ui.AttachmentViewModel.AttachmentState
import com.duckduckgo.duckchat.impl.ui.nativeinput.file.FileAttachment
import com.duckduckgo.duckchat.impl.ui.nativeinput.file.FileAttachmentsContainerView
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class AttachmentViewTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val activityController = Robolectric.buildActivity(AttachmentTestActivity::class.java).setup()
    private val activity = activityController.get()
    private val inputState = MutableStateFlow(NativeInputState.zero().copy(toggleSelection = ToggleSelection.DUCK_AI))
    private val editState = MutableStateFlow(inputState.value)
    private val attachments = MutableStateFlow(AttachmentState())
    private val host: NativeInputHost = mock()
    private val viewModel: AttachmentViewModel = mock()
    private val container = FrameLayout(activity).apply { id = R.id.attachmentsContainer }
    private lateinit var view: AttachmentView

    @After
    fun tearDown() {
        if (::view.isInitialized) shadowOf(view).callOnDetachedFromWindow()
        activityController.pause().stop().destroy()
    }

    @Test
    fun whenSwitchingToSearchThenFilesHideAndReturnWithoutChangingDraftOrNotifyingHost() {
        val draft = AttachmentState(files = listOf(file()))
        attachments.value = draft
        attach()
        assertTrue(container.isVisible)
        val files = container.findFileAttachments()
        val chip = files.getChildAt(0)
        clearInvocations(host, viewModel)

        inputState.value = inputState.value.copy(toggleSelection = ToggleSelection.SEARCH)
        assertFalse(container.isVisible)

        inputState.value = inputState.value.copy(toggleSelection = ToggleSelection.DUCK_AI)
        assertTrue(container.isVisible)
        assertSame(draft, attachments.value)
        assertSame(chip, files.getChildAt(0))
        assertEquals(draft.files, files.getAttachments())
        verify(host, never()).attachmentChanged(any(), any(), any())
        verifyNoInteractions(viewModel)
    }

    @Test
    fun whenFileReadCompletesInSearchThenItStaysHiddenUntilDuckAiIsSelected() {
        attach()
        inputState.value = inputState.value.copy(toggleSelection = ToggleSelection.SEARCH)

        attachments.value = AttachmentState(files = listOf(file()))
        assertFalse(container.isVisible)

        inputState.value = inputState.value.copy(toggleSelection = ToggleSelection.DUCK_AI)
        assertTrue(container.isVisible)
        assertEquals(attachments.value.files, container.findFileAttachments().getAttachments())
    }

    @Test
    fun whenErrorArrivesInSearchThenItStaysHiddenAndReturnsOnlyInDuckAi() {
        attach()
        inputState.value = inputState.value.copy(toggleSelection = ToggleSelection.SEARCH)
        attachments.value = AttachmentState(fileSizeError = "File too large")
        assertFalse(container.isVisible)

        inputState.value = inputState.value.copy(toggleSelection = ToggleSelection.DUCK_AI)
        assertTrue(container.isVisible)

        inputState.value = inputState.value.copy(toggleSelection = ToggleSelection.SEARCH)
        assertFalse(container.isVisible)
    }

    @Test
    fun whenStreamingChangesThenAttachmentsHideAndReturnWithoutAttachmentUpdates() {
        attachments.value = AttachmentState(files = listOf(file()))
        attach()
        inputState.value = inputState.value.copy(isChatStreaming = true)
        assertFalse(container.isVisible)

        inputState.value = inputState.value.copy(isChatStreaming = false)
        assertTrue(container.isVisible)
    }

    @Test
    fun whenEditingThenBrowserSearchAndStreamingDoNotHideAttachments() {
        whenever(host.isEditSurface()).thenReturn(true)
        whenever(host.tabId()).thenReturn("edit:session")
        inputState.value = inputState.value.copy(toggleSelection = ToggleSelection.SEARCH, isChatStreaming = true)
        attachments.value = AttachmentState(files = listOf(file()))
        attach(isEdit = true)
        assertTrue(container.isVisible)

        editState.value = editState.value.copy(isChatStreaming = true)
        assertFalse(container.isVisible)
        editState.value = editState.value.copy(isChatStreaming = false)
        assertTrue(container.isVisible)
    }

    @Test
    fun whenContextualHostSharesBrowserSearchStateThenAttachmentsStayVisible() {
        whenever(host.isContextualSurface()).thenReturn(true)
        attachments.value = AttachmentState(files = listOf(file()))
        attach()

        inputState.value = inputState.value.copy(toggleSelection = ToggleSelection.SEARCH)
        assertTrue(container.isVisible)
    }

    private fun attach(isEdit: Boolean = false) {
        val provider: NativeInputStateProvider = mock()
        whenever(provider.state).thenReturn(inputState)
        whenever(provider.stateForTab("edit:session")).thenReturn(editState)
        whenever(viewModel.attachmentState).thenReturn(attachments)
        val plugin: ViewModelFactoryPlugin = mock()
        whenever(plugin.create(any<Class<AttachmentViewModel>>())).thenReturn(viewModel)
        val factory = ViewViewModelFactory(ViewViewModelFactoryPluginPoint(setOf(plugin)))
        val root = FrameLayout(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            addView(container)
        }
        view = AttachmentView(activity).apply {
            this.host = this@AttachmentViewTest.host
            isEditMode = isEdit
            viewModelFactory = factory
            nativeInputStateProvider = provider
            faviconManager = mock()
        }
        root.addView(FrameLayout(activity).apply { addView(view) })
        shadowOf(view).callOnAttachedToWindow()
    }

    private fun file() = FileAttachment("file", Uri.EMPTY, "draft.pdf", "application/pdf", 100, "data")

    private fun android.view.ViewGroup.findFileAttachments(): FileAttachmentsContainerView {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child is FileAttachmentsContainerView) return child
            if (child is android.view.ViewGroup) {
                runCatching { child.findFileAttachments() }.getOrNull()?.let { return it }
            }
        }
        error("File attachment container not found")
    }

    class AttachmentTestActivity : ComponentActivity(), HasInjector {
        override fun onCreate(savedInstanceState: android.os.Bundle?) {
            setTheme(com.duckduckgo.mobile.android.R.style.Theme_DuckDuckGo_Light)
            super.onCreate(savedInstanceState)
        }

        override fun injectorFactoryFor(key: Class<*>): AndroidInjector.Factory<*, *> =
            object : AndroidInjector.Factory<AttachmentView, AttachmentInjector> {
                override fun create(instance: AttachmentView) = AttachmentInjector()
            }
    }

    class AttachmentInjector : AndroidInjector<AttachmentView> {
        override fun inject(instance: AttachmentView) = Unit
    }
}
