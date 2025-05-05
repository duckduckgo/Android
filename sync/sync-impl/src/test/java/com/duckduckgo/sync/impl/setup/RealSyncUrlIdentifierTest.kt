package com.duckduckgo.sync.impl.setup

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.sync.impl.SyncFeature
import com.duckduckgo.sync.impl.ui.qrcode.SyncBarcodeUrl
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@SuppressLint("DenyListedApi")
@RunWith(AndroidJUnit4::class)
class RealSyncUrlIdentifierTest {

    private val feature = FakeFeatureToggleFactory.create(SyncFeature::class.java)
    private val testee = RealSyncUrlIdentifier(feature)

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
    fun whenInputIsValidSyncSetupUrlButFeatureDisabledThenShouldNotDelegate() = runTest {
        feature.canInterceptSyncSetupUrls().setRawStoredState(State(enable = false))
        assertFalse(testee.shouldDelegateToSyncSetup(intentText = SyncBarcodeUrl.URL_BASE))
    }

    @Test
    fun whenInputIsValidAndFeatureEnabledSyncSetupUrlThenShouldDelegate() {
        feature.canInterceptSyncSetupUrls().setRawStoredState(State(enable = true))
        val inputText = SyncBarcodeUrl(
            webSafeB64EncodedCode = "code",
            deviceName = "deviceName",
        ).asUrl()
        assertTrue(testee.shouldDelegateToSyncSetup(intentText = inputText))
    }
}
