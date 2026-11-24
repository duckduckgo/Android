package com.duckduckgo.sync.impl.setup

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.sync.impl.ui.qrcode.SyncBarcodeUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealSyncUrlIdentifierTest {

    private val testee = RealSyncUrlIdentifier()

    @Test
    fun whenInputIsNullThenShouldNotDelegate() {
        assertFalse(testee.shouldDelegateToSyncSetup(intentText = null))
    }

    @Test
    fun whenInputIsEmptyStringThenShouldNotDelegate() {
        assertFalse(testee.shouldDelegateToSyncSetup(intentText = ""))
    }

    @Test
    fun whenInputStartsWithUrlBaseButIsIncompleteThenShouldNotDelegate() {
        assertFalse(testee.shouldDelegateToSyncSetup(intentText = SyncBarcodeUrl.URL_BASE))
    }

    @Test
    fun whenInputIsValidSyncSetupUrlThenShouldDelegate() {
        val inputText = SyncBarcodeUrl(
            webSafeB64EncodedCode = "code",
            deviceName = "deviceName",
        ).asUrl()
        assertTrue(testee.shouldDelegateToSyncSetup(intentText = inputText))
    }
}
