package com.duckduckgo.sync.impl.ui.qrcode

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.sync.impl.SyncDeviceIds
import com.duckduckgo.sync.impl.SyncFeature
import com.duckduckgo.sync.impl.ui.qrcode.SyncBarcodeDecorator.CodeType.Connect
import com.duckduckgo.sync.impl.ui.qrcode.SyncBarcodeDecorator.CodeType.Exchange
import com.duckduckgo.sync.impl.ui.qrcode.SyncBarcodeDecorator.CodeType.Recovery
import com.duckduckgo.sync.impl.ui.qrcode.SyncBarcodeUrl.Companion.URL_BASE
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
@RunWith(AndroidJUnit4::class)
class SyncBarcodeUrlDecoratorTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val feature = FakeFeatureToggleFactory.create(SyncFeature::class.java)
    private val syncDeviceIds: SyncDeviceIds = mock()

    private val testee = SyncBarcodeUrlDecorator(
        syncFeature = feature,
        dispatchers = coroutineTestRule.testDispatcherProvider,
        syncDeviceIds = syncDeviceIds,
    )

    @Before
    fun setup() {
        whenever(syncDeviceIds.deviceName()).thenReturn("iPhone")
    }

    @Test
    fun whenFeatureDisabledThenUrlNeverReturned() = runTest {
        configureFeatureState(enabled = false)
        testee.decorateCode(B64_ENCODED_PLAIN_CODE, Exchange).assertIsNotUrlAndOnlyCode(B64_ENCODED_PLAIN_CODE)
        testee.decorateCode(B64_ENCODED_PLAIN_CODE, Recovery).assertIsNotUrlAndOnlyCode(B64_ENCODED_PLAIN_CODE)
        testee.decorateCode(B64_ENCODED_PLAIN_CODE, Connect).assertIsNotUrlAndOnlyCode(B64_ENCODED_PLAIN_CODE)
    }

    @Test
    fun whenFeatureEnabledThenExchangeCodeIsUrlWrapped() = runTest {
        configureFeatureState(enabled = true)
        testee.decorateCode(B64_ENCODED_PLAIN_CODE, Exchange).assertIsUrlContainingCode(B64_URL_SAFE_ENCODED_PLAIN_CODE)
    }

    @Test
    fun whenFeatureEnabledThenConnectCodeIsUrlWrapped() = runTest {
        configureFeatureState(enabled = true)
        testee.decorateCode(B64_ENCODED_PLAIN_CODE, Connect).assertIsUrlContainingCode(B64_URL_SAFE_ENCODED_PLAIN_CODE)
    }

    @Test
    fun whenFeatureEnabledThenRecoveryCodeIsNotUrlWrapped() = runTest {
        configureFeatureState(enabled = true)
        testee.decorateCode(B64_ENCODED_PLAIN_CODE, Recovery).assertIsNotUrlAndOnlyCode(B64_ENCODED_PLAIN_CODE)
    }

    private fun configureFeatureState(enabled: Boolean) {
        feature.syncSetupBarcodeIsUrlBased().setRawStoredState(State(enable = enabled))
    }

    private companion object {
        private const val B64_ENCODED_PLAIN_CODE = "QUJDLTEyMw=="
        private const val B64_URL_SAFE_ENCODED_PLAIN_CODE = "QUJDLTEyMw"
    }

    private fun String.assertIsUrlContainingCode(code: String) {
        assertTrue("Expected $this to be a sync pairing URL", this.startsWith(URL_BASE))
        assertTrue("Expected $this to contain code $code", this.contains(code))
    }

    private fun String.assertIsNotUrlAndOnlyCode(expectedCode: String) {
        assertEquals("Expected $this to be a plain code exactly matching $expectedCode", expectedCode, this)
    }
}
