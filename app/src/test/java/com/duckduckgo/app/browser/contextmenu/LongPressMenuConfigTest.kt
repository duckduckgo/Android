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

package com.duckduckgo.app.browser.contextmenu

import android.webkit.WebView.HitTestResult.IMAGE_TYPE
import android.webkit.WebView.HitTestResult.SRC_ANCHOR_TYPE
import android.webkit.WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
import android.webkit.WebView.HitTestResult.UNKNOWN_TYPE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LongPressMenuConfigTest {

    @Test
    fun whenLinkInRegularModeThenDedicatedFireRowShownAndPrimaryIsNewTab() {
        val config = longPressMenuConfigFor(SRC_ANCHOR_TYPE, isFireMode = false)!!
        assertEquals(LongPressMenuShape.LINK, config.shape)
        assertEquals(false, config.primaryRowIsFireTab)
        assertEquals(true, config.showDedicatedFireTabRow)
    }

    @Test
    fun whenLinkInFireModeThenPrimaryIsFireTabAndDedicatedRowHidden() {
        val config = longPressMenuConfigFor(SRC_ANCHOR_TYPE, isFireMode = true)!!
        assertEquals(LongPressMenuShape.LINK, config.shape)
        assertEquals(true, config.primaryRowIsFireTab)
        assertEquals(false, config.showDedicatedFireTabRow)
    }

    @Test
    fun whenImageThenImageShapeWithNoFireRows() {
        val config = longPressMenuConfigFor(IMAGE_TYPE, isFireMode = false)!!
        assertEquals(LongPressMenuShape.IMAGE, config.shape)
        assertEquals(false, config.primaryRowIsFireTab)
        assertEquals(false, config.showDedicatedFireTabRow)
    }

    @Test
    fun whenImageLinkInFireModeThenImageLinkShapeWithPrimaryFireTab() {
        val config = longPressMenuConfigFor(SRC_IMAGE_ANCHOR_TYPE, isFireMode = true)!!
        assertEquals(LongPressMenuShape.IMAGE_LINK, config.shape)
        assertEquals(true, config.primaryRowIsFireTab)
        assertEquals(false, config.showDedicatedFireTabRow)
    }

    @Test
    fun whenImageLinkInRegularModeThenImageLinkShapeWithDedicatedFireRow() {
        val config = longPressMenuConfigFor(SRC_IMAGE_ANCHOR_TYPE, isFireMode = false)!!
        assertEquals(LongPressMenuShape.IMAGE_LINK, config.shape)
        assertEquals(false, config.primaryRowIsFireTab)
        assertEquals(true, config.showDedicatedFireTabRow)
    }

    @Test
    fun whenUnsupportedTypeThenNull() {
        assertNull(longPressMenuConfigFor(UNKNOWN_TYPE, isFireMode = false))
    }
}
