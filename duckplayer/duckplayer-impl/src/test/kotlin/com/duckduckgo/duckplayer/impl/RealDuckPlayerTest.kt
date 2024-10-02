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
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Count
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.common.utils.UrlScheme.Companion.duck
import com.duckduckgo.common.utils.UrlScheme.Companion.https
import com.duckduckgo.duckplayer.api.DuckPlayer.DuckPlayerState.DISABLED
import com.duckduckgo.duckplayer.api.DuckPlayer.DuckPlayerState.DISABLED_WIH_HELP_LINK
import com.duckduckgo.duckplayer.api.DuckPlayer.DuckPlayerState.ENABLED
import com.duckduckgo.duckplayer.api.DuckPlayer.UserPreferences
import com.duckduckgo.duckplayer.api.PrivatePlayerMode.AlwaysAsk
import com.duckduckgo.duckplayer.api.PrivatePlayerMode.Disabled
import com.duckduckgo.duckplayer.api.PrivatePlayerMode.Enabled
import com.duckduckgo.duckplayer.impl.DuckPlayerPixelName.DUCK_PLAYER_DAILY_UNIQUE_VIEW
import com.duckduckgo.duckplayer.impl.DuckPlayerPixelName.DUCK_PLAYER_OVERLAY_YOUTUBE_IMPRESSIONS
import com.duckduckgo.duckplayer.impl.DuckPlayerPixelName.DUCK_PLAYER_OVERLAY_YOUTUBE_WATCH_HERE
import com.duckduckgo.duckplayer.impl.DuckPlayerPixelName.DUCK_PLAYER_VIEW_FROM_OTHER
import com.duckduckgo.duckplayer.impl.DuckPlayerPixelName.DUCK_PLAYER_VIEW_FROM_YOUTUBE_AUTOMATIC
import com.duckduckgo.duckplayer.impl.DuckPlayerPixelName.DUCK_PLAYER_VIEW_FROM_YOUTUBE_MAIN_OVERLAY
import com.duckduckgo.duckplayer.impl.DuckPlayerPixelName.DUCK_PLAYER_WATCH_ON_YOUTUBE
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
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
    private val duckPlayerFeature = FakeFeatureToggleFactory.create(DuckPlayerFeature::class.java)
    private val mockPixel: Pixel = mock()
    private val mockDuckPlayerLocalFilesPath: DuckPlayerLocalFilesPath = mock()
    private val mimeType: MimeTypeMap = mock()
    private val dispatcherProvider = coroutineRule.testDispatcherProvider

    private val testee = RealDuckPlayer(
        mockDuckPlayerFeatureRepository,
        duckPlayerFeature,
        mockPixel,
        mockDuckPlayerLocalFilesPath,
        mimeType,
        dispatcherProvider,
    )

    @Before
    fun setup() = runTest {
        setFeatureToggle(true)
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
        setFeatureToggle(true)

        val result = testee.getDuckPlayerState()

        assertEquals(ENABLED, result)
    }

    @Test
    fun whenDuckPlayerStateIsDisabled_getDuckPlayerStateReturnsDisabled() = runTest {
        setFeatureToggle(false)

        val result = testee.getDuckPlayerState()

        assertEquals(DISABLED, result)
    }

    @Test
    fun whenDuckPlayerStateIsDisabledWithHelpLink_getDuckPlayerStateReturnsDisabledWithHelpLink() = runTest {
        setFeatureToggle(false)
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
    fun sendDuckPlayerPixelWithOverlay_firesPixelWithCorrectNameAndData() = runTest {
        val pixelName = "overlay"
        val pixelData = mapOf("key" to "value")

        testee.sendDuckPlayerPixel(pixelName, pixelData)

        verify(mockPixel).fire(DUCK_PLAYER_OVERLAY_YOUTUBE_IMPRESSIONS, pixelData, emptyMap(), Count)
    }

    @Test
    fun sendDuckPlayerPixelWithOverlay_firesPixelWithEmptyDataWhenNoDataProvided() = runTest {
        val pixelName = "overlay"

        testee.sendDuckPlayerPixel(pixelName, emptyMap())

        verify(mockPixel).fire(DUCK_PLAYER_OVERLAY_YOUTUBE_IMPRESSIONS, emptyMap(), emptyMap(), Count)
    }

    @Test
    fun sendDuckPlayerPixelWithPlayUse_firesPixelWithCorrectNameAndData() = runTest {
        val pixelName = "play.use"
        val pixelData = mapOf("key" to "value")

        testee.sendDuckPlayerPixel(pixelName, pixelData)

        verify(mockPixel).fire(DUCK_PLAYER_VIEW_FROM_YOUTUBE_MAIN_OVERLAY, pixelData, emptyMap(), Count)
    }

    @Test
    fun sendDuckPlayerPixelWithPlayUse_firesPixelWithEmptyDataWhenNoDataProvided() = runTest {
        val pixelName = "play.use"

        testee.sendDuckPlayerPixel(pixelName, emptyMap())

        verify(mockPixel).fire(DUCK_PLAYER_VIEW_FROM_YOUTUBE_MAIN_OVERLAY, emptyMap(), emptyMap(), Count)
    }

    @Test
    fun sendDuckPlayerPixelWithPlayDoNotUse_firesPixelWithCorrectNameAndData() = runTest {
        val pixelName = "play.do_not_use"
        val pixelData = mapOf("key" to "value")

        testee.sendDuckPlayerPixel(pixelName, pixelData)

        verify(mockPixel).fire(DUCK_PLAYER_OVERLAY_YOUTUBE_WATCH_HERE, pixelData, emptyMap(), Count)
    }

    @Test
    fun sendDuckPlayerPixelWithPlayDoNotUse_firesPixelWithEmptyDataWhenNoDataProvided() = runTest {
        val pixelName = "play.do_not_use"

        testee.sendDuckPlayerPixel(pixelName, emptyMap())

        verify(mockPixel).fire(DUCK_PLAYER_OVERLAY_YOUTUBE_WATCH_HERE, emptyMap(), emptyMap(), Count)
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

        setFeatureToggle(false)

        val result = testee.intercept(request, url, webView)

        assertNull(result)
    }

    @Test
    fun whenUrlDoesNotMatchAnyCondition_interceptReturnsNull() = runTest {
        val request: WebResourceRequest = mock()
        val url: Uri = Uri.parse("https://www.notmatching.com")
        val webView: WebView = mock()

        setFeatureToggle(true)

        val result = testee.intercept(request, url, webView)

        assertNull(result)
    }

    @Test
    fun whenUriIsDuckPlayerUri_interceptProcessesDuckPlayerUri() = runTest {
        val request: WebResourceRequest = mock()
        val url: Uri = Uri.parse("duck://player/12345")
        val webView: WebView = mock()
        whenever(mockDuckPlayerFeatureRepository.getUserPreferences()).thenReturn(UserPreferences(true, Enabled))

        val result = testee.intercept(request, url, webView)

        verify(webView).loadUrl("https://www.youtube-nocookie.com?videoID=12345")
        verify(mockPixel).fire(DUCK_PLAYER_VIEW_FROM_OTHER)
        assertNotNull(result)
    }

    @Test
    fun whenUriIsDuckPlayerUriWithOpenInYouTube_interceptLoadsYouTubeUri() = runTest {
        val request: WebResourceRequest = mock()
        val url: Uri = Uri.parse("duck://player/openInYouTube?v=12345")
        val webView: WebView = mock()
        whenever(mockDuckPlayerFeatureRepository.getUserPreferences()).thenReturn(UserPreferences(true, Enabled))

        val result = testee.intercept(request, url, webView)

        verify(webView).loadUrl("https://youtube.com/watch?v=12345")
        verify(mockPixel).fire(DUCK_PLAYER_WATCH_ON_YOUTUBE)
        assertNotNull(result)
    }

    @Test
    fun whenUriIsDuckPlayerUriAndFeatureIsDisabled_InterceptLoadsYouTubeUri() = runTest {
        setFeatureToggle(false)
        val request: WebResourceRequest = mock()
        val url: Uri = Uri.parse("duck://player/12345")
        val webView: WebView = mock()
        whenever(mockDuckPlayerFeatureRepository.getUserPreferences()).thenReturn(UserPreferences(true, Enabled))

        val result = testee.intercept(request, url, webView)

        verify(webView).loadUrl("https://youtube.com/watch?v=12345")
        verify(mockPixel, never()).fire(DUCK_PLAYER_WATCH_ON_YOUTUBE)
        assertNotNull(result)
    }

    @Test
    fun whenUriIsDuckPlayerUriAndUserSettingsNever_InterceptLoadsYouTubeUri() = runTest {
        setFeatureToggle(true)
        val request: WebResourceRequest = mock()
        val url: Uri = Uri.parse("duck://player/12345")
        val webView: WebView = mock()
        whenever(mockDuckPlayerFeatureRepository.getUserPreferences()).thenReturn(UserPreferences(true, Disabled))

        val result = testee.intercept(request, url, webView)

        verify(webView).loadUrl("https://youtube.com/watch?v=12345")
        verify(mockPixel, never()).fire(DUCK_PLAYER_WATCH_ON_YOUTUBE)
        assertNotNull(result)
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
        verify(mockPixel).fire(DUCK_PLAYER_DAILY_UNIQUE_VIEW, type = Daily())
        assertEquals("text/html", result?.mimeType)
    }

    @Test
    fun whenUriIsYouTubeEmbedAndFeatureDisabled_doNothing() = runTest {
        setFeatureToggle(false)
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

        verify(webView).loadUrl("duck://player/12345?origin=auto")
        verify(mockPixel).fire(DUCK_PLAYER_VIEW_FROM_YOUTUBE_AUTOMATIC)
        assertNotNull(result)
    }

    @Test
    fun whenUriIsYoutubeWatchUrlAndPreviousUrlIsDuckPlayer_interceptReturnsNull() = runTest {
        val request: WebResourceRequest = mock()
        val url: Uri = Uri.parse("https://www.youtube.com/watch?v=12345")
        val webView: WebView = mock()
        whenever(mockDuckPlayerFeatureRepository.getUserPreferences()).thenReturn(UserPreferences(true, Enabled))
        whenever(webView.url).thenReturn("https://www.youtube-nocookie.com?videoID=12345")

        val result = testee.intercept(request, url, webView)

        assertNull(result)
    }

    @Test
    fun whenUriIsYoutubeWatchUrlWithRefererAndPreviousUrlIsDuckPlayer_interceptReturnsNull() = runTest {
        val request: WebResourceRequest = mock()
        val url: Uri = Uri.parse("https://www.youtube.com/watch?v=12345")
        whenever(request.requestHeaders).thenReturn(mapOf("Referer" to "https://www.youtube-nocookie.com?videoID=12345"))
        whenever(mockDuckPlayerFeatureRepository.getYouTubeReferrerHeaders()).thenReturn(listOf("Referer"))
        val webView: WebView = mock()
        whenever(mockDuckPlayerFeatureRepository.getUserPreferences()).thenReturn(UserPreferences(true, Enabled))
        whenever(webView.url).thenReturn("https://www.youtube-nocookie.com?videoID=12345")

        val result = testee.intercept(request, url, webView)

        assertNull(result)
    }

    @Test
    fun whenUriIsYoutubeWatchUrlAndPreviousUrlIsDuckPlayerWithDifferentId_interceptOpensDuckPlayer() = runTest {
        val request: WebResourceRequest = mock()
        val url: Uri = Uri.parse("https://www.youtube.com/watch?v=123456")
        val webView: WebView = mock()
        whenever(mockDuckPlayerFeatureRepository.getUserPreferences()).thenReturn(UserPreferences(true, Enabled))
        whenever(webView.url).thenReturn("https://www.youtube-nocookie.com?videoID=12345")

        val result = testee.intercept(request, url, webView)

        verify(webView).loadUrl("duck://player/123456?origin=auto")
        verify(mockPixel).fire(DUCK_PLAYER_VIEW_FROM_YOUTUBE_AUTOMATIC)
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

    // region willNavigateToDuckPlayer

    @Test
    fun whenWillNavigateToDuckPlayerWithYouTubeWatchUrlSettingEnabledAndFeatureEnabled_returnTrue() = runTest {
        val uri = "https://www.youtube.com/watch?v=12345".toUri()
        whenever(mockDuckPlayerFeatureRepository.getUserPreferences()).thenReturn(UserPreferences(true, Enabled))

        val result = testee.willNavigateToDuckPlayer(uri)

        assertTrue(result)
    }

    @Test
    fun whenWillNavigateToDuckPlayerWithYouTubeWatchUrlSettingDisabledAndFeatureEnabled_returnFalse() = runTest {
        val uri = "https://www.youtube.com/watch?v=12345".toUri()
        whenever(mockDuckPlayerFeatureRepository.getUserPreferences()).thenReturn(UserPreferences(true, Disabled))

        val result = testee.willNavigateToDuckPlayer(uri)

        assertFalse(result)
    }

    @Test
    fun whenWillNavigateToDuckPlayerWithYouTubeRootUrlSettingEnableddAndFeatureEnabled_returnFalse() = runTest {
        val uri = "https://www.youtube.com".toUri()
        whenever(mockDuckPlayerFeatureRepository.getUserPreferences()).thenReturn(UserPreferences(true, Enabled))

        val result = testee.willNavigateToDuckPlayer(uri)

        assertFalse(result)
    }

    @Test
    fun whenWillNavigateToDuckPlayerWithNonYouTubeUrlSettingEnabledAndFeatureEnabled_returnFalse() = runTest {
        val uri = "https://example.com".toUri()
        whenever(mockDuckPlayerFeatureRepository.getUserPreferences()).thenReturn(UserPreferences(true, Enabled))

        val result = testee.willNavigateToDuckPlayer(uri)

        assertFalse(result)
    }

    // endregion

    private fun setFeatureToggle(enabled: Boolean) {
        duckPlayerFeature.self().setRawStoredState(State(enabled))
        duckPlayerFeature.enableDuckPlayer().setRawStoredState(State(enabled))
    }
}
