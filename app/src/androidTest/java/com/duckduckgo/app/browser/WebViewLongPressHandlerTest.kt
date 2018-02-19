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
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.MockitoAnnotations

private const val HTTPS_IMAGE_URL = "https://example.com/1.img"
private const val DATA_URI_IMAGE_URL = "data:image/png;base64,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"

class WebViewLongPressHandlerTest {

    private lateinit var testee: WebViewLongPressHandler

    @Mock
    private lateinit var mockMenu: ContextMenu

    @Mock
    private lateinit var mockMenuItem: MenuItem

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        testee = WebViewLongPressHandler()
    }

    @Test
    fun whenLongPressedWithImageTypeThenImageOptionsHeaderAddedToMenu() {
        testee.handleLongPress(HitTestResult.IMAGE_TYPE, HTTPS_IMAGE_URL, mockMenu)
        verify(mockMenu).setHeaderTitle(R.string.imageOptions)
    }

    @Test
    fun whenLongPressedWithAnchorImageTypeThenImageOptionsHeaderAddedToMenu() {
        testee.handleLongPress(HitTestResult.SRC_IMAGE_ANCHOR_TYPE, HTTPS_IMAGE_URL, mockMenu)
        verify(mockMenu).setHeaderTitle(R.string.imageOptions)
    }

    @Test
    fun whenLongPressedWithImageTypeThenDownloadImageMenuAdded() {
        testee.handleLongPress(HitTestResult.IMAGE_TYPE, HTTPS_IMAGE_URL, mockMenu)
        verify(mockMenu).add(anyInt(), eq(WebViewLongPressHandler.CONTEXT_MENU_ID_DOWNLOAD_IMAGE), anyInt(), eq(R.string.downloadImage))
    }

    @Test
    fun whenLongPressedWithAnchorImageTypeThenDownloadImageMenuAdded() {
        testee.handleLongPress(HitTestResult.SRC_IMAGE_ANCHOR_TYPE, HTTPS_IMAGE_URL, mockMenu)
        verify(mockMenu).add(anyInt(), eq(WebViewLongPressHandler.CONTEXT_MENU_ID_DOWNLOAD_IMAGE), anyInt(), eq(R.string.downloadImage))
    }

    @Test
    fun whenLongPressedWithOtherImageTypeThenMenuNotAltered() {
        testee.handleLongPress(HitTestResult.UNKNOWN_TYPE, HTTPS_IMAGE_URL, mockMenu)
        verifyMenuNotAltered()
    }

    @Test
    fun whenLongPressedWithImageTypeWhichIsADataUriThenMenuNotAltered() {
        testee.handleLongPress(HitTestResult.IMAGE_TYPE, DATA_URI_IMAGE_URL, mockMenu)
        verifyMenuNotAltered()
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

    private fun verifyMenuNotAltered() {
        verify(mockMenu, never()).setHeaderTitle(anyString())
        verify(mockMenu, never()).setHeaderTitle(anyInt())
        verify(mockMenu, never()).add(anyInt())
        verify(mockMenu, never()).add(anyString())
        verify(mockMenu, never()).add(anyInt(), anyInt(), anyInt(), anyInt())
        verify(mockMenu, never()).add(anyInt(), anyInt(), anyInt(), anyString())
    }
}