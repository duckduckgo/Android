/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.browser.applinks

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.SpecialUrlDetector.UrlType.AppLink
import com.duckduckgo.browser.feature.toggles.AndroidBrowserConfigFeature
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

@RunWith(AndroidJUnit4::class)
class DuckDuckGoAppLinksHandlerTest {

    private lateinit var testee: DuckDuckGoAppLinksHandler

    private var mockCallback: () -> Unit = mock()

    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature =
        FakeFeatureToggleFactory.create(AndroidBrowserConfigFeature::class.java)

    @Before
    fun setup() {
        androidBrowserConfigFeature.customTabEndlessLoopFix().setRawStoredState(State(true))
        testee = DuckDuckGoAppLinksHandler(androidBrowserConfigFeature)
        testee.previousUrl = "example.com"
    }

    @Test
    fun whenAppLinkHandledAndIsSameOrSubdomainThenReturnFalse() {
        assertFalse(
            testee.handleAppLink(
                isForMainFrame = true,
                appLink = AppLink(uriString = "example.com"),
                hasGesture = true,
                clientPackage = null,
                launchAppLink = mockCallback,
                shouldHaltWebNavigation = true,
                appLinksEnabled = true,
            ),
        )
    }

    @Test
    fun whenAppLinkHandledAndIsNotSameOrSubdomainThenReturnFalseAndLaunchAppLink() {
        assertFalse(
            testee.handleAppLink(
                isForMainFrame = true,
                appLink = AppLink(uriString = "foo.com"),
                hasGesture = true,
                clientPackage = null,
                launchAppLink = mockCallback,
                shouldHaltWebNavigation = false,
                appLinksEnabled = true,
            ),
        )
        verify(mockCallback).invoke()
    }

    @Test
    fun whenAppLinkHandledAndIsNotForMainFrameThenReturnFalse() {
        assertFalse(
            testee.handleAppLink(
                isForMainFrame = false,
                appLink = AppLink(uriString = "foo.com"),
                hasGesture = true,
                clientPackage = null,
                launchAppLink = mockCallback,
                shouldHaltWebNavigation = true,
                appLinksEnabled = true,
            ),
        )
        verifyNoInteractions(mockCallback)
    }

    @Test
    fun whenAppLinkHandledOnApiLessThan24ThenReturnFalse() {
        assertFalse(
            testee.handleAppLink(
                isForMainFrame = false,
                appLink = AppLink(uriString = "foo.com"),
                hasGesture = true,
                clientPackage = null,
                launchAppLink = mockCallback,
                shouldHaltWebNavigation = true,
                appLinksEnabled = true,
            ),
        )
        verifyNoInteractions(mockCallback)
    }

    @Test
    fun whenPreviousUrlUpdatedThenUpdatePreviousUrl() {
        testee.updatePreviousUrl("foo.com")
        assertEquals("foo.com", testee.previousUrl)
    }

    @Test
    fun whenPreviousUrlUpdatedAndIsNullThenResetTriggerState() {
        testee.hasTriggeredForDomain = true
        testee.updatePreviousUrl(null)
        assertFalse(testee.hasTriggeredForDomain)
    }

    @Test
    fun whenPreviousUrlUpdatedAndPreviousUrlIsNullThenDoNotResetTriggerState() {
        testee.previousUrl = null
        testee.hasTriggeredForDomain = true
        testee.updatePreviousUrl("example.com")
        assertTrue(testee.hasTriggeredForDomain)
    }

    @Test
    fun whenPreviousUrlUpdatedAndIsNotSameOrSubdomainThenResetTriggerState() {
        testee.hasTriggeredForDomain = true
        testee.previousUrl = "example.com"
        testee.updatePreviousUrl("foo.com")
        assertFalse(testee.hasTriggeredForDomain)
    }

    @Test
    fun whenPreviousUrlUpdatedAndIsSameOrSubdomainThenDoNotResetTriggerState() {
        testee.hasTriggeredForDomain = true
        testee.previousUrl = "example.com"
        testee.updatePreviousUrl("app.example.com")
        assertTrue(testee.hasTriggeredForDomain)
    }

    @Test
    fun whenAppLinksDisabledThenReturnFalse() {
        assertFalse(
            testee.handleAppLink(
                isForMainFrame = true,
                appLink = AppLink(uriString = "example.com"),
                hasGesture = true,
                clientPackage = null,
                launchAppLink = mockCallback,
                shouldHaltWebNavigation = true,
                appLinksEnabled = false,
            ),
        )
    }

