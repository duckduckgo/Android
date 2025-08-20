/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.app.browser

import android.content.Intent
import android.content.Intent.URI_ANDROID_APP_SCHEME
import android.content.Intent.URI_INTENT_SCHEME
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.SpecialUrlDetector.UrlType.*
import com.duckduckgo.app.browser.SpecialUrlDetectorImpl.Companion.EMAIL_MAX_LENGTH
import com.duckduckgo.app.browser.SpecialUrlDetectorImpl.Companion.PHONE_MAX_LENGTH
import com.duckduckgo.app.browser.SpecialUrlDetectorImpl.Companion.SMS_MAX_LENGTH
import com.duckduckgo.app.browser.applinks.ExternalAppIntentFlagsFeature
import com.duckduckgo.app.browser.duckchat.AIChatQueryDetectionFeature
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckplayer.api.DuckPlayer
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.privacy.config.api.AmpLinkType
import com.duckduckgo.privacy.config.api.AmpLinks
import com.duckduckgo.privacy.config.api.TrackingParameters
import com.duckduckgo.subscriptions.api.Subscriptions
import java.net.URISyntaxException
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.*

@RunWith(AndroidJUnit4::class)
class SpecialUrlDetectorImplTest {

    lateinit var testee: SpecialUrlDetectorImpl

    val mockPackageManager: PackageManager = mock()

    val mockAmpLinks: AmpLinks = mock()

    val mockTrackingParameters: TrackingParameters = mock()

    val subscriptions: Subscriptions = mock()

    val externalAppIntentFlagsFeature: ExternalAppIntentFlagsFeature =
        FakeFeatureToggleFactory.create(ExternalAppIntentFlagsFeature::class.java)

    val mockDuckPlayer: DuckPlayer = mock()

    val mockDuckChat: DuckChat = mock()

    val mockAIChatQueryDetectionFeature: AIChatQueryDetectionFeature = mock()

    val mockAIChatQueryDetectionFeatureToggle: Toggle = mock()

    val androidBrowserConfigFeature: AndroidBrowserConfigFeature = FakeFeatureToggleFactory.create(AndroidBrowserConfigFeature::class.java)

    @Before
    fun setup() = runTest {
        testee = spy(
            SpecialUrlDetectorImpl(
                packageManager = mockPackageManager,
                ampLinks = mockAmpLinks,
                trackingParameters = mockTrackingParameters,
                subscriptions = subscriptions,
                externalAppIntentFlagsFeature = externalAppIntentFlagsFeature,
                duckPlayer = mockDuckPlayer,
                duckChat = mockDuckChat,
                aiChatQueryDetectionFeature = mockAIChatQueryDetectionFeature,
                androidBrowserConfigFeature = androidBrowserConfigFeature,
            ),
        )
        whenever(mockPackageManager.queryIntentActivities(any(), anyInt())).thenReturn(emptyList())
        whenever(mockDuckPlayer.willNavigateToDuckPlayer(any())).thenReturn(false)
        whenever(mockAIChatQueryDetectionFeatureToggle.isEnabled()).thenReturn(false)
        whenever(mockAIChatQueryDetectionFeature.self()).thenReturn(mockAIChatQueryDetectionFeatureToggle)
        androidBrowserConfigFeature.handleIntentScheme().setRawStoredState(State(true))
    }

    @Test
    fun whenUrlIsHttpThenWebTypeDetected() {
        val expected = Web::class
        val actual = testee.determineType("http://example.com")
        assertEquals(expected, actual::class)
    }

    @Test
    fun whenUrlIsHttpThenWebAddressInData() {
        val type: Web = testee.determineType("http://example.com") as Web
        assertEquals("http://example.com", type.webAddress)
    }

    @Test
    fun whenUrlIsHttpsThenWebTypeDetected() {
        val expected = Web::class
        val actual = testee.determineType("https://example.com")
        assertEquals(expected, actual::class)
    }

