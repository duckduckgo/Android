/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.browser

import android.view.ContextMenu
import android.view.MenuItem
import android.webkit.WebView.HitTestResult
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.statistics.pixels.Pixel
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.MockitoAnnotations

private const val HTTPS_IMAGE_URL = "https://example.com/1.img"
private const val DATA_URI_IMAGE_URL = "data:image/png;base64,iVB23="

class WebViewLongPressHandlerTest {

    private lateinit var testee: WebViewLongPressHandler

    @Mock
    private lateinit var mockMenu: ContextMenu

    @Mock
    private lateinit var mockMenuItem: MenuItem

    @Mock
    private lateinit var mockPixel: Pixel

    private var context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        testee = WebViewLongPressHandler(context, mockPixel)
    }

    @Test
    fun whenLongPressedWithImageTypeThenPixelFired() {
        testee.handleLongPress(HitTestResult.IMAGE_TYPE, HTTPS_IMAGE_URL, mockMenu)
        verify(mockPixel).fire(Pixel.PixelName.LONG_PRESS)
    }

    @Test
    fun whenLongPressedWithAnchorImageTypeThenPixelFired() {
        testee.handleLongPress(HitTestResult.SRC_IMAGE_ANCHOR_TYPE, HTTPS_IMAGE_URL, mockMenu)
        verify(mockPixel).fire(Pixel.PixelName.LONG_PRESS)
    }

    @Test
    fun whenLongPressedWithUnknownTypeThenPixelNotFired() {
        testee.handleLongPress(HitTestResult.UNKNOWN_TYPE, HTTPS_IMAGE_URL, mockMenu)
        verify(mockPixel, never()).fire(Pixel.PixelName.LONG_PRESS)
    }

    @Test
    fun whenLongPressedWithImageTypeThenUrlHeaderAddedToMenu() {
        testee.handleLongPress(HitTestResult.IMAGE_TYPE, HTTPS_IMAGE_URL, mockMenu)
        verify(mockMenu).setHeaderTitle(HTTPS_IMAGE_URL)
    }

    @Test
    fun whenLongPressedWithAnchorImageTypeThenUrlHeaderAddedToMenu() {
        testee.handleLongPress(HitTestResult.SRC_IMAGE_ANCHOR_TYPE, HTTPS_IMAGE_URL, mockMenu)
        verify(mockMenu).setHeaderTitle(HTTPS_IMAGE_URL)
    }

    @Test
    fun whenLongPressedWithAnchorImageTypeThenTabOptionsHeaderAddedToMenu() {
        testee.handleLongPress(HitTestResult.SRC_IMAGE_ANCHOR_TYPE, HTTPS_IMAGE_URL, mockMenu)
        verifyNewForegroundTabItemAdded()
    }

    @Test
    fun whenLongPressedWithAnchorImageTypeThenBgTabOptionsHeaderAddedToMenu() {
        testee.handleLongPress(HitTestResult.SRC_IMAGE_ANCHOR_TYPE, HTTPS_IMAGE_URL, mockMenu)
        verifyNewBackgroundTabItemAdded()
    }

    @Test
    fun whenLongPressedWithImageTypeThenDownloadImageMenuAdded() {
        testee.handleLongPress(HitTestResult.IMAGE_TYPE, HTTPS_IMAGE_URL, mockMenu)
        verifyDownloadImageItemAdded()
    }

    @Test
    fun whenLongPressedWithAnchorImageTypeThenDownloadImageMenuAdded() {
        testee.handleLongPress(HitTestResult.SRC_IMAGE_ANCHOR_TYPE, HTTPS_IMAGE_URL, mockMenu)
        verifyDownloadImageItemAdded()
    }

    @Test
    fun whenLongPressedWithOtherImageTypeThenMenuNotAltered() {
        testee.handleLongPress(HitTestResult.UNKNOWN_TYPE, HTTPS_IMAGE_URL, mockMenu)
        verifyMenuNotAltered()
    }

    @Test
    fun whenLongPressedWithImageTypeWhichIsADataUriThenDownloadImageMenuAdded() {
        testee.handleLongPress(HitTestResult.IMAGE_TYPE, DATA_URI_IMAGE_URL, mockMenu)
        verifyDownloadImageItemAdded()
    }

    @Test
    fun whenUserSelectedDownloadImageOptionThenActionIsDownloadFileActionRequired() {
        whenever(mockMenuItem.itemId).thenReturn(WebViewLongPressHandler.CONTEXT_MENU_ID_DOWNLOAD_IMAGE)
        val action = testee.userSelectedMenuItem("example.com", mockMenuItem)
        assertTrue(action is LongPressHandler.RequiredAction.DownloadFile)
    }

    @Test
    fun whenUserSelectedDownloadImageOptionThenDownloadFileWithCorrectUrlReturned() {
        whenever(mockMenuItem.itemId).thenReturn(WebViewLongPressHandler.CONTEXT_MENU_ID_DOWNLOAD_IMAGE)
        val action = testee.userSelectedMenuItem("example.com", mockMenuItem) as LongPressHandler.RequiredAction.DownloadFile
        assertEquals("example.com", action.url)
    }

    @Test
    fun whenUserSelectedUnknownOptionThenNoActionRequiredReturned() {
        val unknownMenuId = 123
        whenever(mockMenuItem.itemId).thenReturn(unknownMenuId)
        val action = testee.userSelectedMenuItem("example.com", mockMenuItem)
        assertTrue(action == LongPressHandler.RequiredAction.None)
    }

    private fun verifyDownloadImageItemAdded() {
        verify(mockMenu).add(anyInt(), eq(WebViewLongPressHandler.CONTEXT_MENU_ID_DOWNLOAD_IMAGE), anyInt(), eq(R.string.downloadImage))
    }

    private fun verifyNewForegroundTabItemAdded() {
        verify(mockMenu).add(anyInt(), eq(WebViewLongPressHandler.CONTEXT_MENU_ID_OPEN_IN_NEW_TAB), anyInt(), eq(R.string.openInNewTab))
    }

    private fun verifyNewBackgroundTabItemAdded() {
        verify(mockMenu).add(
            anyInt(),
            eq(WebViewLongPressHandler.CONTEXT_MENU_ID_OPEN_IN_NEW_BACKGROUND_TAB),
            anyInt(),
            eq(R.string.openInNewBackgroundTab)
        )
    }

    private fun verifyMenuNotAltered() {
        verify(mockMenu, never()).add(anyInt())
        verify(mockMenu, never()).add(anyString())
        verify(mockMenu, never()).add(anyInt(), anyInt(), anyInt(), anyInt())
        verify(mockMenu, never()).add(anyInt(), anyInt(), anyInt(), anyString())
    }
}