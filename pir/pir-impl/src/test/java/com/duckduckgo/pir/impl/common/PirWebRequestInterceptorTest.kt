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

package com.duckduckgo.pir.impl.common

import android.net.Uri
import android.webkit.WebResourceRequest
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.trackerdetection.model.TrackerStatus
import com.duckduckgo.app.trackerdetection.model.TrackerType
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.pir.impl.PirRemoteFeatures
import com.duckduckgo.tracker.detection.api.TrackerDetector
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class PirWebRequestInterceptorTest {

    private val mockTrackerDetector: TrackerDetector = mock()
    private val mockPirRemoteFeatures: PirRemoteFeatures = mock()
    private val mockToggle: Toggle = mock()
    private lateinit var interceptor: PirWebRequestInterceptor

    @Before
    fun setup() {
        whenever(mockPirRemoteFeatures.trackerBlocking()).thenReturn(mockToggle)
        whenever(mockToggle.isEnabled()).thenReturn(true)
        interceptor = PirWebRequestInterceptor(mockTrackerDetector, mockPirRemoteFeatures)
    }

    @Test
    fun whenTrackerIsBlockedThenReturnsEmptyResponse() {
        val requestUrl = "http://tracker.com/script.js".toUri()
        val documentUrl = "http://example.com".toUri()
        val request = mockRequest(requestUrl, isForMainFrame = false)

        whenever(mockTrackerDetector.evaluate(any<Uri>(), any(), any(), any()))
            .thenReturn(blockedTrackingEvent(documentUrl.toString(), requestUrl.toString()))

        val result = interceptor.shouldInterceptRequest(request) { documentUrl }

        assertNotNull(result)
    }

    @Test
    fun whenTrackerIsAllowedThenReturnsNull() {
        val requestUrl = "http://tracker.com/script.js".toUri()
        val documentUrl = "http://example.com".toUri()
        val request = mockRequest(requestUrl, isForMainFrame = false)

        whenever(mockTrackerDetector.evaluate(any<Uri>(), any(), any(), any()))
            .thenReturn(allowedTrackingEvent(documentUrl.toString(), requestUrl.toString()))

        val result = interceptor.shouldInterceptRequest(request) { documentUrl }

        assertNull(result)
    }

    @Test
    fun whenNoTrackingEventThenReturnsNull() {
        val requestUrl = "http://safe.com/image.png".toUri()
        val documentUrl = "http://example.com".toUri()
        val request = mockRequest(requestUrl, isForMainFrame = false)

        whenever(mockTrackerDetector.evaluate(any<Uri>(), any(), any(), any()))
            .thenReturn(null)

        val result = interceptor.shouldInterceptRequest(request) { documentUrl }

        assertNull(result)
    }

    @Test
    fun whenRequestIsForMainFrameThenReturnsNull() {
        val requestUrl = "http://tracker.com/page".toUri()
        val documentUrl = "http://example.com".toUri()
        val request = mockRequest(requestUrl, isForMainFrame = true)

        val result = interceptor.shouldInterceptRequest(request) { documentUrl }

        assertNull(result)
    }

    @Test
    fun whenDocumentUrlIsNullThenReturnsNull() {
        val requestUrl = "http://tracker.com/script.js".toUri()
        val request = mockRequest(requestUrl, isForMainFrame = false)

        val result = interceptor.shouldInterceptRequest(request) { null }

        assertNull(result)
    }

    @Test
    fun whenFeatureIsDisabledThenReturnsNull() {
        whenever(mockToggle.isEnabled()).thenReturn(false)

        val requestUrl = "http://tracker.com/script.js".toUri()
        val documentUrl = "http://example.com".toUri()
        val request = mockRequest(requestUrl, isForMainFrame = false)

        whenever(mockTrackerDetector.evaluate(any<Uri>(), any(), any(), any()))
            .thenReturn(blockedTrackingEvent(documentUrl.toString(), requestUrl.toString()))

        val result = interceptor.shouldInterceptRequest(request) { documentUrl }

        assertNull(result)
    }

    @Test
    fun whenFeatureIsDisabledThenDocumentUrlProviderIsNotInvoked() {
        whenever(mockToggle.isEnabled()).thenReturn(false)

        val request = mockRequest("http://tracker.com/script.js".toUri(), isForMainFrame = false)
        var providerInvoked = false

        interceptor.shouldInterceptRequest(request) {
            providerInvoked = true
            "http://example.com".toUri()
        }

        assertFalse(providerInvoked)
    }

    @Test
    fun whenRequestIsForMainFrameThenDocumentUrlProviderIsNotInvoked() {
        val request = mockRequest("http://tracker.com/page".toUri(), isForMainFrame = true)
        var providerInvoked = false

        interceptor.shouldInterceptRequest(request) {
            providerInvoked = true
            "http://example.com".toUri()
        }

        assertFalse(providerInvoked)
    }

    private fun mockRequest(url: Uri, isForMainFrame: Boolean): WebResourceRequest {
        val request: WebResourceRequest = mock()
        whenever(request.url).thenReturn(url)
        whenever(request.isForMainFrame).thenReturn(isForMainFrame)
        whenever(request.requestHeaders).thenReturn(emptyMap())
        return request
    }

    private fun blockedTrackingEvent(documentUrl: String, trackerUrl: String) = TrackingEvent(
        documentUrl = documentUrl,
        trackerUrl = trackerUrl,
        categories = null,
        entity = null,
        surrogateId = null,
        status = TrackerStatus.BLOCKED,
        type = TrackerType.OTHER,
    )

    private fun allowedTrackingEvent(documentUrl: String, trackerUrl: String) = TrackingEvent(
        documentUrl = documentUrl,
        trackerUrl = trackerUrl,
        categories = null,
        entity = null,
        surrogateId = null,
        status = TrackerStatus.ALLOWED,
        type = TrackerType.OTHER,
    )
}