    @Test
    fun whenUrlIsHttpsThenSchemePreserved() {
        val type = testee.determineType("https://example.com") as Web
        assertEquals("https://example.com", type.webAddress)
    }

    @Test
    fun whenNoNonBrowserActivitiesFoundThenReturnWebType() {
        whenever(mockPackageManager.queryIntentActivities(any(), anyInt())).thenReturn(listOf(buildBrowserResolveInfo()))
        val type = testee.determineType("https://example.com")
        assertTrue(type is Web)
    }

    @Test
    fun whenAppLinkThrowsURISyntaxExceptionThenReturnWebType() {
        given(mockPackageManager.queryIntentActivities(any(), anyInt())).willAnswer { throw URISyntaxException("", "") }
        val type = testee.determineType("https://example.com")
        assertTrue(type is Web)
    }

    @Test
    fun whenDefaultNonBrowserActivityFoundThenReturnAppLinkWithIntent() {
        whenever(mockPackageManager.resolveActivity(any(), eq(PackageManager.MATCH_DEFAULT_ONLY))).thenReturn(buildAppResolveInfo())
        whenever(mockPackageManager.queryIntentActivities(any(), anyInt())).thenReturn(
            listOf(
                buildBrowserResolveInfo(),
                buildAppResolveInfo(),
                ResolveInfo(),
            ),
        )
        val type = testee.determineType("https://example.com")
        verify(mockPackageManager).queryIntentActivities(
            argThat { hasCategory(Intent.CATEGORY_BROWSABLE) },
            eq(PackageManager.GET_RESOLVED_FILTER),
        )
        assertTrue(type is AppLink)
        val appLinkType = type as AppLink
        assertEquals("https://example.com", appLinkType.uriString)
        assertEquals(EXAMPLE_APP_PACKAGE, appLinkType.appIntent!!.component!!.packageName)
        assertEquals(EXAMPLE_APP_ACTIVITY_NAME, appLinkType.appIntent!!.component!!.className)
    }

    @Test
    fun whenFirstNonBrowserActivityFoundThenReturnAppLinkWithIntent() {
        whenever(mockPackageManager.resolveActivity(any(), eq(PackageManager.MATCH_DEFAULT_ONLY))).thenReturn(null)
        whenever(mockPackageManager.queryIntentActivities(any(), anyInt())).thenReturn(
            listOf(
                buildAppResolveInfo(),
                buildBrowserResolveInfo(),
                ResolveInfo(),
            ),
        )
        val type = testee.determineType("https://example.com")
        verify(mockPackageManager).queryIntentActivities(
            argThat { hasCategory(Intent.CATEGORY_BROWSABLE) },
            eq(PackageManager.GET_RESOLVED_FILTER),
        )
        assertTrue(type is AppLink)
        val appLinkType = type as AppLink
        assertEquals("https://example.com", appLinkType.uriString)
        assertEquals(EXAMPLE_APP_PACKAGE, appLinkType.appIntent!!.component!!.packageName)
        assertEquals(EXAMPLE_APP_ACTIVITY_NAME, appLinkType.appIntent!!.component!!.className)
    }

    @Test
    fun whenWillNavigateToDuckPlayerThenReturnShouldLaunchDuckPlayerLink() = runTest {
        whenever(mockDuckPlayer.willNavigateToDuckPlayer(any())).thenReturn(true)
        val type = testee.determineType("https://example.com")
        whenever(mockPackageManager.resolveActivity(any(), eq(PackageManager.MATCH_DEFAULT_ONLY))).thenReturn(null)
        whenever(mockPackageManager.queryIntentActivities(any(), anyInt())).thenReturn(
            listOf(
                buildAppResolveInfo(),
                buildBrowserResolveInfo(),
                ResolveInfo(),
            ),
        )
        assertTrue(type is ShouldLaunchDuckPlayerLink)
    }

