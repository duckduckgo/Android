package com.duckduckgo.autofill.impl.configuration.integration.modern.listener

import android.net.Uri
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.mock

class AutofillWebMessageListenerTest {

    private val mockReply: JavaScriptReplyProxy = mock()

    private val testee = object : AutofillWebMessageListener() {
        override val key: String
            get() = "testkey"

        override fun onPostMessage(
            p0: WebView,
            p1: WebMessageCompat,
            p2: Uri,
            p3: Boolean,
            p4: JavaScriptReplyProxy,
        ) {
        }

        fun testStoreReply(reply: JavaScriptReplyProxy): String {
            return storeReply(reply)
        }
    }

    @Test
    fun whenStoreReplyThenGetBackNonNullId() {
        assertNotNull(testee.testStoreReply(mockReply))
    }

    @Test
    fun whenAttemptResponseWithNoAssociatedReplyThenMessageNotHandled() {
        assertFalse(testee.onResponse("message", "unknown-request-id"))
    }

    @Test
    fun whenAttemptResponseWithAnAssociatedReplyThenMessageIsHandled() {
        val requestId = testee.testStoreReply(mockReply)
        assertTrue(testee.onResponse("message", requestId))
    }

    @Test
    fun whenReplyIsUsedThenItIsCleanedUp() {
        val requestId = testee.testStoreReply(mockReply)
        assertTrue(testee.onResponse("message", requestId))
        assertFalse(testee.onResponse("message", requestId))
    }

    @Test
    fun whenMaxConcurrentRepliesInUseThenAllStillUsable() {
        val requestIds = mutableListOf<String>()
        repeat(10) { requestIds.add(it, testee.testStoreReply(mockReply)) }
        requestIds.forEach {
            assertTrue(testee.onResponse("message", it))
        }
    }

    @Test
    fun whenMaxConcurrentRepliesPlusOneInUseThenAllButFirstIsStillUsable() {
        val requestIds = mutableListOf<String>()
        repeat(11) { requestIds.add(it, testee.testStoreReply(mockReply)) }
        assertFalse(testee.onResponse("message", requestIds.first()))
        requestIds.drop(1).forEach {
            assertTrue(testee.onResponse("message", it))
        }
    }
}
