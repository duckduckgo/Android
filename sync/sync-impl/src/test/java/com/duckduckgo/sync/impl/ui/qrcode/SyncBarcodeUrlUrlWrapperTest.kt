package com.duckduckgo.sync.impl.ui.qrcode

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.sync.impl.SyncDeviceIds
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

@RunWith(AndroidJUnit4::class)
class SyncBarcodeUrlUrlWrapperTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val syncDeviceIds: SyncDeviceIds = mock()

    private val testee = SyncBarcodeUrlUrlWrapper(syncDeviceIds = syncDeviceIds)

    @Before
    fun setup() {
        whenever(syncDeviceIds.deviceName()).thenReturn("iPhone")
    }

    @Test
    fun whenUrlGivenThenUrlWrappedCodeReturned() = runTest {
        testee.wrapCodeInUrl(B64_ENCODED_PLAIN_CODE).assertIsUrlContainingCode(B64_URL_SAFE_ENCODED_PLAIN_CODE)
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