    @Test
    fun whenPreviousUrlIsSameThenReturnFalse() {
        testee.previousUrl = "example.com"
        assertFalse(
            testee.handleAppLink(
                isForMainFrame = true,
                appLink = AppLink(uriString = "example.com"),
                hasGesture = true,
                clientPackage = null,
                launchAppLink = mockCallback,
                shouldHaltWebNavigation = true,
                appLinksEnabled = true,
            ),
        )
    }

    @Test
    fun whenPreviousUrlIsSubdomainThenReturnFalse() {
        testee.previousUrl = "foo.example.com"
        assertFalse(
            testee.handleAppLink(
                isForMainFrame = true,
                appLink = AppLink(uriString = "example.com"),
                hasGesture = true,
                clientPackage = null,
                launchAppLink = mockCallback,
                shouldHaltWebNavigation = true,
                appLinksEnabled = true,
            ),
        )
    }

    @Test
    fun whenNextUrlIsSubdomainThenReturnFalse() {
        testee.previousUrl = "example.com"
        assertFalse(
            testee.handleAppLink(
                isForMainFrame = true,
                appLink = AppLink(uriString = "foo.example.com"),
                hasGesture = true,
                clientPackage = null,
                launchAppLink = mockCallback,
                shouldHaltWebNavigation = true,
                appLinksEnabled = true,
            ),
        )
    }

    @Test
    fun whenAppLinkIsSameOrSubdomainAndIsUserQueryThenReturnFalseAndSetPreviousUrlAndLaunchAppLink() {
        testee.isAUserQuery = true
        testee.hasTriggeredForDomain = false
        testee.previousUrl = "example.com/something"
        assertFalse(
            testee.handleAppLink(
                isForMainFrame = true,
                appLink = AppLink(uriString = "example.com/something_else"),
                hasGesture = true,
                clientPackage = null,
                launchAppLink = mockCallback,
                shouldHaltWebNavigation = true,
                appLinksEnabled = true,
            ),
        )
        assertTrue(testee.hasTriggeredForDomain)
        assertEquals("example.com/something_else", testee.previousUrl)
        verify(mockCallback).invoke()
    }

    @Test
    fun whenAppLinkIsSameOrSubdomainAndHasNotTriggeredForDomainThenReturnFalseAndSetPreviousUrlAndLaunchAppLink() {
        testee.isAUserQuery = false
        testee.hasTriggeredForDomain = false
        testee.previousUrl = "example.com/something"
        assertFalse(
            testee.handleAppLink(
                isForMainFrame = true,
                appLink = AppLink(uriString = "app.example.com/something"),
                hasGesture = true,
                clientPackage = null,
                launchAppLink = mockCallback,
                shouldHaltWebNavigation = true,
                appLinksEnabled = true,
            ),
        )
        assertTrue(testee.hasTriggeredForDomain)
        assertEquals("app.example.com/something", testee.previousUrl)
        verify(mockCallback).invoke()
    }

    @Test
    fun whenAppLinkIsSameOrSubdomainAndIsInAlwaysTriggerListThenReturnFalseAndSetPreviousUrlAndLaunchAppLink() {
        testee.isAUserQuery = false
        testee.hasTriggeredForDomain = true
        testee.previousUrl = "digid.nl/something"
        assertTrue(
            testee.handleAppLink(
                isForMainFrame = true,
                appLink = AppLink(uriString = "app.digid.nl/something"),
                hasGesture = true,
                clientPackage = null,
                launchAppLink = mockCallback,
                shouldHaltWebNavigation = true,
                appLinksEnabled = true,
            ),
        )
        assertTrue(testee.hasTriggeredForDomain)
        assertEquals("app.digid.nl/something", testee.previousUrl)
        verify(mockCallback).invoke()
    }

