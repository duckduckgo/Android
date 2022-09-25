/*
 * Copyright (c) 2022 DuckDuckGo
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

import android.content.Context
import android.view.ContextMenu
import android.view.MenuItem
import android.webkit.WebView.HitTestResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.model.LongPressTarget
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.MockitoAnnotations

private const val HTTPS_IMAGE_URL = "https://example.com/1.img"
private const val DATA_URI_IMAGE_URL = "data:image/png;base64,iVB23="

@RunWith(AndroidJUnit4::class)
class WebViewLongPressHandlerTest {

    private lateinit var testee: WebViewLongPressHandler

    @Mock
    private lateinit var mockMenu: ContextMenu

    @Mock
    private lateinit var mockMenuItem: MenuItem

    @Mock
    private lateinit var mockPixel: Pixel

    @Mock
    private lateinit var context: Context

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        testee = WebViewLongPressHandler(context, mockPixel)
    }

    @Test
    fun whenUserLongPressesWithImageTypeThenPixelFired() {
        testee.handleLongPress(HitTestResult.IMAGE_TYPE, HTTPS_IMAGE_URL, mockMenu)
        verify(mockPixel).fire(AppPixelName.LONG_PRESS)
    }

    @Test
    fun whenUserLongPressesWithAnchorImageTypeThenPixelFired() {
        testee.handleLongPress(HitTestResult.SRC_IMAGE_ANCHOR_TYPE, HTTPS_IMAGE_URL, mockMenu)
        verify(mockPixel).fire(AppPixelName.LONG_PRESS)
    }

    @Test
    fun whenUserLongPressesWithUnknownTypeThenPixelNotFired() {
        testee.handleLongPress(HitTestResult.UNKNOWN_TYPE, HTTPS_IMAGE_URL, mockMenu)
        verify(mockPixel, never()).fire(AppPixelName.LONG_PRESS)
    }

    @Test
    fun whenUserLongPressesWithImageTypeThenUrlHeaderAddedToMenu() {
        testee.handleLongPress(HitTestResult.IMAGE_TYPE, HTTPS_IMAGE_URL, mockMenu)
        verify(mockMenu).setHeaderTitle(HTTPS_IMAGE_URL)
    }

    @Test
    fun whenUserLongPressesWithAnchorImageTypeThenUrlHeaderAddedToMenu() {
        testee.handleLongPress(HitTestResult.SRC_IMAGE_ANCHOR_TYPE, HTTPS_IMAGE_URL, mockMenu)
        verify(mockMenu).setHeaderTitle(HTTPS_IMAGE_URL)
    }

    @Test
    fun whenUserLongPressesWithAnchorImageTypeThenTabOptionsHeaderAddedToMenu() {
        testee.handleLongPress(HitTestResult.SRC_IMAGE_ANCHOR_TYPE, HTTPS_IMAGE_URL, mockMenu)
        verifyNewForegroundTabItemAdded()
    }

    @Test
    fun whenUserLongPressesWithAnchorImageTypeThenBgTabOptionsHeaderAddedToMenu() {
        testee.handleLongPress(HitTestResult.SRC_IMAGE_ANCHOR_TYPE, HTTPS_IMAGE_URL, mockMenu)
        verifyNewBackgroundTabItemAdded()
    }

    @Test
    fun whenUserLongPressesWithImageTypeThenDownloadImageMenuAdded() {
        testee.handleLongPress(HitTestResult.IMAGE_TYPE, HTTPS_IMAGE_URL, mockMenu)
        verifyDownloadImageItemAdded()
    }

    @Test
    fun whenUserLongPressesWithAnchorImageTypeThenDownloadImageMenuAdded() {
        testee.handleLongPress(HitTestResult.SRC_IMAGE_ANCHOR_TYPE, HTTPS_IMAGE_URL, mockMenu)
        verifyDownloadImageItemAdded()
    }

    @Test
    fun whenUserLongPressesWithOtherImageTypeThenMenuNotAltered() {
        testee.handleLongPress(HitTestResult.UNKNOWN_TYPE, HTTPS_IMAGE_URL, mockMenu)
        verifyMenuNotAltered()
    }

    @Test
    fun whenUserLongPressesWithImageTypeWhichIsADataUriThenDownloadImageMenuAdded() {
        testee.handleLongPress(HitTestResult.IMAGE_TYPE, DATA_URI_IMAGE_URL, mockMenu)
        verifyDownloadImageItemAdded()
    }

    @Test
    fun whenUserSelectsDownloadImageOptionThenActionIsDownloadFileActionRequired() {
        whenever(mockMenuItem.itemId).thenReturn(WebViewLongPressHandler.CONTEXT_MENU_ID_DOWNLOAD_IMAGE)
        val longPressTarget = LongPressTarget(url = "example.com", imageUrl = "example.com/foo.jpg", type = HitTestResult.SRC_ANCHOR_TYPE)
        val action = testee.userSelectedMenuItem(longPressTarget, mockMenuItem)
        assertTrue(action is LongPressHandler.RequiredAction.DownloadFile)
    }

    @Test
    fun whenUserSelectsDownloadImageOptionButNoImageUrlAvailableThenNoActionRequired() {
        whenever(mockMenuItem.itemId).thenReturn(WebViewLongPressHandler.CONTEXT_MENU_ID_DOWNLOAD_IMAGE)
        val longPressTarget = LongPressTarget(url = "example.com", imageUrl = null, type = HitTestResult.SRC_ANCHOR_TYPE)
        val action = testee.userSelectedMenuItem(longPressTarget, mockMenuItem)
        assertTrue(action is LongPressHandler.RequiredAction.None)
    }

    @Test
    fun whenUserSelectsDownloadImageOptionThenDownloadFileWithCorrectUrlReturned() {
        whenever(mockMenuItem.itemId).thenReturn(WebViewLongPressHandler.CONTEXT_MENU_ID_DOWNLOAD_IMAGE)
        val longPressTarget = LongPressTarget(url = "example.com", imageUrl = "example.com/foo.jpg", type = HitTestResult.SRC_ANCHOR_TYPE)
        val action = testee.userSelectedMenuItem(longPressTarget, mockMenuItem) as LongPressHandler.RequiredAction.DownloadFile
        assertEquals("example.com/foo.jpg", action.url)
    }

    @Test
    fun whenUserSelectsUnknownOptionThenNoActionRequiredReturned() {
        val unknownMenuId = 123
        whenever(mockMenuItem.itemId).thenReturn(unknownMenuId)
        val longPressTarget = LongPressTarget(url = "example.com", type = HitTestResult.SRC_ANCHOR_TYPE)
        val action = testee.userSelectedMenuItem(longPressTarget, mockMenuItem)
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
