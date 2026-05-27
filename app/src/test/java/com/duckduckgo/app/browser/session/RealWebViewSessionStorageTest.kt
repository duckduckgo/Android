/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.app.browser.session

import android.os.Bundle
import android.os.Parcel
import android.webkit.WebBackForwardList
import android.webkit.WebView
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class RealWebViewSessionStorageTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val dao = FakeWebViewSessionDao()

    private val storage = RealWebViewSessionStorage(
        dao = dao,
        appScope = coroutineRule.testScope,
        dispatchers = coroutineRule.testDispatcherProvider,
    )

    @Test
    fun whenSaveSessionThenDaoUpsertCalledWithRoundTrippableBytes() {
        val webView: WebView = mock {
            on { saveState(any()) } doAnswer { invocation ->
                val bundle = invocation.getArgument<Bundle>(0)
                bundle.putString("test_key", "test_value")
                null
            }
        }

        storage.saveSession(webView, "tab-1")
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        val entity = dao.getSync("tab-1")
        assertNotNull(entity)
        val bytes = entity!!.sessionBundle
        assertTrue(bytes.isNotEmpty())

        // Unmarshal and verify round-trip
        val parcel = Parcel.obtain()
        try {
            parcel.unmarshall(bytes, 0, bytes.size)
            parcel.setDataPosition(0)
            val restored = parcel.readBundle(javaClass.classLoader)
            assertNotNull(restored)
            assertTrue(restored!!.containsKey("test_key"))
        } finally {
            parcel.recycle()
        }
    }

    @Test
    fun whenRestoreSessionAndNoRowThenReturnsFalseAndWebViewUntouched() = runTest {
        val webView: WebView = mock()

        val result = storage.restoreSession(webView, "tab-missing")

        assertFalse(result)
        verify(webView, never()).restoreState(any())
    }

    @Test
    fun whenRestoreSessionAndRestoreYieldsEmptyListThenReturnsFalse() = runTest {
        val backForwardList: WebBackForwardList = mock {
            on { size } doReturn 0
        }
        val webView: WebView = mock {
            on { copyBackForwardList() } doReturn backForwardList
        }

        // Seed DAO with valid marshalled bundle bytes
        dao.upsert(WebViewSessionEntity("tab-1", validBundleBytes(), System.currentTimeMillis()))

        val result = storage.restoreSession(webView, "tab-1")

        assertFalse(result)
    }

    @Test
    fun whenRestoreSessionAndRestoreYieldsNonEmptyListThenReturnsTrue() = runTest {
        val backForwardList: WebBackForwardList = mock {
            on { size } doReturn 2
        }
        val webView: WebView = mock {
            on { copyBackForwardList() } doReturn backForwardList
        }

        // Seed DAO with valid marshalled bundle bytes
        dao.upsert(WebViewSessionEntity("tab-1", validBundleBytes(), System.currentTimeMillis()))

        val result = storage.restoreSession(webView, "tab-1")

        assertTrue(result)
        verify(webView, times(1)).restoreState(any())
    }

    @Test
    fun whenRestoreSessionAndStoredBytesAreCorruptThenReturnsFalse() = runTest {
        val webView: WebView = mock()

        dao.upsert(WebViewSessionEntity("tab-1", byteArrayOf(0, 1, 2, 3), System.currentTimeMillis()))

        val result = storage.restoreSession(webView, "tab-1")

        assertFalse(result)
        verify(webView, never()).restoreState(any())
    }

    @Test
    fun whenDeleteSessionThenDaoDeleteCalled() {
        dao.upsertSync(WebViewSessionEntity("tab-1", validBundleBytes(), System.currentTimeMillis()))

        storage.deleteSession("tab-1")
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        assertTrue(dao.getSync("tab-1") == null)
    }

    @Test
    fun whenDeleteAllSessionsThenAllRowsRemoved() = runTest {
        dao.upsert(WebViewSessionEntity("tab-1", validBundleBytes(), System.currentTimeMillis()))
        dao.upsert(WebViewSessionEntity("tab-2", validBundleBytes(), System.currentTimeMillis()))

        storage.deleteAllSessions()

        assertTrue(dao.getSync("tab-1") == null)
        assertTrue(dao.getSync("tab-2") == null)
    }

    // Helper: produce a valid marshalled bundle ByteArray with a known key
    private fun validBundleBytes(): ByteArray {
        val bundle = Bundle().apply { putString("session_key", "session_value") }
        val parcel = Parcel.obtain()
        return try {
            parcel.writeBundle(bundle)
            parcel.marshall()
        } finally {
            parcel.recycle()
        }
    }
}

private class FakeWebViewSessionDao : WebViewSessionDao {
    private val rows = mutableMapOf<String, WebViewSessionEntity>()

    override suspend fun get(tabId: String): WebViewSessionEntity? = rows[tabId]
    override suspend fun upsert(session: WebViewSessionEntity) { rows[session.tabId] = session }
    override suspend fun delete(tabId: String) { rows.remove(tabId) }
    override suspend fun deleteAll() { rows.clear() }

    // Synchronous helpers for use outside coroutines in tests
    fun getSync(tabId: String): WebViewSessionEntity? = rows[tabId]
    fun upsertSync(session: WebViewSessionEntity) { rows[session.tabId] = session }
}