    @Test
    fun whenAppLinkIsSameOrSubdomainAndIsNotUserQueryAndHasTriggeredForDomainAndIsNotInAlwaysTriggerListThenReturnFalse() {
        testee.hasTriggeredForDomain = true
        testee.isAUserQuery = false
        testee.previousUrl = "foo.example.com"
        assertFalse(
            testee.handleAppLink(
                isForMainFrame = true,
                appLink = AppLink(uriString = "example.com"),
                hasGesture = true,
                clientPackage = null,
                launchAppLink = mockCallback,
                shouldHaltWebNavigation = true,
                appLinksEnabled = true,
            ),
        )
        assertTrue(testee.hasTriggeredForDomain)
        assertEquals("foo.example.com", testee.previousUrl)
        verifyNoInteractions(mockCallback)
    }

    @Test
    fun whenShouldHaltWebNavigationThenReturnTrueAndSetPreviousUrlAndLaunchAppLink() {
        testee.previousUrl = "foo.com"
        assertTrue(
            testee.handleAppLink(
                isForMainFrame = true,
                appLink = AppLink(uriString = "example.com"),
                hasGesture = true,
                clientPackage = null,
                launchAppLink = mockCallback,
                shouldHaltWebNavigation = true,
                appLinksEnabled = true,
            ),
        )
        assertEquals("example.com", testee.previousUrl)
        verify(mockCallback).invoke()
    }

    @Test
    fun whenShouldNotHaltWebNavigationThenReturnFalseAndSetPreviousUrlAndLaunchAppLink() {
        testee.previousUrl = "foo.com"
        assertFalse(
            testee.handleAppLink(
                isForMainFrame = true,
                appLink = AppLink(uriString = "example.com"),
                hasGesture = true,
                clientPackage = null,
                launchAppLink = mockCallback,
                shouldHaltWebNavigation = false,
                appLinksEnabled = true,
            ),
        )
        assertEquals("example.com", testee.previousUrl)
        verify(mockCallback).invoke()
    }

    @Test
    fun whenNoGestureAndNotUserQueryAndNotTrustedCallerThenReturnFalseAndDoNotLaunch() {
        testee.isAUserQuery = false
        testee.previousUrl = "foo.com"
        assertFalse(
            testee.handleAppLink(
                isForMainFrame = true,
                appLink = AppLink(uriString = "example.com"),
                hasGesture = false,
                clientPackage = null,
                launchAppLink = mockCallback,
                shouldHaltWebNavigation = true,
                appLinksEnabled = true,
            ),
        )
        verifyNoInteractions(mockCallback)
    }

    @Test
    fun whenNoGestureAndIsUserQueryAndNotTrustedCallerThenReturnFalseAndDoNotLaunch() {
        testee.isAUserQuery = true
        testee.previousUrl = "foo.com"
        assertFalse(
            testee.handleAppLink(
                isForMainFrame = true,
                appLink = AppLink(uriString = "example.com"),
                hasGesture = false,
                clientPackage = null,
                launchAppLink = mockCallback,
                shouldHaltWebNavigation = true,
                appLinksEnabled = true,
            ),
        )
        verifyNoInteractions(mockCallback)
    }

    @Test
    fun whenNoGestureButTrustedCallerViaComponentPackageThenLaunchAppLink() {
        testee.isAUserQuery = false
        testee.previousUrl = "foo.com"
        val appIntent = Intent().setComponent(ComponentName("com.example.app", "com.example.app.MainActivity"))
        assertTrue(
            testee.handleAppLink(
                isForMainFrame = true,
                appLink = AppLink(uriString = "example.com", appIntent = appIntent),
                hasGesture = false,
                clientPackage = "com.example.app",
                launchAppLink = mockCallback,
                shouldHaltWebNavigation = true,
                appLinksEnabled = true,
            ),
        )
        verify(mockCallback).invoke()
    }

    @Test
    fun whenNoGestureButTrustedCallerViaIntentPackageThenLaunchAppLink() {
        testee.isAUserQuery = false
        testee.previousUrl = "foo.com"
        val appIntent = Intent().setPackage("com.example.app")
        assertTrue(
            testee.handleAppLink(
                isForMainFrame = true,
                appLink = AppLink(uriString = "example.com", appIntent = appIntent),
                hasGesture = false,
                clientPackage = "com.example.app",
                launchAppLink = mockCallback,
                shouldHaltWebNavigation = true,
                appLinksEnabled = true,
            ),
        )
        verify(mockCallback).invoke()
    }