    @Test
    fun whenNoNonBrowserActivityFoundThenReturnWebType() {
        whenever(mockPackageManager.resolveActivity(any(), eq(PackageManager.MATCH_DEFAULT_ONLY))).thenReturn(null)
        whenever(mockPackageManager.queryIntentActivities(any(), anyInt())).thenReturn(
            listOf(
                buildBrowserResolveInfo(),
                buildAppResolveInfo(),
                ResolveInfo(),
            ),
        )
        val type = testee.determineType("https://example.com")
        verify(mockPackageManager).queryIntentActivities(
            argThat { hasCategory(Intent.CATEGORY_BROWSABLE) },
            eq(PackageManager.GET_RESOLVED_FILTER),
        )
        assertTrue(type is Web)
    }

    @Test
    fun whenUrlIsTelWithDashesThenTelephoneTypeDetected() {
        val expected = Telephone::class
        val actual = testee.determineType("tel:+123-555-12323")
        assertEquals(expected, actual::class)
    }

    @Test
    fun whenUrlIsTelThenTelephoneTypeDetected() {
        val expected = Telephone::class
        val actual = testee.determineType("tel:12355512323")
        assertEquals(expected, actual::class)
    }

    @Test
    fun whenUrlIsTelThenSchemeRemoved() {
        val type = testee.determineType("tel:+123-555-12323") as Telephone
        assertEquals("+123-555-12323", type.telephoneNumber)
    }

    @Test
    fun whenUrlIsTelpromptThenTelephoneTypeDetected() {
        val expected = Telephone::class
        val actual = testee.determineType("telprompt:12355512323")
        assertEquals(expected, actual::class)
    }

    @Test
    fun whenUrlIsTelpromptThenSchemeRemoved() {
        val type = testee.determineType("telprompt:123-555-12323") as Telephone
        assertEquals("123-555-12323", type.telephoneNumber)
    }

    @Test
    fun whenUrlIsMailtoThenEmailTypeDetected() {
        val expected = Email::class
        val actual = testee.determineType("mailto:foo@example.com")
        assertEquals(expected, actual::class)
    }

    @Test
    fun whenUrlIsMailtoThenSchemePreserved() {
        val type = testee.determineType("mailto:foo@example.com") as Email
        assertEquals("mailto:foo@example.com", type.emailAddress)
    }

    @Test
    fun whenUrlIsSmsThenSmsTypeDetected() {
        val expected = Sms::class
        val actual = testee.determineType("sms:123-555-13245")
        assertEquals(expected, actual::class)
    }

    @Test
    fun whenUrlIsSmsToThenSmsTypeDetected() {
        val expected = Sms::class
        val actual = testee.determineType("smsto:123-555-13245")
        assertEquals(expected, actual::class)
    }

    @Test
    fun whenUrlIsSmsThenSchemeRemoved() {
        val type = testee.determineType("sms:123-555-12323") as Sms
        assertEquals("123-555-12323", type.telephoneNumber)
    }

    @Test
    fun whenUrlIsSmsToThenSchemeRemoved() {
        val type = testee.determineType("smsto:123-555-12323") as Sms
        assertEquals("123-555-12323", type.telephoneNumber)
    }

