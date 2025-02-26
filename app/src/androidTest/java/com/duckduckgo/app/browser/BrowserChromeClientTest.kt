/*
 * Copyright (c) 2018 DuckDuckGo
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

@file:Suppress("RemoveExplicitTypeArguments")

package com.duckduckgo.app.browser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Message
import android.view.View
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.core.graphics.get
import androidx.core.net.toUri
import androidx.test.annotation.UiThreadTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.site.permissions.api.SitePermissionsManager
import com.duckduckgo.site.permissions.api.SitePermissionsManager.SitePermissions
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.*

class BrowserChromeClientTest {

    private lateinit var testee: BrowserChromeClient
    private lateinit var webView: TestWebView
    private lateinit var mockWebViewClientListener: WebViewClientListener
    private lateinit var mockFilePathCallback: ValueCallback<Array<Uri>>
    private lateinit var mockFileChooserParams: WebChromeClient.FileChooserParams
    private lateinit var mockAppBuildConfig: AppBuildConfig
    private lateinit var mockSitePermissionsManager: SitePermissionsManager
    private val fakeView = View(getInstrumentation().targetContext)

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    @UiThreadTest
    @Before
    fun setup() {
        mockAppBuildConfig = mock()
        mockSitePermissionsManager = mock()
        testee = BrowserChromeClient(
            mockAppBuildConfig,
            TestScope(),
            coroutineTestRule.testDispatcherProvider,
            mockSitePermissionsManager,
        )
        mockWebViewClientListener = mock()
        mockFilePathCallback = mock()
        mockFileChooserParams = mock()
        testee.webViewClientListener = mockWebViewClientListener
        webView = TestWebView(getInstrumentation().targetContext)
        mockSitePermissionsManager.stub { onBlocking { getSitePermissions(any(), any()) }.thenReturn(SitePermissions(emptyList(), emptyList())) }
    }

    @Test
    fun whenWindowClosedThenCloseCurrentTab() {
        testee.onCloseWindow(window = null)
        verify(mockWebViewClientListener).closeCurrentTab()
    }

    @Test
    fun whenCustomViewShownForFirstTimeListenerInstructedToGoFullScreen() {
        testee.onShowCustomView(fakeView, null)
        verify(mockWebViewClientListener).goFullScreen(fakeView)
    }

    @Test
    fun whenCustomViewShownMultipleTimesListenerInstructedToGoFullScreenOnlyOnce() {
        testee.onShowCustomView(fakeView, null)
        testee.onShowCustomView(fakeView, null)
        testee.onShowCustomView(fakeView, null)
        verify(mockWebViewClientListener, times(1)).goFullScreen(fakeView)
    }

    @Test
    fun whenCustomViewShownMultipleTimesCallbackInstructedToHideForAllButTheFirstCall() {
        val mockCustomViewCallback: WebChromeClient.CustomViewCallback = mock()
        testee.onShowCustomView(fakeView, mockCustomViewCallback)
        testee.onShowCustomView(fakeView, mockCustomViewCallback)
        testee.onShowCustomView(fakeView, mockCustomViewCallback)
        verify(mockCustomViewCallback, times(2)).onCustomViewHidden()
    }

    @Test
    fun whenHideCustomViewCalledThenListenerInstructedToExistFullScreen() = runTest {
        testee.onHideCustomView()
        verify(mockWebViewClientListener).exitFullScreen()
    }

    @UiThreadTest
    @Test
    fun whenOnProgressChangedCalledThenListenerInstructedToUpdateProgress() {
        testee.onProgressChanged(webView, 10)
        verify(mockWebViewClientListener).progressChanged(eq(20), any()) // Value should come from the webView instance
    }

    @UiThreadTest
    @Test
    fun whenOnProgressChangedCalledAndValueIsZeroThenNothingCalled() {
        val mockWebView: WebView = mock()
        whenever(mockWebView.progress).thenReturn(0)
        testee.onProgressChanged(mockWebView, 10)
        verify(mockWebViewClientListener, never()).progressChanged(any(), any())
    }

    @UiThreadTest
    @Test
    fun whenOnCreateWindowWithUserGestureThenMessageOpenedInNewTab() {
        testee.onCreateWindow(webView, isDialog = false, isUserGesture = true, resultMsg = mockMsg)
        verify(mockWebViewClientListener).openMessageInNewTab(eq(mockMsg))
        verifyNoMoreInteractions(mockWebViewClientListener)
    }

    @UiThreadTest
    @Test
    fun whenOnCreateWindowWithoutUserGestureThenNewTabNotOpened() {
        testee.onCreateWindow(webView, isDialog = false, isUserGesture = false, resultMsg = mockMsg)
        verifyNoInteractions(mockWebViewClientListener)
    }

    @Test
    fun whenOnReceivedIconThenIconReceived() {
        val bitmap: Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565)
        testee.onReceivedIcon(webView, bitmap)
        verify(mockWebViewClientListener).iconReceived(webView.url, bitmap)
    }

    @Test
    fun whenOnReceivedTitleThenTitleReceived() {
        val title = "title"
        testee.onReceivedTitle(webView, title)
        verify(mockWebViewClientListener).titleReceived(title)
    }

    @Test
    fun whenOnShowFileChooserCalledThenShowFileChooser() {
        assertTrue(testee.onShowFileChooser(webView, mockFilePathCallback, mockFileChooserParams))
        verify(mockWebViewClientListener).showFileChooser(mockFilePathCallback, mockFileChooserParams)
    }

    @Test(expected = java.lang.RuntimeException::class)
    fun whenShowFileChooserThrowsExceptionThenRecordException() = runTest {
        val exception = RuntimeException("deliberate")

        whenever(mockWebViewClientListener.showFileChooser(any(), any())).thenThrow(exception)
        testee.onShowFileChooser(webView, mockFilePathCallback, mockFileChooserParams)

        verify(mockFilePathCallback).onReceiveValue(null)
    }

    @Test
    fun whenOnMediaPermissionRequestIfDomainIsAllowToAskThenRequestPermission() = runTest {
        val permissions = SitePermissions(
            userHandled = listOf(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID),
            autoAccept = emptyList(),
        )
        val mockPermission: PermissionRequest = mock()
        whenever(mockWebViewClientListener.getCurrentTabId()).thenReturn("id")
        whenever(mockPermission.resources).thenReturn(arrayOf(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID))
        whenever(mockPermission.origin).thenReturn("https://open.spotify.com".toUri())
        whenever(mockSitePermissionsManager.getSitePermissions(any(), any())).thenReturn(permissions)
        testee.onPermissionRequest(mockPermission)

        verify(mockWebViewClientListener).onSitePermissionRequested(mockPermission, permissions)
    }

    @Test
    fun whenOnCameraPermissionRequestIfDomainIsAllowToAskThenRequestPermission() = runTest {
        val permissions = SitePermissions(
            userHandled = listOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE),
            autoAccept = emptyList(),
        )
        val mockRequest: PermissionRequest = mock()
        whenever(mockWebViewClientListener.getCurrentTabId()).thenReturn("id")
        whenever(mockRequest.resources).thenReturn(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
        whenever(mockRequest.origin).thenReturn("https://www.example.com".toUri())
        whenever(mockSitePermissionsManager.getSitePermissions(any(), any())).thenReturn(permissions)

        testee.onPermissionRequest(mockRequest)

        verify(mockWebViewClientListener).onSitePermissionRequested(mockRequest, permissions)
    }

    @Test
    fun whenOnMicPermissionRequestIfDomainIsAllowToAskThenRequestPermission() = runTest {
        val permissions = SitePermissions(
            userHandled = listOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE),
            autoAccept = emptyList(),
        )
        val mockRequest: PermissionRequest = mock()
        whenever(mockWebViewClientListener.getCurrentTabId()).thenReturn("id")
        whenever(mockRequest.resources).thenReturn(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
        whenever(mockRequest.origin).thenReturn("https://www.example.com".toUri())
        whenever(mockSitePermissionsManager.getSitePermissions(any(), any())).thenReturn(permissions)

        testee.onPermissionRequest(mockRequest)

        verify(mockWebViewClientListener).onSitePermissionRequested(mockRequest, permissions)
    }

    @Test
    fun whenNotSitePermissionsAreRequestedThenCallOnSitePermissionRequested() = runTest {
        val permissions = SitePermissions(emptyList(), emptyList())
        val mockRequest: PermissionRequest = mock()
        whenever(mockWebViewClientListener.getCurrentTabId()).thenReturn("id")
        whenever(mockRequest.resources).thenReturn(arrayOf())
        whenever(mockRequest.origin).thenReturn("https://www.example.com".toUri())
        whenever(mockSitePermissionsManager.getSitePermissions(any(), any())).thenReturn(permissions)

        testee.onPermissionRequest(mockRequest)

        verify(mockWebViewClientListener, never()).onSitePermissionRequested(mockRequest, permissions)
    }

    @Test
    fun whenGetDefaultVideoPosterThenReturnTransparentPixel() = runTest {
        val bitmap = testee.defaultVideoPoster

        assertEquals(1, bitmap.width)
        assertEquals(1, bitmap.height)
        assertEquals(Color.TRANSPARENT, bitmap[0, 0])
    }

    private val mockMsg = Message().apply {
        target = mock()
        obj = mock<WebView.WebViewTransport>()
    }

    private class TestWebView(context: Context) : WebView(context) {
        override fun getUrl(): String {
            return "https://example.com"
        }

        override fun getProgress(): Int {
            return 20
        }
    }
}