    @Test
    fun whenNoGestureAndClientPackageDoesNotMatchTargetThenReturnFalseAndDoNotLaunch() {
        testee.isAUserQuery = false
        testee.previousUrl = "foo.com"
        val appIntent = Intent().setComponent(ComponentName("com.example.app", "com.example.app.MainActivity"))
        assertFalse(
            testee.handleAppLink(
                isForMainFrame = true,
                appLink = AppLink(uriString = "example.com", appIntent = appIntent),
                hasGesture = false,
                clientPackage = "com.different.app",
                launchAppLink = mockCallback,
                shouldHaltWebNavigation = true,
                appLinksEnabled = true,
            ),
        )
        verifyNoInteractions(mockCallback)
    }

    @Test
    fun whenAppLinksDisabledAndOverrideAllowedAndTrustedCallerWithoutGestureThenLaunchAppLink() {
        val appIntent = Intent().setPackage("com.example.app")
        assertTrue(
            testee.handleAppLink(
                isForMainFrame = true,
                appLink = AppLink(uriString = "foo.com", appIntent = appIntent),
                hasGesture = false,
                clientPackage = "com.example.app",
                appLinksEnabled = false,
                shouldHaltWebNavigation = true,
                trustedCallerOverridesDisabledAppLinks = true,
                launchAppLink = mockCallback,
            ),
        )
        verify(mockCallback).invoke()
    }

    @Test
    fun whenAppLinksDisabledAndOverrideAllowedWithGestureButNoVerifiedCallerThenLaunchAppLink() {
        val appIntent = Intent().setPackage("com.example.app")
        assertTrue(
            testee.handleAppLink(
                isForMainFrame = true,
                appLink = AppLink(uriString = "foo.com", appIntent = appIntent),
                hasGesture = true,
                clientPackage = null,
                appLinksEnabled = false,
                shouldHaltWebNavigation = true,
                trustedCallerOverridesDisabledAppLinks = true,
                launchAppLink = mockCallback,
            ),
        )
        verify(mockCallback).invoke()
    }

    @Test
    fun whenAppLinksDisabledAndOverrideAllowedWithoutGestureOrVerifiedCallerThenReturnFalseAndDoNotLaunch() {
        val appIntent = Intent().setPackage("com.example.app")
        assertFalse(
            testee.handleAppLink(
                isForMainFrame = true,
                appLink = AppLink(uriString = "foo.com", appIntent = appIntent),
                hasGesture = false,
                clientPackage = null,
                appLinksEnabled = false,
                shouldHaltWebNavigation = true,
                trustedCallerOverridesDisabledAppLinks = true,
                launchAppLink = mockCallback,
            ),
        )
        verifyNoInteractions(mockCallback)
    }

    @Test
    fun whenAppLinksDisabledAndOverrideNotAllowedThenTrustedCallerReturnsFalseAndDoesNotLaunch() {
        val appIntent = Intent().setPackage("com.example.app")
        assertFalse(
            testee.handleAppLink(
                isForMainFrame = true,
                appLink = AppLink(uriString = "foo.com", appIntent = appIntent),
                hasGesture = true,
                clientPackage = "com.example.app",
                appLinksEnabled = false,
                shouldHaltWebNavigation = true,
                trustedCallerOverridesDisabledAppLinks = false,
                launchAppLink = mockCallback,
            ),
        )
        verifyNoInteractions(mockCallback)
    }

    @Test
    fun whenNoGestureAndNotTrustedCallerButIsInAlwaysTriggerListThenLaunchAppLink() {
        testee.isAUserQuery = false
        testee.previousUrl = "foo.com"
        assertTrue(
            testee.handleAppLink(
                isForMainFrame = true,
                appLink = AppLink(uriString = "app.digid.nl/something"),
                hasGesture = false,
                clientPackage = null,
                launchAppLink = mockCallback,
                shouldHaltWebNavigation = true,
                appLinksEnabled = true,
            ),
        )
        assertEquals("app.digid.nl/something", testee.previousUrl)
        verify(mockCallback).invoke()
    }

    @Test
    fun whenNoGestureAndSameDomainAndHasTriggeredButIsInAlwaysTriggerListThenLaunchAppLink() {
        testee.isAUserQuery = false
        testee.hasTriggeredForDomain = true
        testee.previousUrl = "digid.nl/something"
        assertTrue(
            testee.handleAppLink(
                isForMainFrame = true,
                appLink = AppLink(uriString = "app.digid.nl/something"),
                hasGesture = false,
                clientPackage = null,
                launchAppLink = mockCallback,
                shouldHaltWebNavigation = true,
                appLinksEnabled = true,
            ),
        )
        assertEquals("app.digid.nl/something", testee.previousUrl)
        verify(mockCallback).invoke()
    }

