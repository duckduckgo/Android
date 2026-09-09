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

package com.duckduckgo.site.permissions.impl

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.text.Spanned
import android.text.style.ClickableSpan
import android.view.View
import android.webkit.PermissionRequest
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.api.DuckAiHostProvider
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.site.permissions.api.SitePermissionsGrantedListener
import com.duckduckgo.site.permissions.api.SitePermissionsManager.LocationPermissionRequest
import com.duckduckgo.site.permissions.api.SitePermissionsManager.SitePermissions
import com.duckduckgo.site.permissions.impl.feature.DrmPolicyFeature
import com.duckduckgo.site.permissions.impl.feature.SitePermissionsDialogRedesignFeature
import com.duckduckgo.site.permissions.store.sitepermissions.SitePermissionAskSettingType.ALLOW_ALWAYS
import com.duckduckgo.site.permissions.store.sitepermissions.SitePermissionAskSettingType.DENY_ALWAYS
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog
import com.duckduckgo.mobile.android.R as CommonR

@RunWith(AndroidJUnit4::class)
class SitePermissionsDialogActivityLauncherTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val systemPermissionsHelper: SystemPermissionsHelper = mock()
    private val sitePermissionsRepository: SitePermissionsRepository = mock()
    private val faviconManager: FaviconManager = mock()
    private val pixel: Pixel = mock()
    private val permissionsGrantedListener: SitePermissionsGrantedListener = mock()
    private val duckAiHostProvider: DuckAiHostProvider = mock<DuckAiHostProvider>().also {
        whenever(it.getHost()).thenReturn("duck.ai")
    }

    private val drmPolicyFeature = FakeFeatureToggleFactory.create(DrmPolicyFeature::class.java)
    private val sitePermissionsDialogRedesignFeature = FakeFeatureToggleFactory.create(SitePermissionsDialogRedesignFeature::class.java)

    private val testee = createLauncher(BrowserMode.REGULAR)

    private fun createLauncher(browserMode: BrowserMode) = SitePermissionsDialogActivityLauncher(
        systemPermissionsHelper = systemPermissionsHelper,
        sitePermissionsRepository = sitePermissionsRepository,
        faviconManager = faviconManager,
        pixel = pixel,
        dispatcher = coroutineRule.testDispatcherProvider,
        appCoroutineScope = coroutineRule.testScope,
        duckAiHostProvider = duckAiHostProvider,
        browserMode = browserMode,
        drmPolicyFeature = drmPolicyFeature,
        sitePermissionsDialogRedesignFeature = sitePermissionsDialogRedesignFeature,
    )

    @Test
    fun whenCentralPolicyEnabledThenLauncherDoesNotRecheckSessionOrBlockList() {
        drmPolicyFeature.self().setRawStoredState(Toggle.State(true))
        drmPolicyFeature.centralPolicy().setRawStoredState(Toggle.State(true))
        val activity: Activity = mock()
        val request: PermissionRequest = mock()
        whenever(request.resources).thenReturn(arrayOf(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID))
        whenever(sitePermissionsRepository.getDrmForSession(any(), any())).thenReturn(false)
        whenever(sitePermissionsRepository.isDrmBlockedForUrlByConfig(any())).thenReturn(true)

        // Showing the dialog needs a themed Activity, which a mock cannot provide. Only the skipped
        // pre-checks are under test here: with the flag on neither early return may fire.
        runCatching {
            testee.askForSitePermission(
                activity = activity,
                url = "https://example.com",
                tabId = "tabId",
                permissionsRequested = SitePermissions(
                    autoAccept = emptyList(),
                    userHandled = listOf(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID),
                ),
                request = request,
                permissionsGrantedListener = permissionsGrantedListener,
            )
        }

        verify(request, never()).grant(any())
        verify(request, never()).deny()
        verify(sitePermissionsRepository, never()).getDrmForSession(any(), any())
        verify(sitePermissionsRepository, never()).isDrmBlockedForUrlByConfig(any())
    }

    @Test
    fun whenDrmAlreadyAllowedForSessionThenDialogNotShownAndNoImpressionPixelFired() {
        whenever(sitePermissionsRepository.getDrmForSession("tabId", "example.com")).thenReturn(true)

        val activity: Activity = mock()
        val request: PermissionRequest = mock()
        whenever(request.resources).thenReturn(arrayOf(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID))

        testee.askForSitePermission(
            activity = activity,
            url = "https://example.com",
            tabId = "tabId",
            permissionsRequested = SitePermissions(
                autoAccept = emptyList(),
                userHandled = listOf(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID),
            ),
            request = request,
            permissionsGrantedListener = permissionsGrantedListener,
        )

        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenDrmBlockedByConfigThenDialogNotShownAndNoImpressionPixelFired() {
        whenever(sitePermissionsRepository.getDrmForSession("tabId", "example.com")).thenReturn(null)
        whenever(sitePermissionsRepository.isDrmBlockedForUrlByConfig("https://example.com")).thenReturn(true)

        val activity: Activity = mock()
        val request: PermissionRequest = mock()
        whenever(request.resources).thenReturn(arrayOf(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID))

        testee.askForSitePermission(
            activity = activity,
            url = "https://example.com",
            tabId = "tabId",
            permissionsRequested = SitePermissions(
                autoAccept = emptyList(),
                userHandled = listOf(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID),
            ),
            request = request,
            permissionsGrantedListener = permissionsGrantedListener,
        )

        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenDuckAiRequestsAudioCaptureAndMicNotGrantedThenRequestsMicPermissionWithoutPersisting() {
        whenever(systemPermissionsHelper.hasMicPermissionsGranted()).thenReturn(false)

        val activity: Activity = mock()
        val request: PermissionRequest = mock()
        whenever(request.resources).thenReturn(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
        whenever(request.origin).thenReturn(Uri.parse("https://duck.ai"))

        testee.askForSitePermission(
            activity = activity,
            url = "https://duck.ai",
            tabId = "tabId",
            permissionsRequested = SitePermissions(
                autoAccept = emptyList(),
                userHandled = listOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE),
            ),
            request = request,
            permissionsGrantedListener = permissionsGrantedListener,
        )

        // No impression pixel — no dialog shown for duck.ai
        verifyNoMoreInteractions(pixel)
        verify(systemPermissionsHelper).requestMultiplePermissions(
            arrayOf(android.Manifest.permission.RECORD_AUDIO, android.Manifest.permission.MODIFY_AUDIO_SETTINGS),
        )
    }

    @Test
    fun whenDuckAiRequestsAudioCaptureAndMicGrantedThenGrantsDirectlyWithoutDialog() {
        whenever(systemPermissionsHelper.hasMicPermissionsGranted()).thenReturn(true)

        val activity: Activity = mock()
        val request: PermissionRequest = mock()
        whenever(request.resources).thenReturn(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
        whenever(request.origin).thenReturn(Uri.parse("https://duck.ai"))

        testee.askForSitePermission(
            activity = activity,
            url = "https://duck.ai",
            tabId = "tabId",
            permissionsRequested = SitePermissions(
                autoAccept = emptyList(),
                userHandled = listOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE),
            ),
            request = request,
            permissionsGrantedListener = permissionsGrantedListener,
        )

        // No impression pixel — no dialog shown for duck.ai
        verifyNoMoreInteractions(pixel)
        // System permission not re-requested (already granted)
        verify(systemPermissionsHelper, never()).requestMultiplePermissions(com.nhaarman.mockitokotlin2.any())
    }

    @Test
    fun whenRegularModeAndPermissionGrantedThenPersistedAndWebViewGranted() {
        whenever(systemPermissionsHelper.hasMicPermissionsGranted()).thenReturn(true)

        val activity: Activity = mock()
        val request: PermissionRequest = mock()
        whenever(request.resources).thenReturn(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
        whenever(request.origin).thenReturn(Uri.parse("https://duck.ai"))

        testee.askForSitePermission(
            activity = activity,
            url = "https://duck.ai",
            tabId = "tabId",
            permissionsRequested = SitePermissions(
                autoAccept = emptyList(),
                userHandled = listOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE),
            ),
            request = request,
            permissionsGrantedListener = permissionsGrantedListener,
        )

        // WebView permission granted in-session
        verify(request).grant(any())
        // And the grant is persisted into the shared store
        verify(sitePermissionsRepository).sitePermissionGranted("https://duck.ai", "tabId", PermissionRequest.RESOURCE_AUDIO_CAPTURE)
    }

    @Test
    fun whenFireModeAndPermissionGrantedThenWebViewGrantedButNotPersisted() = runTest {
        val fireLauncher = createLauncher(BrowserMode.FIRE)
        whenever(systemPermissionsHelper.hasMicPermissionsGranted()).thenReturn(true)

        val activity: Activity = mock()
        val request: PermissionRequest = mock()
        whenever(request.resources).thenReturn(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
        whenever(request.origin).thenReturn(Uri.parse("https://duck.ai"))

        fireLauncher.askForSitePermission(
            activity = activity,
            url = "https://duck.ai",
            tabId = "tabId",
            permissionsRequested = SitePermissions(
                autoAccept = emptyList(),
                userHandled = listOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE),
            ),
            request = request,
            permissionsGrantedListener = permissionsGrantedListener,
        )

        // WebView permission still granted in-session so the page works
        verify(request).grant(any())
        // But nothing is persisted into the shared store in Fire mode
        verify(sitePermissionsRepository, never()).sitePermissionGranted(any(), any(), any())
        verify(sitePermissionsRepository, never()).sitePermissionPermanentlySaved(any(), any(), any())
        verify(sitePermissionsRepository, never()).savePermission(any())
    }

    @Test
    fun whenDrmLearnMoreClickedThenDialogDismissedAndPermissionDenied() {
        whenever(sitePermissionsRepository.getDrmForSession("tabId", "example.com")).thenReturn(null)
        whenever(sitePermissionsRepository.isDrmBlockedForUrlByConfig(any())).thenReturn(false)

        val activity = Robolectric.buildActivity(ThemedActivity::class.java).setup().get()
        val request: PermissionRequest = mock()
        whenever(request.resources).thenReturn(arrayOf(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID))

        testee.askForSitePermission(
            activity = activity,
            url = "https://example.com",
            tabId = "tabId",
            permissionsRequested = SitePermissions(
                autoAccept = emptyList(),
                userHandled = listOf(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID),
            ),
            request = request,
            permissionsGrantedListener = permissionsGrantedListener,
        )

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        val message = dialog.findViewById<TextView>(CommonR.id.textAlertDialogMessage)!!
        val learnMore = (message.text as Spanned).getSpans(0, message.text.length, ClickableSpan::class.java).first()

        learnMore.onClick(message)

        assertFalse(dialog.isShowing)
        verify(request).deny()
    }

    private fun showTieredCameraDialog(): AlertDialog {
        sitePermissionsDialogRedesignFeature.self().setRawStoredState(Toggle.State(true))
        whenever(systemPermissionsHelper.hasCameraPermissionsGranted()).thenReturn(true)

        val activity = Robolectric.buildActivity(ThemedActivity::class.java).setup().get()
        val request: PermissionRequest = mock()
        whenever(request.resources).thenReturn(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
        whenever(request.origin).thenReturn(Uri.parse("https://example.com"))

        testee.askForSitePermission(
            activity = activity,
            url = "https://example.com",
            tabId = "tabId",
            permissionsRequested = SitePermissions(
                autoAccept = emptyList(),
                userHandled = listOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE),
            ),
            request = request,
            permissionsGrantedListener = permissionsGrantedListener,
        )
        this.request = request
        return ShadowDialog.getLatestDialog() as AlertDialog
    }

    private lateinit var request: PermissionRequest

    private fun AlertDialog.tieredButtons() =
        findViewById<LinearLayout>(CommonR.id.stackedAlertDialogButtonLayout)!!

    @Test
    fun whenRedesignEnabledThenDialogOffersThreeTiersAndNoRememberChoiceCheckbox() {
        val dialog = showTieredCameraDialog()

        assertEquals(3, dialog.tieredButtons().childCount)
        assertNull(dialog.findViewById<CheckBox>(CommonR.id.textAlertDialogCheckBox))
    }

    @Test
    fun whenRedesignDisabledThenLegacyDialogShown() {
        sitePermissionsDialogRedesignFeature.self().setRawStoredState(Toggle.State(false))
        val activity = Robolectric.buildActivity(ThemedActivity::class.java).setup().get()
        val request: PermissionRequest = mock()
        whenever(request.resources).thenReturn(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
        whenever(request.origin).thenReturn(Uri.parse("https://example.com"))

        testee.askForSitePermission(
            activity = activity,
            url = "https://example.com",
            tabId = "tabId",
            permissionsRequested = SitePermissions(
                autoAccept = emptyList(),
                userHandled = listOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE),
            ),
            request = request,
            permissionsGrantedListener = permissionsGrantedListener,
        )

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        assertNotNull(dialog.findViewById<TextView>(CommonR.id.textAlertDialogMessage))
        assertNull(dialog.findViewById<LinearLayout>(CommonR.id.stackedAlertDialogButtonLayout))
    }

    @Test
    fun whenAllowWhileUsingSiteClickedThenPermissionPersistedAsAlwaysAllow() {
        val dialog = showTieredCameraDialog()

        dialog.tieredButtons().getChildAt(0).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        verify(request).grant(any())
        verify(request, never()).deny()
        verify(sitePermissionsRepository).sitePermissionPermanentlySaved(
            "https://example.com",
            PermissionRequest.RESOURCE_VIDEO_CAPTURE,
            ALLOW_ALWAYS,
        )
    }

    @Test
    fun whenAllowThisTimeClickedThenGrantIsSessionOnly() {
        val dialog = showTieredCameraDialog()

        dialog.tieredButtons().getChildAt(1).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        verify(request).grant(any())
        verify(request, never()).deny()
        verify(sitePermissionsRepository).sitePermissionGranted(
            "https://example.com",
            "tabId",
            PermissionRequest.RESOURCE_VIDEO_CAPTURE,
        )
        verify(sitePermissionsRepository, never()).sitePermissionPermanentlySaved(any(), any(), any())
    }

    @Test
    fun whenNeverAllowClickedThenDeniedAndPersistedAsDenyAlways() {
        val dialog = showTieredCameraDialog()

        dialog.tieredButtons().getChildAt(2).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        verify(request, times(1)).deny()
        verify(request, never()).grant(any())
        verify(sitePermissionsRepository).sitePermissionPermanentlySaved(
            "https://example.com",
            PermissionRequest.RESOURCE_VIDEO_CAPTURE,
            DENY_ALWAYS,
        )
    }

    @Test
    fun whenDialogDismissedThenDeniedForThisRequestOnlyAndReportedAsDenyOnce() {
        val dialog = showTieredCameraDialog()

        dialog.cancel()
        // Dialog.cancel() dispatches onCancel through a Handler message, which Robolectric's
        // paused looper will not run on its own.
        shadowOf(Looper.getMainLooper()).idle()

        verify(request).deny()
        verify(sitePermissionsRepository, never()).sitePermissionPermanentlySaved(any(), any(), any())
        verify(sitePermissionsRepository, never()).sitePermissionGranted(any(), any(), any())
        verify(pixel).fire(
            SitePermissionsPixelName.PERMISSION_DIALOG_CLICK,
            mapOf(
                SitePermissionsPixelParameters.PERMISSION_TYPE to SitePermissionsPixelValues.CAMERA,
                SitePermissionsPixelParameters.PERMISSION_SELECTION to SitePermissionsPixelValues.DENY_ONCE,
            ),
        )
    }

    @Test
    fun whenNeverAllowClickedAndOtherPermissionAutoAcceptedThenOnlyTheOfferedPermissionIsDenied() {
        sitePermissionsDialogRedesignFeature.self().setRawStoredState(Toggle.State(true))

        val activity = Robolectric.buildActivity(ThemedActivity::class.java).setup().get()
        val request: PermissionRequest = mock()
        whenever(request.resources).thenReturn(
            arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE, PermissionRequest.RESOURCE_AUDIO_CAPTURE),
        )
        whenever(request.origin).thenReturn(Uri.parse("https://example.com"))

        testee.askForSitePermission(
            activity = activity,
            url = "https://example.com",
            tabId = "tabId",
            permissionsRequested = SitePermissions(
                autoAccept = listOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE),
                userHandled = listOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE),
            ),
            request = request,
            permissionsGrantedListener = permissionsGrantedListener,
        )

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        dialog.findViewById<LinearLayout>(CommonR.id.stackedAlertDialogButtonLayout)!!.getChildAt(2).performClick()

        verify(request).grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
        verify(sitePermissionsRepository).sitePermissionPermanentlySaved(
            "https://example.com",
            PermissionRequest.RESOURCE_AUDIO_CAPTURE,
            DENY_ALWAYS,
        )
        verify(sitePermissionsRepository, never()).sitePermissionPermanentlySaved(
            any(),
            eq(PermissionRequest.RESOURCE_VIDEO_CAPTURE),
            any(),
        )
    }

    @Test
    fun whenSystemPermissionPermanentlyDeniedThenAllowChoiceIsNotSavedAsNeverAllow() {
        sitePermissionsDialogRedesignFeature.self().setRawStoredState(Toggle.State(true))
        whenever(systemPermissionsHelper.hasCameraPermissionsGranted()).thenReturn(false)
        whenever(systemPermissionsHelper.isPermissionsRejectedForever(any())).thenReturn(true)

        val onSystemResult = argumentCaptor<(Boolean) -> Unit>()
        testee.registerPermissionLauncher(mock())
        verify(systemPermissionsHelper).registerPermissionLaunchers(any(), onSystemResult.capture(), any())

        val activity = Robolectric.buildActivity(ThemedActivity::class.java).setup().get()
        val request: PermissionRequest = mock()
        whenever(request.resources).thenReturn(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
        whenever(request.origin).thenReturn(Uri.parse("https://example.com"))

        testee.askForSitePermission(
            activity = activity,
            url = "https://example.com",
            tabId = "tabId",
            permissionsRequested = SitePermissions(
                autoAccept = emptyList(),
                userHandled = listOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE),
            ),
            request = request,
            permissionsGrantedListener = permissionsGrantedListener,
        )

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        dialog.findViewById<LinearLayout>(CommonR.id.stackedAlertDialogButtonLayout)!!.getChildAt(0).performClick()

        onSystemResult.firstValue.invoke(false)

        verify(request).deny()
        verify(sitePermissionsRepository, never()).sitePermissionPermanentlySaved(any(), any(), eq(DENY_ALWAYS))
    }

    private fun showTieredLocationDialog(origin: String): AlertDialog {
        sitePermissionsDialogRedesignFeature.self().setRawStoredState(Toggle.State(true))

        val activity = Robolectric.buildActivity(ThemedActivity::class.java).setup().get()
        val request = LocationPermissionRequest(origin, mock())

        testee.askForSitePermission(
            activity = activity,
            url = origin,
            tabId = "tabId",
            permissionsRequested = SitePermissions(
                autoAccept = emptyList(),
                userHandled = listOf(LocationPermissionRequest.RESOURCE_LOCATION_PERMISSION),
            ),
            request = request,
            permissionsGrantedListener = permissionsGrantedListener,
        )
        return ShadowDialog.getLatestDialog() as AlertDialog
    }

    @Test
    fun whenLocationRequestedBySiteThenTieredDialogShownWithoutSubtitle() {
        val dialog = showTieredLocationDialog("https://example.com/")

        assertEquals(3, dialog.findViewById<LinearLayout>(CommonR.id.stackedAlertDialogButtonLayout)!!.childCount)
        assertEquals(View.GONE, dialog.findViewById<TextView>(CommonR.id.stackedlertDialogMessage)!!.visibility)
    }

    @Test
    fun whenLocationRequestedByDuckDuckGoThenTieredDialogKeepsAnonymisationSubtitle() {
        val dialog = showTieredLocationDialog("https://duckduckgo.com/")

        val message = dialog.findViewById<TextView>(CommonR.id.stackedlertDialogMessage)!!
        assertEquals(View.VISIBLE, message.visibility)
        assertEquals(
            dialog.context.getString(R.string.sitePermissionsTieredDdgLocationDialogSubtitle),
            message.text.toString(),
        )
    }

    @Test
    fun whenFireModeAndNeverAllowClickedThenDeniedButNothingPersisted() {
        val fireLauncher = createLauncher(BrowserMode.FIRE)
        sitePermissionsDialogRedesignFeature.self().setRawStoredState(Toggle.State(true))

        val activity = Robolectric.buildActivity(ThemedActivity::class.java).setup().get()
        val request: PermissionRequest = mock()
        whenever(request.resources).thenReturn(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
        whenever(request.origin).thenReturn(Uri.parse("https://example.com"))

        fireLauncher.askForSitePermission(
            activity = activity,
            url = "https://example.com",
            tabId = "tabId",
            permissionsRequested = SitePermissions(
                autoAccept = emptyList(),
                userHandled = listOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE),
            ),
            request = request,
            permissionsGrantedListener = permissionsGrantedListener,
        )

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        dialog.findViewById<LinearLayout>(CommonR.id.stackedAlertDialogButtonLayout)!!.getChildAt(2).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        verify(request).deny()
        verify(sitePermissionsRepository, never()).sitePermissionPermanentlySaved(any(), any(), any())
    }

    @Test
    fun whenFireModeAndAllowWhileUsingSiteClickedThenGrantedButNothingPersisted() {
        val fireLauncher = createLauncher(BrowserMode.FIRE)
        sitePermissionsDialogRedesignFeature.self().setRawStoredState(Toggle.State(true))
        whenever(systemPermissionsHelper.hasCameraPermissionsGranted()).thenReturn(true)

        val activity = Robolectric.buildActivity(ThemedActivity::class.java).setup().get()
        val request: PermissionRequest = mock()
        whenever(request.resources).thenReturn(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
        whenever(request.origin).thenReturn(Uri.parse("https://example.com"))

        fireLauncher.askForSitePermission(
            activity = activity,
            url = "https://example.com",
            tabId = "tabId",
            permissionsRequested = SitePermissions(
                autoAccept = emptyList(),
                userHandled = listOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE),
            ),
            request = request,
            permissionsGrantedListener = permissionsGrantedListener,
        )

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        dialog.findViewById<LinearLayout>(CommonR.id.stackedAlertDialogButtonLayout)!!.getChildAt(0).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        verify(request).grant(any())
        verify(sitePermissionsRepository, never()).sitePermissionPermanentlySaved(any(), any(), any())
        verify(sitePermissionsRepository, never()).sitePermissionGranted(any(), any(), any())
    }

    private fun showDrmDialog(
        launcher: SitePermissionsDialogActivityLauncher = testee,
        redesignEnabled: Boolean = true,
    ): AlertDialog {
        sitePermissionsDialogRedesignFeature.self().setRawStoredState(Toggle.State(redesignEnabled))
        whenever(sitePermissionsRepository.getDrmForSession("tabId", "example.com")).thenReturn(null)
        whenever(sitePermissionsRepository.isDrmBlockedForUrlByConfig(any())).thenReturn(false)

        val activity = Robolectric.buildActivity(ThemedActivity::class.java).setup().get()
        val request: PermissionRequest = mock()
        whenever(request.resources).thenReturn(arrayOf(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID))
        whenever(request.origin).thenReturn(Uri.parse("https://example.com"))

        launcher.askForSitePermission(
            activity = activity,
            url = "https://example.com",
            tabId = "tabId",
            permissionsRequested = SitePermissions(
                autoAccept = emptyList(),
                userHandled = listOf(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID),
            ),
            request = request,
            permissionsGrantedListener = permissionsGrantedListener,
        )
        this.request = request
        return ShadowDialog.getLatestDialog() as AlertDialog
    }

    @Test
    fun whenRedesignEnabledThenDrmDialogOffersThreeTiersWithoutLearnMoreLink() {
        val dialog = showDrmDialog()

        assertEquals(3, dialog.tieredButtons().childCount)
        val message = dialog.findViewById<TextView>(CommonR.id.stackedlertDialogMessage)!!
        assertEquals(
            dialog.context.getString(R.string.sitePermissionsTieredDrmDialogSubtitle),
            message.text.toString(),
        )
    }

    @Test
    fun whenRedesignDisabledThenLegacyDrmDialogShown() {
        val dialog = showDrmDialog(redesignEnabled = false)

        assertNotNull(dialog.findViewById<TextView>(CommonR.id.textAlertDialogMessage))
        assertNull(dialog.findViewById<LinearLayout>(CommonR.id.stackedAlertDialogButtonLayout))
    }

    @Test
    fun whenDrmAllowWhileUsingSiteClickedThenPersistedWithoutReplacingOtherSitePermissions() = runTest {
        val dialog = showDrmDialog()

        dialog.tieredButtons().getChildAt(0).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        verify(request).grant(any())
        verify(sitePermissionsRepository).sitePermissionPermanentlySaved(
            "https://example.com",
            PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID,
            ALLOW_ALWAYS,
        )
        verify(sitePermissionsRepository, never()).savePermission(any())
        verify(sitePermissionsRepository, never()).saveDrmForSession(any(), any(), any())
    }

    @Test
    fun whenDrmAllowThisTimeClickedThenGrantIsSessionOnly() {
        val dialog = showDrmDialog()

        dialog.tieredButtons().getChildAt(1).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        verify(request).grant(any())
        verify(sitePermissionsRepository).saveDrmForSession("tabId", "example.com", true)
        verify(sitePermissionsRepository, never()).sitePermissionPermanentlySaved(any(), any(), any())
    }

    @Test
    fun whenDrmNeverAllowClickedThenPersistedWithoutReplacingOtherSitePermissions() = runTest {
        val dialog = showDrmDialog()

        dialog.tieredButtons().getChildAt(2).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        verify(request).deny()
        verify(sitePermissionsRepository).sitePermissionPermanentlySaved(
            "https://example.com",
            PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID,
            DENY_ALWAYS,
        )
        verify(sitePermissionsRepository, never()).savePermission(any())
        verify(sitePermissionsRepository, never()).saveDrmForSession(any(), any(), any())
    }

    @Test
    fun whenDrmDialogDismissedThenDeniedForTheTabSession() {
        val dialog = showDrmDialog()

        dialog.cancel()
        shadowOf(Looper.getMainLooper()).idle()

        verify(request).deny()
        verify(sitePermissionsRepository).saveDrmForSession("tabId", "example.com", false)
        verify(sitePermissionsRepository, never()).sitePermissionPermanentlySaved(any(), any(), any())
    }

    @Test
    fun whenFireModeAndDrmDialogDismissedThenDeniedButNoSessionChoiceStored() {
        val dialog = showDrmDialog(createLauncher(BrowserMode.FIRE))

        dialog.cancel()
        shadowOf(Looper.getMainLooper()).idle()

        verify(request).deny()
        verify(sitePermissionsRepository, never()).saveDrmForSession(any(), any(), any())
    }

    @Test
    fun whenFireModeAndDrmAllowClickedThenGrantedButNothingPersisted() {
        val dialog = showDrmDialog(createLauncher(BrowserMode.FIRE))

        dialog.tieredButtons().getChildAt(0).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        verify(request).grant(any())
        verify(sitePermissionsRepository, never()).sitePermissionPermanentlySaved(any(), any(), any())
        verify(sitePermissionsRepository, never()).saveDrmForSession(any(), any(), any())
    }

    class ThemedActivity : AppCompatActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            setTheme(CommonR.style.Theme_DuckDuckGo_Light)
            super.onCreate(savedInstanceState)
        }
    }
}
