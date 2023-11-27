package com.duckduckgo.autofill.impl.pixel

import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SAVE_LOGIN_PROMPT_SHOWN
import com.duckduckgo.autofill.impl.pixel.AutofillPixelParameters.AUTOFILL_DEFAULT_STATE
import com.duckduckgo.autofill.store.AutofillPrefsStore
import com.duckduckgo.common.test.api.FakeChain
import okhttp3.HttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AutofillPixelInterceptorTest {

    private val autofillStore: AutofillPrefsStore = mock()
    private val testee: AutofillPixelInterceptor = AutofillPixelInterceptor(autofillStore)

    @Test
    fun whenNotAMatchThenParamsNotAdded() {
        val url = intercept(NON_MATCHING_PIXEL)
        assertNull(url.queryParameter(MATCHING_PIXEL))
    }

    @Test
    fun whenMatchingPixelAndDefaultStateWasDisabledThenCorrectParamsAdded() {
        configureDefaultEnabledState(false)
        val url = intercept(MATCHING_PIXEL)
        val parameter = url.queryParameter(AUTOFILL_DEFAULT_STATE)
        assertEquals("off", parameter)
    }

    @Test
    fun whenMatchingPixelAndDefaultStateWasEnabledThenCorrectParamsAdded() {
        configureDefaultEnabledState(true)
        val url = intercept(MATCHING_PIXEL)
        val parameter = url.queryParameter(AUTOFILL_DEFAULT_STATE)
        assertEquals("on", parameter)
    }

    private fun configureDefaultEnabledState(defaultEnabledState: Boolean) {
        whenever(autofillStore.wasDefaultStateEnabled()).thenReturn(defaultEnabledState)
    }

    private fun intercept(pixelName: String): HttpUrl {
        val pixelUrl = String.format(PIXEL_TEMPLATE, pixelName)
        return testee.intercept(FakeChain(pixelUrl)).request.url
    }

    companion object {
        private const val PIXEL_TEMPLATE = "https://improving.duckduckgo.com/t/%s_android_phone?"

        // any arbitrary pixel which is defined in the interceptor
        private val MATCHING_PIXEL = AUTOFILL_SAVE_LOGIN_PROMPT_SHOWN.pixelName
        private const val NON_MATCHING_PIXEL = "m_not_a_match"
    }
}