    @Test
    fun whenNoGestureAndNotTrustedCallerAndParentOfAlwaysTriggerDomainThenReturnFalseAndDoNotLaunch() {
        testee.isAUserQuery = false
        testee.previousUrl = "foo.com"
        assertFalse(
            testee.handleAppLink(
                isForMainFrame = true,
                appLink = AppLink(uriString = "digid.nl/something"),
                hasGesture = false,
                clientPackage = null,
                launchAppLink = mockCallback,
                shouldHaltWebNavigation = true,
                appLinksEnabled = true,
            ),
        )
        verifyNoInteractions(mockCallback)
    }

    @Test
    fun whenFixDisabledThenNoGestureAndNotTrustedCallerStillLaunchesAppLink() {
        androidBrowserConfigFeature.customTabEndlessLoopFix().setRawStoredState(State(false))
        testee.isAUserQuery = false
        testee.previousUrl = "foo.com"
        assertTrue(
            testee.handleAppLink(
                isForMainFrame = true,
                appLink = AppLink(uriString = "example.com"),
                hasGesture = false,
                clientPackage = null,
                launchAppLink = mockCallback,
                shouldHaltWebNavigation = true,
                appLinksEnabled = true,
            ),
        )
        verify(mockCallback).invoke()
    }

    @Test
    fun whenFixDisabledThenNoGestureAndClientPackageMismatchStillLaunchesAppLink() {
        androidBrowserConfigFeature.customTabEndlessLoopFix().setRawStoredState(State(false))
        testee.isAUserQuery = false
        testee.previousUrl = "foo.com"
        val appIntent = Intent().setComponent(ComponentName("com.example.app", "com.example.app.MainActivity"))
        assertTrue(
            testee.handleAppLink(
                isForMainFrame = true,
                appLink = AppLink(uriString = "example.com", appIntent = appIntent),
                hasGesture = false,
                clientPackage = "com.different.app",
                launchAppLink = mockCallback,
                shouldHaltWebNavigation = true,
                appLinksEnabled = true,
            ),
        )
        verify(mockCallback).invoke()
    }

    @Test
    fun whenTrustedCallerViaComponentPackageMatchesClientThenReturnTrue() {
        val appIntent = Intent().setComponent(ComponentName("com.example.app", "com.example.app.MainActivity"))
        assertTrue(testee.isTrustedCaller(AppLink(uriString = "example.com", appIntent = appIntent), "com.example.app"))
    }

    @Test
    fun whenTrustedCallerViaIntentPackageMatchesClientThenReturnTrue() {
        val appIntent = Intent().setPackage("com.example.app")
        assertTrue(testee.isTrustedCaller(AppLink(uriString = "example.com", appIntent = appIntent), "com.example.app"))
    }

    @Test
    fun whenTargetPackageDoesNotMatchClientThenReturnFalse() {
        val appIntent = Intent().setPackage("com.example.app")
        assertFalse(testee.isTrustedCaller(AppLink(uriString = "example.com", appIntent = appIntent), "com.different.app"))
    }

    @Test
    fun whenTargetPackageIsNullThenReturnFalse() {
        assertFalse(testee.isTrustedCaller(AppLink(uriString = "example.com", appIntent = null), "com.example.app"))
    }

    @Test
    fun whenIntentSchemePackageResolvesToCallerActivityThenTrustedCallerIntentReturned() {
        val intent = browsableIntent().setPackage(CALLER)
        val trusted = testee.trustedCallerIntent(intent, CALLER) { listOf(resolveInfo(CALLER)) }
        assertEquals(CALLER, trusted?.intent?.`package`)
    }

    @Test
    fun whenIntentHasComponentThenTrustedCallerIntentResolvedWithoutIt() {
        val intent = browsableIntent().setComponent(ComponentName(CALLER, "$CALLER.InternalActivity"))
        var queried: Intent? = null
        val trusted = testee.trustedCallerIntent(intent, CALLER) {
            queried = it
            listOf(resolveInfo(CALLER))
        }
        assertNull(queried?.component)
        assertNull(trusted?.intent?.component)
        assertEquals(ComponentName(CALLER, "$CALLER.InternalActivity"), intent.component)
    }

