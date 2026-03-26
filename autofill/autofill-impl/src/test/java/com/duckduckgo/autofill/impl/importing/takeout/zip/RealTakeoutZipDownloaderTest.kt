package com.duckduckgo.autofill.impl.importing.takeout.zip

import android.content.Context
import android.webkit.CookieManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.cookies.api.CookieManagerProvider
import dagger.Lazy
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class RealTakeoutZipDownloaderTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder.builder().assureDeletion().build()

    private val mockOkHttpClient = mock<OkHttpClient>()
    private val mockCall = mock<Call>()
    private val mockResponse = mock<Response>()
    private val mockCookieManager = mock<CookieManager>()
    private val mockCookieManagerProvider = mock<CookieManagerProvider>()
    private val mockContext = mock<Context>()
    private val lazyOkHttpClient = Lazy { mockOkHttpClient }
    private lateinit var tempFile: File

    private val testee =
        RealTakeoutZipDownloader(
            okHttpClient = lazyOkHttpClient,
            dispatchers = coroutineTestRule.testDispatcherProvider,
            context = mockContext,
            cookieManagerProvider = mockCookieManagerProvider,
        )

    companion object {
        private const val TEST_URL = "https://takeout.google.com/download/test.zip"
        private const val TEST_USER_AGENT = "TestAgent/1.0"
    }

    @Before
    fun setup() {
        whenever(mockCookieManagerProvider.get()).thenReturn(mockCookieManager)
        whenever(mockOkHttpClient.newCall(any())).thenReturn(mockCall)
        whenever(mockCall.execute()).thenReturn(mockResponse)

        tempFile = temporaryFolder.newFile("test.zip")
        whenever(mockContext.cacheDir).thenReturn(temporaryFolder.root)
    }

    @Test
    fun whenDownloadSucceedsThenReturnsFileUri() =
        runTest {
            val expectedResponseBody = "zip file content"
            configureRequestWithCookies()
            configureSuccessfulResponse(expectedResponseBody)

            val result = testee.downloadZip(TEST_URL, TEST_USER_AGENT)
            val resultFile = File(result.path!!)

            assertEquals(expectedResponseBody, resultFile.readText())
            assertEquals("file", result.scheme)
        }

    @Test(expected = IOException::class)
    fun whenDownloadFailsWithHttpErrorThenThrowsIOException() =
        runTest {
            whenever(mockResponse.isSuccessful).thenReturn(false)
            whenever(mockResponse.code).thenReturn(404)
            whenever(mockResponse.message).thenReturn("Not Found")
            testee.downloadZip(TEST_URL, TEST_USER_AGENT)
        }

    @Test(expected = IOException::class)
    fun whenResponseBodyIsNullThenThrowsIOException() =
        runTest {
            whenever(mockResponse.isSuccessful).thenReturn(true)
            whenever(mockResponse.body).thenReturn(null)
            testee.downloadZip(TEST_URL, TEST_USER_AGENT)
        }

    @Test
    fun whenNoCookiesAvailableThenStillMakesRequest() =
        runTest {
            val expectedResponseBody = "zip file content"
            configureRequestWithNoCookies()
            configureSuccessfulResponse(expectedResponseBody)

            val result = testee.downloadZip(TEST_URL, TEST_USER_AGENT)
            val resultFile = File(result.path!!)

            assertEquals(expectedResponseBody, resultFile.readText())
        }

    @Test(expected = IOException::class)
    fun whenExceptionThrownDuringExecuteThenPropagatesException() =
        runTest {
            val expectedException = IOException("Network error")
            whenever(mockCall.execute()).thenThrow(expectedException)
            testee.downloadZip(TEST_URL, TEST_USER_AGENT)
        }

    @Suppress("SameParameterValue")
    private fun configureSuccessfulResponse(responseBody: String) {
        val expectedZipData = responseBody.toByteArray()
        whenever(mockResponse.body).thenReturn(expectedZipData.toResponseBody("application/zip".toMediaType()))
        whenever(mockResponse.isSuccessful).thenReturn(true)
    }

    private fun configureRequestWithCookies() {
        whenever(mockCookieManager.getCookie(TEST_URL)).thenReturn("session=abc123")
    }

    private fun configureRequestWithNoCookies() {
        whenever(mockCookieManager.getCookie(TEST_URL)).thenReturn(null)
    }
}
