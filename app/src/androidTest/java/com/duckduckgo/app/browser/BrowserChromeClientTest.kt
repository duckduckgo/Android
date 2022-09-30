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
import android.net.Uri
import android.os.Message
import android.view.View
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.core.net.toUri
import androidx.test.annotation.UiThreadTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.global.exception.UncaughtExceptionRepository
import com.duckduckgo.app.global.exception.UncaughtExceptionSource
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.privacy.config.api.Drm
import com.duckduckgo.site.permissions.api.SitePermissionsManager
import org.mockito.kotlin.*
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString

@ExperimentalCoroutinesApi
class BrowserChromeClientTest {

    private lateinit var testee: BrowserChromeClient
    private lateinit var webView: TestWebView
    private lateinit var mockWebViewClientListener: WebViewClientListener
    private lateinit var mockUncaughtExceptionRepository: UncaughtExceptionRepository
    private lateinit var mockFilePathCallback: ValueCallback<Array<Uri>>
    private lateinit var mockFileChooserParams: WebChromeClient.FileChooserParams
    private lateinit var mockDrm: Drm
    private lateinit var mockAppBuildConfig: AppBuildConfig
    private lateinit var mockSitePermissionsManager: SitePermissionsManager
    private val fakeView = View(getInstrumentation().targetContext)

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    @ExperimentalCoroutinesApi
    @UiThreadTest
    @Before
    fun setup() {
        mockUncaughtExceptionRepository = mock()
        mockDrm = mock()
        mockAppBuildConfig = mock()
        mockSitePermissionsManager = mock()
        testee = BrowserChromeClient(
            mockUncaughtExceptionRepository,
            mockDrm,
            mockAppBuildConfig,
            TestScope(),
            coroutineTestRule.testDispatcherProvider,
            mockSitePermissionsManager
        )
        mockWebViewClientListener = mock()
        mockFilePathCallback = mock()
        mockFileChooserParams = mock()
        testee.webViewClientListener = mockWebViewClientListener
        webView = TestWebView(getInstrumentation().targetContext)
        whenever(mockDrm.getDrmPermissionsForRequest(any(), any())).thenReturn(arrayOf())
        mockSitePermissionsManager.stub { onBlocking { getSitePermissionsAllowedToAsk(any(), any()) }.thenReturn(arrayOf()) }
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
    fun whenCustomViewShownThrowsExceptionThenRecordException() = runTest {
        val exception = RuntimeException()
        whenever(mockWebViewClientListener.goFullScreen(any())).thenThrow(exception)
        testee.onShowCustomView(fakeView, null)
        verify(mockUncaughtExceptionRepository).recordUncaughtException(exception, UncaughtExceptionSource.SHOW_CUSTOM_VIEW)
    }

    @Test
    fun whenHideCustomViewCalledThenListenerInstructedToExistFullScreen() = runTest {
        testee.onHideCustomView()
        verify(mockWebViewClientListener).exitFullScreen()
    }

    @Test
    fun whenHideCustomViewThrowsExceptionThenRecordException() = runTest {
        val exception = RuntimeException()
        whenever(mockWebViewClientListener.exitFullScreen()).thenThrow(exception)
        testee.onHideCustomView()
        verify(mockUncaughtExceptionRepository).recordUncaughtException(exception, UncaughtExceptionSource.HIDE_CUSTOM_VIEW)
    }

    @UiThreadTest
    @Test
    fun whenOnProgressChangedCalledThenListenerInstructedToUpdateProgress() {
        testee.onProgressChanged(webView, 10)
        verify(mockWebViewClientListener).progressChanged(10)
    }

    @UiThreadTest
    @Test
    fun whenOnProgressChangedCalledThenListenerInstructedToUpdateNavigationState() {
        testee.onProgressChanged(webView, 10)
        verify(mockWebViewClientListener).navigationStateChanged(any())
    }

    @UiThreadTest
    @Test
    fun whenOnProgressChangedThrowsExceptionThenRecordException() = runTest {
        val exception = RuntimeException()
        whenever(mockWebViewClientListener.progressChanged(anyInt())).thenThrow(exception)
        testee.onProgressChanged(webView, 10)
        verify(mockUncaughtExceptionRepository).recordUncaughtException(exception, UncaughtExceptionSource.ON_PROGRESS_CHANGED)
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
    fun whenOnReceivedTitleThrowsExceptionThenRecordException() = runTest {
        val exception = RuntimeException()
        whenever(mockWebViewClientListener.titleReceived(anyString())).thenThrow(exception)
        testee.onReceivedTitle(webView, "")
        verify(mockUncaughtExceptionRepository).recordUncaughtException(exception, UncaughtExceptionSource.RECEIVED_PAGE_TITLE)
    }

    @Test
    fun whenOnShowFileChooserCalledThenShowFileChooser() {
        assertTrue(testee.onShowFileChooser(webView, mockFilePathCallback, mockFileChooserParams))
        verify(mockWebViewClientListener).showFileChooser(mockFilePathCallback, mockFileChooserParams)
    }

    @Test
    fun whenShowFileChooserThrowsExceptionThenRecordException() = runTest {
        val exception = RuntimeException("deliberate")

        whenever(mockWebViewClientListener.showFileChooser(any(), any())).thenThrow(exception)
        testee.onShowFileChooser(webView, mockFilePathCallback, mockFileChooserParams)

        verify(mockUncaughtExceptionRepository).recordUncaughtException(exception, UncaughtExceptionSource.SHOW_FILE_CHOOSER)
        verify(mockFilePathCallback).onReceiveValue(null)
    }

    @Test
    fun whenOnMediaPermissionRequestIfDomainIsInAllowedListThenPermissionIsGranted() {
        val permissions = arrayOf(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID)
        val mockPermission: PermissionRequest = mock()
        whenever(mockPermission.resources).thenReturn(permissions)
        whenever(mockPermission.origin).thenReturn("https://open.spotify.com".toUri())
        whenever(mockDrm.getDrmPermissionsForRequest(any(), any())).thenReturn(permissions)
        testee.onPermissionRequest(mockPermission)

        verify(mockPermission).grant(arrayOf(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID))
    }

    @Test
    fun whenOnMediaPermissionRequestIfDomainIsNotInAllowedListThenPermissionIsNotGranted() {
        val permissions = arrayOf(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID)
        val mockPermission: PermissionRequest = mock()
        whenever(mockPermission.resources).thenReturn(permissions)
        whenever(mockPermission.origin).thenReturn("https://www.example.com".toUri())
        whenever(mockDrm.getDrmPermissionsForRequest(any(), any())).thenReturn(arrayOf())

        testee.onPermissionRequest(mockPermission)

        verify(mockPermission, never()).grant(any())
    }

    @ExperimentalCoroutinesApi
    @Test
    fun whenOnCameraPermissionRequestIfDomainIsAllowToAskThenRequestPermission() = runTest {
        val permissions = arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
        val mockRequest: PermissionRequest = mock()
        whenever(mockRequest.resources).thenReturn(permissions)
        whenever(mockRequest.origin).thenReturn("https://www.example.com".toUri())
        whenever(mockSitePermissionsManager.getSitePermissionsAllowedToAsk(any(), any())).thenReturn(permissions)

        testee.onPermissionRequest(mockRequest)

        verify(mockWebViewClientListener).onSitePermissionRequested(mockRequest, permissions)
    }

    @ExperimentalCoroutinesApi
    @Test
    fun whenOnMicPermissionRequestIfDomainIsAllowToAskThenRequestPermission() = runTest {
        val permissions = arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
        val mockRequest: PermissionRequest = mock()
        whenever(mockRequest.resources).thenReturn(permissions)
        whenever(mockRequest.origin).thenReturn("https://www.example.com".toUri())
        whenever(mockSitePermissionsManager.getSitePermissionsAllowedToAsk(any(), any())).thenReturn(permissions)

        testee.onPermissionRequest(mockRequest)

        verify(mockWebViewClientListener).onSitePermissionRequested(mockRequest, permissions)
    }

    @ExperimentalCoroutinesApi
    @Test
    fun whenNotSitePermissionsAreRequestedThenRequestPermissionIsNotCalled() = runTest {
        val permissions = arrayOf<String>()
        val mockRequest: PermissionRequest = mock()
        whenever(mockRequest.resources).thenReturn(permissions)
        whenever(mockRequest.origin).thenReturn("https://www.example.com".toUri())
        whenever(mockSitePermissionsManager.getSitePermissionsAllowedToAsk(any(), any())).thenReturn(permissions)

        testee.onPermissionRequest(mockRequest)

        verify(mockWebViewClientListener, never()).onSitePermissionRequested(mockRequest, permissions)
    }

    private val mockMsg = Message().apply {
        target = mock()
        obj = mock<WebView.WebViewTransport>()
    }

    private class TestWebView(context: Context) : WebView(context) {
        override fun getUrl(): String {
            return "https://example.com"
        }
    }
}