    @Test
    fun whenIntentHasComponentAndNoCallerFilterMatchesThenTrustedCallerIntentIsNull() {
        val intent = browsableIntent().setComponent(ComponentName(CALLER, "$CALLER.InternalActivity"))
        assertNull(testee.trustedCallerIntent(intent, CALLER) { emptyList() })
    }

    @Test
    fun whenIntentHasSelectorThenSelectorResolvedWithoutComponentAndBrowsable() {
        val intent = browsableIntent()
        intent.selector = Intent(Intent.ACTION_VIEW).setComponent(ComponentName(CALLER, "$CALLER.InternalActivity"))
        var queried: Intent? = null
        testee.trustedCallerIntent(intent, CALLER) {
            queried = it
            listOf(resolveInfo(CALLER))
        }
        assertNull(queried?.selector?.component)
        assertTrue(queried?.selector?.hasCategory(Intent.CATEGORY_BROWSABLE) == true)
    }

    @Test
    fun whenCustomSchemeResolvesToSingleCallerActivityThenTrustedCallerIntentReturned() {
        assertNotNull(testee.trustedCallerIntent(browsableIntent(), CALLER) { listOf(resolveInfo(CALLER)) })
    }

    @Test
    fun whenCustomSchemeResolvesToMultipleCallerActivitiesThenTrustedCallerIntentReturnedWithAllOfThem() {
        val trusted = testee.trustedCallerIntent(browsableIntent(), CALLER) { listOf(resolveInfo(CALLER), resolveInfo(CALLER)) }
        assertEquals(2, trusted?.activities?.size)
    }

    @Test
    fun whenCustomSchemeResolvesToActivitiesInDifferentAppsThenTrustedCallerIntentIsNull() {
        assertNull(testee.trustedCallerIntent(browsableIntent(), CALLER) { listOf(resolveInfo(CALLER), resolveInfo(OTHER)) })
    }

    @Test
    fun whenIntentResolvesToNoActivityThenTrustedCallerIntentIsNull() {
        assertNull(testee.trustedCallerIntent(browsableIntent().setPackage(CALLER), CALLER) { emptyList() })
    }

    @Test
    fun whenIntentNotBrowsableThenTrustedCallerIntentResolvedAsBrowsable() {
        val intent = Intent(Intent.ACTION_VIEW, "example://details".toUri()).setPackage(CALLER)
        var queried: Intent? = null
        testee.trustedCallerIntent(intent, CALLER) {
            queried = it
            listOf(resolveInfo(CALLER))
        }
        assertTrue(queried?.hasCategory(Intent.CATEGORY_BROWSABLE) == true)
        assertFalse(intent.hasCategory(Intent.CATEGORY_BROWSABLE))
    }

    @Test
    fun whenCallerPackageIsNullThenTrustedCallerIntentIsNull() {
        assertNull(testee.trustedCallerIntent(browsableIntent().setPackage(CALLER), null) { listOf(resolveInfo(CALLER)) })
    }

    @Test
    fun whenIntentResolvesToDifferentAppThenTrustedCallerIntentIsNull() {
        assertNull(testee.trustedCallerIntent(browsableIntent().setPackage(OTHER), CALLER) { listOf(resolveInfo(OTHER)) })
    }

    @Test
    fun whenDomainInAlwaysTriggerListThenIsAlwaysTriggerDomainReturnsTrue() {
        assertTrue(testee.isAlwaysTriggerDomain(AppLink(uriString = "https://app.digid.nl/path")))
    }

    @Test
    fun whenDomainNotInAlwaysTriggerListThenIsAlwaysTriggerDomainReturnsFalse() {
        assertFalse(testee.isAlwaysTriggerDomain(AppLink(uriString = "https://example.com/path")))
    }

    private fun browsableIntent(): Intent =
        Intent(Intent.ACTION_VIEW, "example://details".toUri()).addCategory(Intent.CATEGORY_BROWSABLE)

    private fun resolveInfo(packageName: String): ResolveInfo =
        ResolveInfo().apply { activityInfo = ActivityInfo().apply { this.packageName = packageName } }

    private companion object {
        const val CALLER = "com.example.app"
        const val OTHER = "com.different.app"
    }
}
