package com.duckduckgo.duckchat.impl.ui

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

class NativeInputStateTest {

    @Test
    fun whenZeroThenInputModeIsSearchOnly() {
        assertEquals(NativeInputState.InputMode.SEARCH_ONLY, NativeInputState.zero().inputMode)
    }

    @Test
    fun whenZeroThenInputContextIsBrowser() {
        assertEquals(NativeInputState.InputContext.BROWSER, NativeInputState.zero().inputContext)
    }

    @Test
    fun whenZeroThenInputPositionIsTop() {
        assertEquals(NativeInputState.InputPosition.TOP, NativeInputState.zero().inputPosition)
    }

    @Test
    fun whenZeroThenSelectedModelIdIsNull() {
        assertNull(NativeInputState.zero().selectedModelId)
    }

    @Test
    fun whenZeroThenAttachedImagesIsEmpty() {
        assertTrue(NativeInputState.zero().attachedImages.isEmpty())
    }

    @Test
    fun whenCopyingWithSelectedModelIdThenValueIsPreserved() {
        val state = NativeInputState.zero().copy(selectedModelId = "claude-3")
        assertEquals("claude-3", state.selectedModelId)
    }

    @Test
    fun whenCopyingWithAttachedImagesThenValueIsPreserved() {
        val uri: Uri = mock()
        val state = NativeInputState.zero().copy(attachedImages = listOf(uri))
        assertEquals(listOf(uri), state.attachedImages)
    }

    @Test
    fun whenToggleVisibleAndSearchOnlyModeThenToggleNotVisible() {
        val state = NativeInputState.zero() // SEARCH_ONLY
        assertTrue(!state.toggleVisible)
    }
}