    @Test
    fun whenUrlIsCustomUriSchemeThenNonHttpAppLinkTypeDetectedWithAdditionalIntentFlags() {
        externalAppIntentFlagsFeature.self().setRawStoredState(State(true))
        val type = testee.determineType("myapp:foo bar") as NonHttpAppLink
        assertEquals("myapp:foo bar", type.uriString)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP, type.intent.flags)
        assertEquals(Intent.CATEGORY_BROWSABLE, type.intent.categories.first())
    }

    @Test
    fun whenUrlIsCustomUriSchemeThenNonHttpAppLinkTypeDetectedWithoutAdditionalIntentFlags() {
        externalAppIntentFlagsFeature.self().setRawStoredState(State(false))
        val type = testee.determineType("myapp:foo bar") as NonHttpAppLink
        assertEquals("myapp:foo bar", type.uriString)
        assertEquals(0, type.intent.flags)
        assertNull(type.intent.categories)
    }

    @Test
    fun whenUrlIsNotPrivacyProThenQueryTypeDetected() {
        whenever(subscriptions.shouldLaunchPrivacyProForUrl(any())).thenReturn(false)
        val result = testee.determineType("duckduckgo.com")
        assertTrue(result is SearchQuery)
    }

    @Test
    fun whenUrlIsPrivacyProThenPrivacyProTypeDetected() {
        whenever(subscriptions.shouldLaunchPrivacyProForUrl(any())).thenReturn(true)
        val result = testee.determineType("duckduckgo.com")
        assertTrue(result is ShouldLaunchPrivacyProLink)
    }

    @Test
    fun whenUrlIsNotDuckChatUrlAndFeatureIsEnabledThenSearchQueryTypeDetected() {
        whenever(mockAIChatQueryDetectionFeatureToggle.isEnabled()).thenReturn(true)
        whenever(mockDuckChat.isDuckChatUrl(any())).thenReturn(false)
        val result = testee.determineType("duckduckgo.com")
        assertTrue(result is SearchQuery)
    }

    @Test
    fun whenUrlIsDuckChatUrlAndFeatureIsEnabledThenDuckChatTypeDetected() {
        whenever(mockAIChatQueryDetectionFeatureToggle.isEnabled()).thenReturn(true)
        whenever(mockDuckChat.isDuckChatUrl(any())).thenReturn(true)
        val result = testee.determineType("duckduckgo.com")
        assertTrue(result is ShouldLaunchDuckChatLink)
    }

    @Test
    fun whenUrlIsDuckChatUrlAndFeatureIsDisabledThenSearchQueryTypeDetected() {
        whenever(mockDuckChat.isDuckChatUrl(any())).thenReturn(true)
        val result = testee.determineType("duckduckgo.com")
        assertTrue(result is SearchQuery)
    }

    @Test
    fun whenUrlIsParametrizedQueryThenSearchQueryTypeDetected() {
        val type = testee.determineType("foo site:duckduckgo.com") as SearchQuery
        assertEquals("foo site:duckduckgo.com", type.query)
    }

    @Test
    fun whenUrlIsJavascriptSchemeThenWebSearchTypeDetected() {
        val expected = SearchQuery::class
        val actual = testee.determineType("javascript:alert(0)")
        assertEquals(expected, actual::class)
    }

    @Test
    fun whenUrlIsJavascriptSchemeThenFullQueryRetained() {
        val type = testee.determineType("javascript:alert(0)") as SearchQuery
        assertEquals("javascript:alert(0)", type.query)
    }

    @Test
    fun whenUrlIsAboutSchemeThenWebSearchTypeDetected() {
        val expected = SearchQuery::class
        val actual = testee.determineType("about:blank")
        assertEquals(expected, actual::class)
    }

    @Test
    fun whenUrlIsAboutSchemeThenFullQueryRetained() {
        val type = testee.determineType("about:blank") as SearchQuery
        assertEquals("about:blank", type.query)
    }

    @Test
    fun whenUrlIsFileSchemeThenWebSearchTypeDetected() {
        val expected = SearchQuery::class
        val actual = testee.determineType("file:///sdcard/")
        assertEquals(expected, actual::class)
    }

    @Test
    fun whenUrlIsFileSchemeThenFullQueryRetained() {
        val type = testee.determineType("file:///sdcard/") as SearchQuery
        assertEquals("file:///sdcard/", type.query)
    }

    @Test
    fun whenUrlIsSiteSchemeThenWebSearchTypeDetected() {
        val expected = SearchQuery::class
        val actual = testee.determineType("site:example.com")
        assertEquals(expected, actual::class)
    }

    @Test
    fun whenUrlIsSiteSchemeThenFullQueryRetained() {
        val type = testee.determineType("site:example.com") as SearchQuery
        assertEquals("site:example.com", type.query)
    }

    @Test
    fun whenUrlIsBlobSchemeThenFullQueryRetained() {
        val type = testee.determineType("blob:example.com") as SearchQuery
        assertEquals("blob:example.com", type.query)
    }

    @Test
    fun whenSmsContentIsLongerThanMaxAllowedThenTruncateToMax() {
        val longSms = randomString(SMS_MAX_LENGTH + 1)
        val type = testee.determineType("sms:$longSms") as Sms
        assertEquals(longSms.substring(0, SMS_MAX_LENGTH), type.telephoneNumber)
    }

    @Test
    fun whenSmsToContentIsLongerThanMaxAllowedThenTruncateToMax() {
        val longSms = randomString(SMS_MAX_LENGTH + 1)
        val type = testee.determineType("smsto:$longSms") as Sms
        assertEquals(longSms.substring(0, SMS_MAX_LENGTH), type.telephoneNumber)
    }

    @Test
    fun whenEmailContentIsLongerThanMaxAllowedThenTruncateToMax() {
        val longEmail = "mailto:${randomString(EMAIL_MAX_LENGTH + 1)}"
        val type = testee.determineType(longEmail) as Email
        assertEquals(longEmail.substring(0, EMAIL_MAX_LENGTH), type.emailAddress)
    }

    @Test
    fun whenTelephoneContentIsLongerThanMaxAllowedThenTruncateToMax() {
        val longTelephone = randomString(PHONE_MAX_LENGTH + 1)
        val type = testee.determineType("tel:$longTelephone") as Telephone
        assertEquals(longTelephone.substring(0, PHONE_MAX_LENGTH), type.telephoneNumber)
    }

    @Test
    fun whenTelephonePromptContentIsLongerThanMaxAllowedThenTruncateToMax() {
        val longTelephone = randomString(PHONE_MAX_LENGTH + 1)
        val type = testee.determineType("telprompt:$longTelephone") as Telephone
        assertEquals(longTelephone.substring(0, PHONE_MAX_LENGTH), type.telephoneNumber)
    }

    @Test
    fun whenUrlIsAmpLinkThenExtractedAmpLinkTypeDetected() {
        whenever(mockAmpLinks.extractCanonicalFromAmpLink(anyString()))
            .thenReturn(AmpLinkType.ExtractedAmpLink(extractedUrl = "https://www.example.com"))
        val expected = ExtractedAmpLink::class
        val actual = testee.determineType("https://www.google.com/amp/s/www.example.com")
        assertEquals(expected, actual::class)
        assertEquals("https://www.example.com", (actual as ExtractedAmpLink).extractedUrl)
    }

    @Test
    fun whenUrlIsCloakedAmpLinkThenCloakedAmpLinkTypeDetected() {
        whenever(mockAmpLinks.extractCanonicalFromAmpLink(anyString()))
            .thenReturn(AmpLinkType.CloakedAmpLink(ampUrl = "https://www.example.com/amp"))
        val expected = CloakedAmpLink::class
        val actual = testee.determineType("https://www.example.com/amp")
        assertEquals(expected, actual::class)
        assertEquals("https://www.example.com/amp", (actual as CloakedAmpLink).ampUrl)
    }

    @Test
    fun whenUrlIsTrackingParameterLinkThenTrackingParameterLinkTypeDetected() {
        whenever(mockTrackingParameters.cleanTrackingParameters(initiatingUrl = anyString(), url = anyString()))
            .thenReturn("https://www.example.com/query.html")
        val expected = TrackingParameterLink::class
        val actual =
            testee.determineType(initiatingUrl = "https://www.example.com", uri = "https://www.example.com/query.html?utm_example=something".toUri())
        assertEquals(expected, actual::class)
        assertEquals("https://www.example.com/query.html", (actual as TrackingParameterLink).cleanedUrl)
    }

    @Test
    fun whenUrlIsPrivacyProThenPrivacyProLinkDetected() {
        whenever(subscriptions.shouldLaunchPrivacyProForUrl(any())).thenReturn(true)

        val actual =
            testee.determineType(initiatingUrl = "https://www.example.com", uri = "https://www.example.com".toUri())
        assertTrue(actual is ShouldLaunchPrivacyProLink)
    }

    @Test
    fun whenIsDuckChatUrlThenReturnShouldLaunchDuckChatLink() = runTest {
        whenever(mockDuckChat.isDuckChatUrl(any())).thenReturn(true)
        val type = testee.determineType("https://duck.ai")
        whenever(mockPackageManager.resolveActivity(any(), eq(PackageManager.MATCH_DEFAULT_ONLY))).thenReturn(null)
        whenever(mockPackageManager.queryIntentActivities(any(), anyInt())).thenReturn(
            listOf(
                buildAppResolveInfo(),
                buildBrowserResolveInfo(),
                ResolveInfo(),
            ),
        )
        assertTrue(type is ShouldLaunchDuckChatLink)
    }

    @Test
    fun whenIsNotDuckChatUrlThenDoNotReturnShouldLaunchDuckChatLink() = runTest {
        whenever(mockDuckChat.isDuckChatUrl(any())).thenReturn(false)
        val type = testee.determineType("https://example.com")
        whenever(mockPackageManager.resolveActivity(any(), eq(PackageManager.MATCH_DEFAULT_ONLY))).thenReturn(null)
        whenever(mockPackageManager.queryIntentActivities(any(), anyInt())).thenReturn(
            listOf(
                buildAppResolveInfo(),
                buildBrowserResolveInfo(),
                ResolveInfo(),
            ),
        )
        assertTrue(type !is ShouldLaunchDuckChatLink)
    }

    @Test
    fun whenIntentSchemeToggleEnabledThenCheckForIntentCalledWithUriIntentScheme() {
        androidBrowserConfigFeature.handleIntentScheme().setRawStoredState(State(true))

        testee.determineType("intent://path#Intent;scheme=testscheme;package=com.example.app;end")

        verify(testee).checkForIntent(eq("intent"), any(), eq(URI_INTENT_SCHEME))
    }

    @Test
    fun whenIntentSchemeToggleDisabledThenCheckForIntentCalledWithUriAndroidAppScheme() {
        androidBrowserConfigFeature.handleIntentScheme().setRawStoredState(State(false))

        testee.determineType("intent://path#Intent;scheme=testscheme;package=com.example.app;end")

        verify(testee).checkForIntent(eq("intent"), any(), eq(URI_ANDROID_APP_SCHEME))
    }

    private fun randomString(length: Int): String {
        val charList: List<Char> = ('a'..'z') + ('0'..'9')
        return List(length) { charList.random() }.joinToString("")
    }

    private fun buildAppResolveInfo(): ResolveInfo {
        val activity = ResolveInfo()
        activity.filter = IntentFilter()
        activity.filter.addDataAuthority("host.com", "123")
        activity.filter.addDataPath("/path", 0)
        activity.activityInfo = ActivityInfo()
        activity.activityInfo.packageName = EXAMPLE_APP_PACKAGE
        activity.activityInfo.name = EXAMPLE_APP_ACTIVITY_NAME
        return activity
    }

    private fun buildBrowserResolveInfo(): ResolveInfo {
        val activity = ResolveInfo()
        activity.filter = IntentFilter()
        activity.activityInfo = ActivityInfo()
        activity.activityInfo.packageName = EXAMPLE_BROWSER_PACKAGE
        activity.activityInfo.name = EXAMPLE_BROWSER_ACTIVITY_NAME
        return activity
    }

    companion object {
        const val EXAMPLE_APP_PACKAGE = "com.test.apppackage"
        const val EXAMPLE_APP_ACTIVITY_NAME = "com.test.AppActivity"
        const val EXAMPLE_BROWSER_PACKAGE = "com.test.browserpackage"
        const val EXAMPLE_BROWSER_ACTIVITY_NAME = "com.test.BrowserActivity"
    }
}
