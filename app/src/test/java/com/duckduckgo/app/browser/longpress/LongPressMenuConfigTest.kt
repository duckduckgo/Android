package com.duckduckgo.app.browser.longpress

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
