package com.duckduckgo.sync.impl.ui.qrcode

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncBarcodeUrlTest {

    @Test
    fun whenDeviceNamePopulatedThenIncludedInUrl() {
        val url = SyncBarcodeUrl(webSafeB64EncodedCode = "ABC-123", urlEncodedDeviceName = "iPhone")
        assertEquals("${URL_BASE}code=ABC-123&deviceName=iPhone", url.asUrl())
    }

    @Test
    fun whenDeviceNameBlankStringThenNotIncludedInUrl() {
        val url = SyncBarcodeUrl(webSafeB64EncodedCode = "ABC-123", urlEncodedDeviceName = " ")
        assertEquals("${URL_BASE}code=ABC-123", url.asUrl())
    }

    @Test
    fun whenDeviceNameEmptyStringThenNotIncludedInUrl() {
        val url = SyncBarcodeUrl(webSafeB64EncodedCode = "ABC-123", urlEncodedDeviceName = "")
        assertEquals("${URL_BASE}code=ABC-123", url.asUrl())
    }

    @Test
    fun whenDeviceNameNullThenNotIncludedInUrl() {
        val url = SyncBarcodeUrl(webSafeB64EncodedCode = "ABC-123", urlEncodedDeviceName = null)
        assertEquals("${URL_BASE}code=ABC-123", url.asUrl())
    }

    @Test
    fun whenCodeProvidedThenIsSuccessfullyExtracted() {
        val url = SyncBarcodeUrl.parseUrl("${URL_BASE}code=ABC-123")
        assertNotNull(url)
        assertEquals("ABC-123", url!!.webSafeB64EncodedCode)
    }

    @Test
    fun whenCodeMissingProvidedThenIsNull() {
        val url = SyncBarcodeUrl.parseUrl(URL_BASE)
        assertNull(url)
    }

    companion object {
        private const val URL_BASE = "https://duckduckgo.com/sync/pairing/#&"
    }
}
