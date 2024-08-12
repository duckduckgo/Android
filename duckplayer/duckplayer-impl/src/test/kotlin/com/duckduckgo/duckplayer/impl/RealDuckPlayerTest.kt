/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.duckplayer.impl

import android.content.Context
import android.content.res.AssetManager
import android.net.Uri
import android.webkit.MimeTypeMap
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.UrlScheme.Companion.duck
import com.duckduckgo.common.utils.UrlScheme.Companion.https
import com.duckduckgo.duckplayer.api.DuckPlayer.DuckPlayerState.DISABLED
import com.duckduckgo.duckplayer.api.DuckPlayer.DuckPlayerState.DISABLED_WIH_HELP_LINK
import com.duckduckgo.duckplayer.api.DuckPlayer.DuckPlayerState.ENABLED
import com.duckduckgo.duckplayer.api.DuckPlayer.UserPreferences
import com.duckduckgo.duckplayer.api.PrivatePlayerMode.AlwaysAsk
import com.duckduckgo.duckplayer.api.PrivatePlayerMode.Disabled
import com.duckduckgo.duckplayer.api.PrivatePlayerMode.Enabled
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.State
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.never
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RealDuckPlayerTest {

    @get:org.junit.Rule
    var coroutineRule = com.duckduckgo.common.test.CoroutineTestRule()

    private val mockDuckPlayerFeatureRepository: DuckPlayerFeatureRepository =
        mock()
    private val mockDuckPlayerFeature: DuckPlayerFeature = mock()
    private val mockPixel: Pixel = mock()
    private val mockDuckPlayerLocalFilesPath: DuckPlayerLocalFilesPath = mock()
    private val mimeType: MimeTypeMap = mock()
    private val dispatcherProvider = coroutineRule.testDispatcherProvider

    private val testee = RealDuckPlayer(
        mockDuckPlayerFeatureRepository,
        mockDuckPlayerFeature,
        mockPixel,
        mockDuckPlayerLocalFilesPath,
        mimeType,
        dispatcherProvider,
    )

    @Before
    fun setup() = runTest {
        mockFeatureToggle(true)
        whenever(mockDuckPlayerFeatureRepository.getDuckPlayerDisabledHelpPageLink())
            .thenReturn(null)
        whenever(mockDuckPlayerFeatureRepository.getVideoIDQueryParam()).thenReturn("v")
        whenever(mockDuckPlayerFeatureRepository.getYouTubeWatchPath()).thenReturn("watch")
        whenever(mockDuckPlayerFeatureRepository.getYouTubeUrl()).thenReturn("youtube.com")
        whenever(mockDuckPlayerFeatureRepository.getYouTubeEmbedUrl()).thenReturn("youtube-nocookie.com")
        whenever(mockDuckPlayerFeatureRepository.getYouTubeReferrerHeaders()).thenReturn(listOf("Referer"))
        whenever(mockDuckPlayerFeatureRepository.getYouTubeReferrerQueryParams()).thenReturn(listOf("embeds_referring_euri"))
    }

    // region getDuckPlayerState

    @Test
    fun whenDuckPlayerStateIsEnabled_getDuckPlayerStateReturnsEnabled() = runTest {
        mockFeatureToggle(true)

        val result = testee.getDuckPlayerState()

        assertEquals(ENABLED, result)
    }

    @Test
    fun whenDuckPlayerStateIsDisabled_getDuckPlayerStateReturnsDisabled() = runTest {
        mockFeatureToggle(false)

        val result = testee.getDuckPlayerState()

        assertEquals(DISABLED, result)
    }

    @Test
    fun whenDuckPlayerStateIsDisabledWithHelpLink_getDuckPlayerStateReturnsDisabledWithHelpLink() = runTest {
        mockFeatureToggle(false)
        whenever(mockDuckPlayerFeatureRepository.getDuckPlayerDisabledHelpPageLink()).thenReturn("help_link")

        val result = testee.getDuckPlayerState()

        assertEquals(DISABLED_WIH_HELP_LINK, result)
    }

    // endregion

    // region setUserPreferences
    @Test
    fun whenOverlayInteracted_setUserPreferencesUpdatesOverlayInteracted() = runTest {
        testee.setUserPreferences(true, "disabled")

        verify(mockDuckPlayerFeatureRepository).setUserPreferences(UserPreferences(true, Disabled))
    }

    @Test
    fun whenPrivatePlayerModeEnabled_setUserPreferencesUpdatesPrivatePlayerMode() = runTest {
        testee.setUserPreferences(false, "enabled")

        verify(mockDuckPlayerFeatureRepository).setUserPreferences(UserPreferences(false, Enabled))
    }

    @Test
    fun whenPrivatePlayerModeAlwaysAsk_setUserPreferencesUpdatesPrivatePlayerMode() = runTest {
        testee.setUserPreferences(false, "always_ask")

        verify(mockDuckPlayerFeatureRepository).setUserPreferences(UserPreferences(false, AlwaysAsk))
    }

    //endregion

    //region getUserPreferences

    @Test
    fun whenGetUserPreferencesCalled_returnsUserPreferencesFromRepository() = runTest {
        val expectedUserPreferences = UserPreferences(true, Enabled)
        whenever(mockDuckPlayerFeatureRepository.getUserPreferences()).thenReturn(expectedUserPreferences)

        val actualUserPreferences = testee.getUserPreferences()

        assertEquals(expectedUserPreferences, actualUserPreferences)
    }

    @Test
    fun whenGetUserPreferencesCalled_returnsUserPreferencesWithOverlayInteracted() = runTest {
        whenever(mockDuckPlayerFeatureRepository.getUserPreferences()).thenReturn(UserPreferences(true, Disabled))

        val result = testee.getUserPreferences()

        assertTrue(result.overlayInteracted)
    }

    @Test
    fun whenGetUserPreferencesCalled_returnsUserPreferencesWithPrivatePlayerMode() = runTest {
        whenever(mockDuckPlayerFeatureRepository.getUserPreferences()).thenReturn(UserPreferences(false, AlwaysAsk))

        val result = testee.getUserPreferences()

        assertEquals(AlwaysAsk, result.privatePlayerMode)
    }

    // endregion

    // region shouldHideDuckPlayerOverlay

    @Test
    fun whenOverlayHidden_shouldHideDuckPlayerOverlayReturnsFalse() {
        testee.duckPlayerOverlayHidden()

        assertFalse(testee.shouldHideDuckPlayerOverlay())
    }

    @Test
    fun whenOverlayNotHidden_shouldHideDuckPlayerOverlayReturnsFalse() {
        assertFalse(testee.shouldHideDuckPlayerOverlay())
    }

    // endregion

    // region observeUserPreferences
    @Test
    fun observeUserPreferences_emitsUserPreferencesFromRepository() = runTest {
        val expectedUserPreferences = UserPreferences(true, Enabled)
        whenever(mockDuckPlayerFeatureRepository.observeUserPreferences()).thenReturn(flowOf(expectedUserPreferences))

        val result = testee.observeUserPreferences().first()

        assertEquals(expectedUserPreferences, result)
    }

    @Test
    fun observeUserPreferences_emitsMultipleUserPreferencesFromRepository() = runTest {
        val userPreferences1 = UserPreferences(true, Enabled)
        val userPreferences2 = UserPreferences(false, Disabled)
        whenever(mockDuckPlayerFeatureRepository.observeUserPreferences()).thenReturn(flowOf(userPreferences1, userPreferences2))

        val results = testee.observeUserPreferences().take(2).toList()

        assertEquals(userPreferences1, results[0])
        assertEquals(userPreferences2, results[1])
    }

    // endregion

    // region sendDuckPlayerPixel

    @Test
    fun sendDuckPlayerPixel_firesPixelWithCorrectNameAndData() = runTest {
        val pixelName = "pixelName"
        val pixelData = mapOf("key" to "value")

        testee.sendDuckPlayerPixel(pixelName, pixelData)

        verify(mockPixel).fire("m_pixelName", pixelData)
    }

    @Test
    fun sendDuckPlayerPixel_firesPixelWithEmptyDataWhenNoDataProvided() = runTest {
        val pixelName = "pixelName"

        testee.sendDuckPlayerPixel(pixelName, emptyMap())

        verify(mockPixel).fire("m_pixelName", emptyMap())
    }

    // endregion

    // region createYouTubeWatchFromDuckPlayer

    @Test
    fun createYoutubeWatchUrlFromDuckPlayer_returnsCorrectUrl_whenVideoIdQueryParamExists() = runTest {
        val youTubeWatchPath = "watch"
        val youTubeHost = "youtube.com"
        val videoID = "12345"
        val uri = Uri.parse("$duck://player/$videoID")

        val result = testee.createYoutubeWatchUrlFromDuckPlayer(uri)

        assertEquals("$https://$youTubeHost/$youTubeWatchPath?v=$videoID", result)
    }

    @Test
    fun createYoutubeWatchUrlFromDuckPlayer_returnsNull_whenNoVideoIdFound() = runTest {
        val youTubeWatchPath = "watch"
        val youTubeHost = "youtube.com"
        val uri = Uri.parse("$https://$youTubeHost/$youTubeWatchPath")

        val result = testee.createYoutubeWatchUrlFromDuckPlayer(uri)

        assertNull(result)
    }

    //endregion

    // region isDuckPlayerUrl

    @Test
    fun whenUriIsDuckPlayerUri_isDuckPlayerUriReturnsTrue() = runTest {
        val uri = "duck://player/12345"

        val result = testee.isDuckPlayerUri(uri)

        assertTrue(result)
    }

    @Test
    fun whenUriIsNotDuckPlayerUri_isDuckPlayerUriReturnsFalse() = runTest {
        val uri = "https://youtube.com/watch?v=12345"

        val result = testee.isDuckPlayerUri(uri)

        assertFalse(result)
    }

    @Test
    fun whenUriIsEmpty_isDuckPlayerUriReturnsFalse() = runTest {
        val uri = ""

        val result = testee.isDuckPlayerUri(uri)

        assertFalse(result)
    }

    // endregion

    // region isSimulatedYoutubeNoCookie

    @Test
    fun whenUriHostYouTube_isSimulatedYoutubeNoCookieReturnsTrue() = runTest {
        val uri = "https://www.youtube.com".toUri()

        val result = testee.isSimulatedYoutubeNoCookie(uri)

        assertFalse(result)
    }

    @Test
    fun whenUriHostIsEmbedUrlAndPathContainsValidPath_isSimulatedYoutubeNoCookieReturnsTrue() = runTest {
        val uri = "https://www.youtube-nocookie.com/?videoID=1234".toUri()

        val result = testee.isSimulatedYoutubeNoCookie(uri)

        assertTrue(result)
    }

    @Test
    fun whenUrHostIsEmbedAndFileIsAvailableLocally_isSimulatedYoutubeNoCookieReturnsTrue() = runTest {
        whenever(mockDuckPlayerLocalFilesPath.assetsPath).thenReturn(listOf("js/duckplayer.js"))
        val uri = "https://www.youtube-nocookie.com/js/duckplayer.js".toUri()

        val result = testee.isSimulatedYoutubeNoCookie(uri)

        assertTrue(result)
    }

    @Test
    fun whenUrHostIsEmbedAndFileIsNotAvailableLocally_isSimulatedYoutubeNoCookieReturnsFalse() = runTest {
        whenever(mockDuckPlayerLocalFilesPath.assetsPath).thenReturn(listOf("css/duckplayer.css"))
        val uri = "https://www.youtube-nocookie.com/js/duckplayer.js".toUri()

        val result = testee.isSimulatedYoutubeNoCookie(uri)

        assertFalse(result)
    }

    @Test
    fun whenUrHostIsEmbedAndPathContainsEmbed_isSimulatedYoutubeNoCookieReturnsFalse() = runTest {
        whenever(mockDuckPlayerLocalFilesPath.assetsPath).thenReturn(listOf())
        val uri = "https://www.youtube-nocookie.com/embed/js/duckplayer.js".toUri()

        val result = testee.isSimulatedYoutubeNoCookie(uri)

        assertFalse(result)
    }

    @Test
    fun whenUriHostIsNotEmbedUrl_isSimulatedYoutubeNoCookieReturnsFalse() = runTest {
        val uri = "https://www.notyoutube.com".toUri()

        val result = testee.isSimulatedYoutubeNoCookie(uri)

        assertFalse(result)
    }

    @Test
    fun whenUriHostIsEmbedUrlAndPathContainsEmbed_isSimulatedYoutubeNoCookieReturnsFalse() = runTest {
        val uri = "https://www.youtube.com/embed/12345".toUri()

        val result = testee.isSimulatedYoutubeNoCookie(uri)

        assertFalse(result)
    }

    @Test
    fun whenUriHostIsEmbedUrlAndPathDoesNotContainValidPath_isSimulatedYoutubeNoCookieReturnsFalse() = runTest {
        val uri = "https://www.youtube.com/invalidPath".toUri()

        val result = testee.isSimulatedYoutubeNoCookie(uri)

        assertFalse(result)
    }

    @Test
    fun whenUriHostIsEmbedUrlAndPathDoesNotHaveVideoIdQueryParam_isSimulatedYoutubeNoCookieReturnsFalse() = runTest {
        val uri = "https://www.youtube.com/watch".toUri()

        val result = testee.isSimulatedYoutubeNoCookie(uri)

        assertFalse(result)
    }

    // endregion

    // region isYouTubeWatchUrl

    @Test
    fun whenUriHostIsYoutubeAndPathIsWatch_isYoutubeWatchUrlReturnsTrue() = runTest {
        val uri = "https://www.youtube.com/watch?v=12345".toUri()

        val result = testee.isYoutubeWatchUrl(uri)

        assertTrue(result)
    }

    @Test
    fun whenUriHostIsMobileYoutubeAndPathIsWatch_isYoutubeWatchUrlReturnsTrue() = runTest {
        val uri = "https://m.youtube.com/watch?v=12345".toUri()

        val result = testee.isYoutubeWatchUrl(uri)

        assertTrue(result)
    }

    @Test
    fun whenUriHostIsNotYoutube_isYoutubeWatchUrlReturnsFalse() = runTest {
        val uri = "https://www.notyoutube.com/watch?v=12345".toUri()

        val result = testee.isYoutubeWatchUrl(uri)

        assertFalse(result)
    }

    @Test
    fun whenUriPathIsNotWatch_isYoutubeWatchUrlReturnsFalse() = runTest {
        val uri = "https://www.youtube.com/notwatch?v=12345".toUri()

        val result = testee.isYoutubeWatchUrl(uri)

        assertFalse(result)
    }

    // endregion

    // region createDuckPlayerUriFromYoutubeNoCookie

    @Test
    fun whenUriHasVideoIdQueryParam_createDuckPlayerUriFromYoutubeNoCookieReturnsCorrectUri() = runTest {
        val uri = "https://www.youtube-nocookie.com/?videoID=12345".toUri()

        val result = testee.createDuckPlayerUriFromYoutubeNoCookie(uri)

        assertEquals("$duck://player/12345", result)
    }

    @Test
    fun whenUriDoesNotHaveVideoIdQueryParam_createDuckPlayerUriFromYoutubeNoCookieReturnsNull() = runTest {
        val uri = "https://www.youtube-nocookie.com/".toUri()

        val result = testee.createDuckPlayerUriFromYoutubeNoCookie(uri)

        assertNull(result)
    }

    @Test
    fun whenUriIsEmpty_createDuckPlayerUriFromYoutubeNoCookieReturnsNull() = runTest {
        val uri = "".toUri()

        val result = testee.createDuckPlayerUriFromYoutubeNoCookie(uri)

        assertNull(result)
    }

    // endregion

    // region intercept

    @Test
    fun whenDuckPlayerStateIsNotEnabled_interceptReturnsNull() = runTest {
        val request: WebResourceRequest = mock()
        val url: Uri = mock()
        val webView: WebView = mock()

        mockFeatureToggle(false)

        val result = testee.intercept(request, url, webView)

        assertNull(result)
    }

    @Test
    fun whenUrlDoesNotMatchAnyCondition_interceptReturnsNull() = runTest {
        val request: WebResourceRequest = mock()
        val url: Uri = Uri.parse("https://www.notmatching.com")
        val webView: WebView = mock()

        mockFeatureToggle(true)

        val result = testee.intercept(request, url, webView)

        assertNull(result)
    }

    @Test
    fun whenUriIsDuckPlayerUri_interceptProcessesDuckPlayerUri() = runTest {
        val request: WebResourceRequest = mock()
        val url: Uri = Uri.parse("duck://player/12345")
        val webView: WebView = mock()

        val result = testee.intercept(request, url, webView)

        verify(webView).loadUrl("https://www.youtube-nocookie.com?videoID=12345")
        assertNotNull(result)
    }

    @Test
    fun whenUriIsDuckPlayerUriAndFeatureIsDisabled_doNothing() = runTest {
        mockFeatureToggle(false)
        val request: WebResourceRequest = mock()
        val url: Uri = Uri.parse("duck://player/12345")
        val webView: WebView = mock()

        val result = testee.intercept(request, url, webView)

        verify(webView, never()).loadUrl(any())
        assertNull(result)
    }

    @Test
    fun whenUriIsYouTubeEmbed_interceptLoadsLocalFile() = runTest {
        val request: WebResourceRequest = mock()
        val url: Uri = Uri.parse("https://www.youtube-nocookie.com?videoID=12345")
        val webView: WebView = mock()
        val context: Context = mock()
        val assets: AssetManager = mock()
        whenever(webView.context).thenReturn(context)
        whenever(context.assets).thenReturn(assets)
        whenever(assets.open(any())).thenReturn(mock())

        val result = testee.intercept(request, url, webView)

        verify(assets).open("duckplayer/index.html")
        assertEquals("text/html", result?.mimeType)
    }

    @Test
    fun whenUriIsYouTubeEmbedAndFeatureDisabled_doNothing() = runTest {
        mockFeatureToggle(false)
        val request: WebResourceRequest = mock()
        val url: Uri = Uri.parse("https://www.youtube-nocookie.com?videoID=12345")
        val webView: WebView = mock()

        val result = testee.intercept(request, url, webView)

        assertNull(result)
    }

    @Test
    fun whenUriIsYoutubeWatchUrl_interceptProcessesYoutubeWatchUri() = runTest {
        val request: WebResourceRequest = mock()
        val url: Uri = Uri.parse("https://www.youtube.com/watch?v=12345")
        val webView: WebView = mock()
        whenever(mockDuckPlayerFeatureRepository.getUserPreferences()).thenReturn(UserPreferences(true, Enabled))

        val result = testee.intercept(request, url, webView)

        verify(webView).loadUrl("duck://player/12345")
        assertNotNull(result)
    }

    @Test
    fun whenUriIsYoutubeWatchUrlAndSettingsAlwaysAsk_interceptProcessesYoutubeWatchUri() = runTest {
        val request: WebResourceRequest = mock()
        val url: Uri = Uri.parse("https://www.youtube.com/watch?v=12345")
        val webView: WebView = mock()
        whenever(mockDuckPlayerFeatureRepository.getUserPreferences()).thenReturn(UserPreferences(true, AlwaysAsk))

        val result = testee.intercept(request, url, webView)

        verify(webView, never()).loadUrl(any())
        assertNull(result)
    }

    // endregion

    private fun mockFeatureToggle(enabled: Boolean) {
        whenever(mockDuckPlayerFeature.self()).thenReturn(object : Toggle {
            override fun isEnabled() = enabled

            override fun setEnabled(state: State) {
                TODO("Not yet implemented")
            }

            override fun getRawStoredState(): State? {
                TODO("Not yet implemented")
            }
        },)
    }
}
