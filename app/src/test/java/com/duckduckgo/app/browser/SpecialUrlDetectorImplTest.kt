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
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.SpecialUrlDetector.UrlType.*
import com.duckduckgo.app.browser.SpecialUrlDetectorImpl.Companion.EMAIL_MAX_LENGTH
import com.duckduckgo.app.browser.SpecialUrlDetectorImpl.Companion.PHONE_MAX_LENGTH
import com.duckduckgo.app.browser.SpecialUrlDetectorImpl.Companion.SMS_MAX_LENGTH
import org.mockito.kotlin.*
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import java.net.URISyntaxException

@RunWith(AndroidJUnit4::class)
class SpecialUrlDetectorImplTest {

    lateinit var testee: SpecialUrlDetector

    @Mock
    lateinit var mockPackageManager: PackageManager

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        testee = SpecialUrlDetectorImpl(mockPackageManager)
        whenever(mockPackageManager.queryIntentActivities(any(), anyInt())).thenReturn(emptyList())
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
    fun whenOneNonBrowserActivityFoundThenReturnAppLinkWithIntent() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            whenever(mockPackageManager.queryIntentActivities(any(), anyInt())).thenReturn(
                listOf(
                    buildAppResolveInfo(),
                    buildBrowserResolveInfo(),
                    ResolveInfo()
                )
            )
            val type = testee.determineType("https://example.com")
            verify(mockPackageManager).queryIntentActivities(
                argThat { hasCategory(Intent.CATEGORY_BROWSABLE) },
                eq(PackageManager.GET_RESOLVED_FILTER)
            )
            assertTrue(type is AppLink)
            val appLinkType = type as AppLink
            assertEquals("https://example.com", appLinkType.uriString)
            assertEquals(EXAMPLE_APP_PACKAGE, appLinkType.appIntent!!.component!!.packageName)
            assertEquals(EXAMPLE_APP_ACTIVITY_NAME, appLinkType.appIntent!!.component!!.className)
            assertNull(appLinkType.excludedComponents)
        }
    }

    @Test
    fun whenMultipleNonBrowserActivitiesFoundThenReturnAppLinkWithExcludedComponents() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            whenever(mockPackageManager.queryIntentActivities(any(), anyInt())).thenReturn(
                listOf(
                    buildAppResolveInfo(),
                    buildAppResolveInfo(),
                    buildBrowserResolveInfo(),
                    ResolveInfo()
                )
            )
            val type = testee.determineType("https://example.com")
            verify(mockPackageManager).queryIntentActivities(
                argThat { hasCategory(Intent.CATEGORY_BROWSABLE) },
                eq(PackageManager.GET_RESOLVED_FILTER)
            )
            assertTrue(type is AppLink)
            val appLinkType = type as AppLink
            assertEquals("https://example.com", appLinkType.uriString)
            assertEquals(1, appLinkType.excludedComponents!!.size)
            assertEquals(EXAMPLE_BROWSER_PACKAGE, appLinkType.excludedComponents!![0].packageName)
            assertEquals(EXAMPLE_BROWSER_ACTIVITY_NAME, appLinkType.excludedComponents!![0].className)
            assertNull(appLinkType.appIntent)
        }
    }

    @Test
    fun whenAppLinkCheckedOnApiLessThan24ThenReturnWebType() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            val type = testee.determineType("https://example.com")
            verifyNoInteractions(mockPackageManager)
            assertTrue(type is Web)
        }
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
    fun whenUrlIsCustomUriSchemeThenNonHttpAppLinkTypeDetected() {
        val type = testee.determineType("myapp:foo bar") as NonHttpAppLink
        assertEquals("myapp:foo bar", type.uriString)
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
