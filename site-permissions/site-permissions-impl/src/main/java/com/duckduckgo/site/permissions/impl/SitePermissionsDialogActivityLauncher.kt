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

package com.duckduckgo.site.permissions.impl

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.provider.Settings
import android.view.ViewGroup
import android.webkit.PermissionRequest
import androidx.activity.result.ActivityResultCaller
import androidx.annotation.StringRes
import androidx.core.net.toUri
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.ui.view.button.ButtonType.GHOST
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.extensions.websiteFromGeoLocationsApiOrigin
import com.duckduckgo.common.utils.extractDomain
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.site.permissions.api.SitePermissionsDialogLauncher
import com.duckduckgo.site.permissions.api.SitePermissionsGrantedListener
import com.duckduckgo.site.permissions.api.SitePermissionsManager.LocationPermissionRequest
import com.duckduckgo.site.permissions.api.SitePermissionsManager.SitePermissions
import com.duckduckgo.site.permissions.store.sitepermissions.SitePermissionAskSettingType
import com.duckduckgo.site.permissions.store.sitepermissions.SitePermissionAskSettingType.ALLOW_ALWAYS
import com.duckduckgo.site.permissions.store.sitepermissions.SitePermissionAskSettingType.DENY_ALWAYS
import com.duckduckgo.site.permissions.store.sitepermissions.SitePermissionsEntity
import com.google.android.material.snackbar.BaseTransientBottomBar.BaseCallback
import com.google.android.material.snackbar.Snackbar
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority.WARN
import logcat.logcat
import java.lang.IllegalStateException
import javax.inject.Inject

@ContributesBinding(FragmentScope::class)
class SitePermissionsDialogActivityLauncher @Inject constructor(
    private val systemPermissionsHelper: SystemPermissionsHelper,
    private val sitePermissionsRepository: SitePermissionsRepository,
    private val faviconManager: FaviconManager,
    private val pixel: Pixel,
    private val dispatcher: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : SitePermissionsDialogLauncher {

    private lateinit var sitePermissionRequest: PermissionRequest
    private lateinit var activity: Activity
    private lateinit var permissionRequested: SitePermissionsRequestedType
    private var permissionPermanent: Boolean = false
    private lateinit var permissionsGrantedListener: SitePermissionsGrantedListener
    private lateinit var permissionsHandledByUser: List<String>
    private lateinit var permissionsHandledAutomatically: List<String>
    private var siteURL: String = ""
    private var tabId: String = ""

    override fun registerPermissionLauncher(caller: ActivityResultCaller) {
        systemPermissionsHelper.registerPermissionLaunchers(
            caller,
            this::onResultSystemPermissionRequest,
            this::onResultMultipleSystemPermissionsRequest,
        )
    }

    override fun askForSitePermission(
        activity: Activity,
        url: String,
        tabId: String,
        permissionsRequested: SitePermissions,
        request: PermissionRequest,
        permissionsGrantedListener: SitePermissionsGrantedListener,
    ) {
        logcat { "Permissions: permission askForSitePermission $permissionsRequested" }
        sitePermissionRequest = request
        siteURL = url
        this.tabId = tabId
        this.activity = activity
        this.permissionsGrantedListener = permissionsGrantedListener
        permissionsHandledByUser = permissionsRequested.userHandled
        permissionsHandledAutomatically = permissionsRequested.autoAccept

        when {
            permissionsHandledByUser.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE) && permissionsHandledByUser.contains(
                PermissionRequest.RESOURCE_AUDIO_CAPTURE,
            ) -> {
                showSitePermissionsRationaleDialog(
                    R.string.sitePermissionsMicAndCameraDialogTitle,
                    R.string.sitePermissionsMicAndCameraDialogSubtitle,
                    url,
                    SitePermissionsPixelValues.CAMERA_AND_MICROPHONE,
                    { rememberChoice ->
                        askForMicAndCameraPermissions(rememberChoice)
                    },
                )
            }

            permissionsHandledByUser.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE) -> {
                showSitePermissionsRationaleDialog(
                    R.string.sitePermissionsMicDialogTitle,
                    R.string.sitePermissionsMicDialogSubtitle,
                    url,
                    SitePermissionsPixelValues.MICROPHONE,
                    { rememberChoice ->
                        askForMicPermissions(rememberChoice)
                    },
                )
            }

            permissionsHandledByUser.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE) -> {
                showSitePermissionsRationaleDialog(
                    R.string.sitePermissionsCameraDialogTitle,
                    R.string.sitePermissionsCameraDialogSubtitle,
                    url,
                    SitePermissionsPixelValues.CAMERA,
                    { rememberChoice ->
                        askForCameraPermissions(rememberChoice)
                    },
                )
            }

            permissionsHandledByUser.contains(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID) -> {
                showSiteDrmPermissionsDialog(activity, url)
            }

            permissionsHandledByUser.contains(LocationPermissionRequest.RESOURCE_LOCATION_PERMISSION) -> {
                showSiteLocationPermissionDialog(activity, request as LocationPermissionRequest, tabId)
            }
        }
    }

    private fun showSiteLocationPermissionDialog(
        activity: Activity,
        locationPermissionRequest: LocationPermissionRequest,
        tabId: String,
    ) {
        sendDialogImpressionPixel(SitePermissionsPixelValues.LOCATION)
        this.tabId = tabId
        this.activity = activity

        val domain = locationPermissionRequest.origin.websiteFromGeoLocationsApiOrigin()

        val subtitle = if (domain == "duckduckgo.com") {
            R.string.preciseLocationDDGDialogSubtitle
        } else {
            R.string.preciseLocationSiteDialogSubtitle
        }

        TextAlertDialogBuilder(activity)
            .setTitle(
                String.format(activity.getString(R.string.sitePermissionsLocationDialogTitle), domain),
            )
            .setMessage(subtitle)
            .setPositiveButton(R.string.sitePermissionsDialogAllowButton, GHOST)
            .setNegativeButton(R.string.sitePermissionsDialogDenyButton, GHOST)
            .setCheckBoxText(R.string.sitePermissionsDialogRememberMeCheckBox)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    var rememberChoice = false
                    override fun onPositiveButtonClicked() {
                        if (rememberChoice) {
                            storeFavicon(locationPermissionRequest.origin)
                        }
                        sendPositiveDialogClickPixel(SitePermissionsPixelValues.LOCATION, rememberChoice)
                        askForLocationPermissions(rememberChoice)
                    }

                    override fun onNegativeButtonClicked() {
                        if (rememberChoice) {
                            storeFavicon(locationPermissionRequest.origin)
                        }
                        sendNegativeDialogClickPixel(SitePermissionsPixelValues.LOCATION, rememberChoice)
                        denyPermissions(rememberChoice)
                    }

                    override fun onCheckedChanged(checked: Boolean) {
                        rememberChoice = checked
                    }
                },
            )
            .show()
    }

    private fun showSitePermissionsRationaleDialog(
        @StringRes titleRes: Int,
        @StringRes messageRes: Int,
        url: String,
        pixelType: String,
        onPermissionAllowed: (Boolean) -> Unit,
    ) {
        sendDialogImpressionPixel(pixelType)
        TextAlertDialogBuilder(activity)
            .setTitle(String.format(activity.getString(titleRes), url.websiteFromGeoLocationsApiOrigin()))
            .setMessage(messageRes)
            .setPositiveButton(R.string.sitePermissionsDialogAllowButton, GHOST)
            .setNegativeButton(R.string.sitePermissionsDialogDenyButton, GHOST)
            .setCheckBoxText(R.string.sitePermissionsDialogRememberMeCheckBox)
            .addEventListener(

                object : TextAlertDialogBuilder.EventListener() {
                    var rememberChoice = false
                    override fun onPositiveButtonClicked() {
                        onPermissionAllowed(rememberChoice)
                        sendPositiveDialogClickPixel(rememberChoice = rememberChoice, type = pixelType)
                    }

                    override fun onNegativeButtonClicked() {
                        denyPermissions(rememberChoice)
                        sendNegativeDialogClickPixel(rememberChoice = rememberChoice, type = pixelType)
                    }

                    override fun onCheckedChanged(checked: Boolean) {
                        rememberChoice = checked
                    }
                },
            )
            .show()
    }

    private fun showSiteDrmPermissionsDialog(
        activity: Activity,
        url: String,
    ) {
        sendDialogImpressionPixel(SitePermissionsPixelValues.DRM)
        val domain = url.extractDomain() ?: url

        // Check if user allowed or denied per session
        val sessionSetting = sitePermissionsRepository.getDrmForSession(domain)
        if (sessionSetting != null) {
            if (sessionSetting) {
                grantPermissions()
            } else {
                denyPermissions()
            }
            return
        }

        // No session-based setting --> check if DRM blocked by config
        if (sitePermissionsRepository.isDrmBlockedForUrlByConfig(url)) {
            denyPermissions()
            return
        }

        // No session-based setting and no config --> proceed to show dialog
        val title = url.websiteFromGeoLocationsApiOrigin()
        TextAlertDialogBuilder(activity)
            .setTitle(
                String.format(
                    activity.getString(R.string.drmSiteDialogTitle),
                    title,
                ),
            )
            .setClickableMessage(
                activity.getText(R.string.drmSiteDialogSubtitle),
                DRM_LEARN_MORE_ANNOTATION,
            ) {
                denyPermissions()
                activity.startActivity(Intent(Intent.ACTION_VIEW, DRM_LEARN_MORE_URL))
            }
            .setPositiveButton(R.string.sitePermissionsDialogAllowButton, GHOST)
            .setNegativeButton(R.string.sitePermissionsDialogDenyButton, GHOST)
            .setCheckBoxText(R.string.sitePermissionsDialogRememberMeCheckBox)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {

                    var rememberChoice = false

                    override fun onPositiveButtonClicked() {
                        if (rememberChoice) {
                            grantPermissions()
                            onSiteDrmPermissionSave(domain, SitePermissionAskSettingType.ALLOW_ALWAYS)
                            storeFavicon(url)
                        } else {
                            sitePermissionsRepository.saveDrmForSession(domain, true)
                            grantPermissions()
                        }
                        sendPositiveDialogClickPixel(SitePermissionsPixelValues.DRM, rememberChoice)
                    }

                    override fun onNegativeButtonClicked() {
                        denyPermissions(rememberChoice)
                        if (rememberChoice) {
                            onSiteDrmPermissionSave(domain, SitePermissionAskSettingType.DENY_ALWAYS)
                            storeFavicon(url)
                        } else {
                            sitePermissionsRepository.saveDrmForSession(domain, false)
                        }
                        sendNegativeDialogClickPixel(SitePermissionsPixelValues.DRM, rememberChoice)
                    }

                    override fun onCheckedChanged(checked: Boolean) {
                        rememberChoice = checked
                    }
                },
            )
            .show()
    }

    private fun onSiteDrmPermissionSave(
        domain: String,
        drmPermission: SitePermissionAskSettingType,
    ) {
        val sitePermissionsEntity = SitePermissionsEntity(
            domain = domain,
            askDrmSetting = drmPermission.name,
        )

        appCoroutineScope.launch(dispatcher.io()) {
            sitePermissionsRepository.savePermission(sitePermissionsEntity)
        }
    }

    private fun sendDialogImpressionPixel(type: String) {
        pixel.fire(
            SitePermissionsPixelName.PERMISSION_DIALOG_IMPRESSION,
            mapOf(SitePermissionsPixelParameters.PERMISSION_TYPE to type),
        )
    }

    private fun sendNegativeDialogClickPixel(
        type: String,
        rememberChoice: Boolean,
    ) {
        val selection = if (rememberChoice) {
            SitePermissionsPixelValues.DENY_ALWAYS
        } else {
            SitePermissionsPixelValues.DENY_ONCE
        }
        pixel.fire(
            SitePermissionsPixelName.PERMISSION_DIALOG_CLICK,
            mapOf(
                SitePermissionsPixelParameters.PERMISSION_TYPE to type,
                SitePermissionsPixelParameters.PERMISSION_SELECTION to selection,
            ),
        )
    }

    private fun sendPositiveDialogClickPixel(
        type: String,
        rememberChoice: Boolean,
    ) {
        val selection = if (rememberChoice) {
            SitePermissionsPixelValues.ALLOW_ALWAYS
        } else {
            SitePermissionsPixelValues.ALLOW_ONCE
        }
        pixel.fire(
            SitePermissionsPixelName.PERMISSION_DIALOG_CLICK,
            mapOf(
                SitePermissionsPixelParameters.PERMISSION_TYPE to type,
                SitePermissionsPixelParameters.PERMISSION_SELECTION to selection,
            ),
        )
    }

    private fun askForMicAndCameraPermissions(rememberChoice: Boolean) {
        permissionRequested = SitePermissionsRequestedType.CAMERA_AND_AUDIO
        permissionPermanent = rememberChoice
        when {
            systemPermissionsHelper.hasMicPermissionsGranted() && systemPermissionsHelper.hasCameraPermissionsGranted() -> {
                systemPermissionGranted()
            }

            systemPermissionsHelper.hasMicPermissionsGranted() -> {
                systemPermissionsHelper.requestPermission(Manifest.permission.CAMERA)
            }

            systemPermissionsHelper.hasCameraPermissionsGranted() -> {
                systemPermissionsHelper.requestMultiplePermissions(
                    arrayOf(
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.MODIFY_AUDIO_SETTINGS,
                    ),
                )
            }

            else -> {
                systemPermissionsHelper.requestMultiplePermissions(
                    arrayOf(
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.MODIFY_AUDIO_SETTINGS,
                        Manifest.permission.CAMERA,
                    ),
                )
            }
        }
    }

    private fun askForMicPermissions(rememberChoice: Boolean = false) {
        permissionRequested = SitePermissionsRequestedType.AUDIO
        permissionPermanent = rememberChoice
        if (systemPermissionsHelper.hasMicPermissionsGranted()) {
            systemPermissionGranted()
        } else {
            systemPermissionsHelper.requestMultiplePermissions(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.MODIFY_AUDIO_SETTINGS))
        }
    }

    private fun askForCameraPermissions(rememberChoice: Boolean = false) {
        permissionRequested = SitePermissionsRequestedType.CAMERA
        permissionPermanent = rememberChoice
        if (systemPermissionsHelper.hasCameraPermissionsGranted()) {
            systemPermissionGranted()
        } else {
            systemPermissionsHelper.requestPermission(Manifest.permission.CAMERA)
        }
    }

    private fun askForLocationPermissions(rememberChoice: Boolean = false) {
        permissionRequested = SitePermissionsRequestedType.LOCATION
        permissionPermanent = rememberChoice
        if (systemPermissionsHelper.hasLocationPermissionsGranted()) {
            systemPermissionGranted()
        } else {
            systemPermissionsHelper.requestMultiplePermissions(
                // ACCESS_FINE_LOCATION is now considered optional, so ACCESS_COARSE_LOCATION should be first on the list,
                // because of how systemPermissionsHelper handles showing rationale dialog in case permissions are rejected.
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
            )
        }
    }

    private fun onResultSystemPermissionRequest(granted: Boolean) {
        when (granted) {
            true -> systemPermissionGranted()
            false -> systemPermissionDenied()
        }
    }

    private fun onResultMultipleSystemPermissionsRequest(grantedPermissions: Map<String, Boolean>) {
        if (grantedPermissions.values.contains(false)) {
            systemPermissionDenied()
        } else {
            systemPermissionGranted()
        }
    }

    private fun grantPermissions() {
        val permissions = permissionsHandledAutomatically.toTypedArray() + permissionsHandledByUser
        try {
            sitePermissionRequest.grant(permissions)
        } catch (e: IllegalStateException) {
            // IllegalStateException is thrown when grant() or deny() have been called already.
            logcat(WARN) { "IllegalStateException when calling grant() site permissions" }
        }
    }

    private fun systemPermissionGranted() {
        grantPermissions()
        permissionsHandledByUser.forEach {
            logcat(WARN) { "Permissions: sitePermission $it granted for $siteURL rememberChoice $permissionPermanent" }
            if (permissionPermanent) {
                sitePermissionsRepository.sitePermissionPermanentlySaved(siteURL, it, ALLOW_ALWAYS)
            } else {
                sitePermissionsRepository.sitePermissionGranted(siteURL, tabId, it)
            }
        }
        checkIfActionNeeded()
    }

    private fun checkIfActionNeeded() {
        when (siteURL.extractDomain().orEmpty()) {
            "whereby.com" -> permissionsGrantedListener.permissionsGrantedOnWhereby()
            else -> {
                // No action needed
            }
        }
    }

    private fun systemPermissionDenied() {
        when (systemPermissionsHelper.isPermissionsRejectedForever(activity)) {
            true -> showSystemPermissionsDeniedDialog()
            false -> showPermissionsDeniedSnackBar()
        }
    }

    private fun showPermissionsDeniedSnackBar(rememberChoice: Boolean = false) {
        val onPermissionAllowed: () -> Unit
        val message =
            when (permissionRequested) {
                SitePermissionsRequestedType.CAMERA -> {
                    onPermissionAllowed = {
                        askForCameraPermissions(rememberChoice)
                    }
                    R.string.sitePermissionsCameraDeniedSnackBarMessage
                }

                SitePermissionsRequestedType.AUDIO -> {
                    onPermissionAllowed = {
                        askForMicPermissions(rememberChoice)
                    }
                    R.string.sitePermissionsMicDeniedSnackBarMessage
                }

                SitePermissionsRequestedType.CAMERA_AND_AUDIO -> {
                    onPermissionAllowed = {
                        askForMicAndCameraPermissions(rememberChoice)
                    }
                    R.string.sitePermissionsCameraAndMicDeniedSnackBarMessage
                }

                SitePermissionsRequestedType.LOCATION -> {
                    onPermissionAllowed = {
                        askForLocationPermissions(rememberChoice)
                    }
                    R.string.sitePermissionsLocationDeniedSnackBarMessage
                }
            }

        val snackbar = Snackbar.make(activity.window.decorView.rootView, message, Snackbar.LENGTH_LONG)
        val snackbarView = snackbar.view
        if (activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            val layoutParams = snackbarView.layoutParams as ViewGroup.MarginLayoutParams
            layoutParams.setMargins(layoutParams.leftMargin, layoutParams.topMargin, layoutParams.rightMargin, 32.toPx())
            snackbarView.layoutParams = layoutParams
        }
        snackbar.apply {
            setAction(R.string.sitePermissionsDeniedSnackBarAction) { onPermissionAllowed() }
            addCallback(
                object : Snackbar.Callback() {
                    override fun onDismissed(
                        transientBottomBar: Snackbar?,
                        event: Int,
                    ) {
                        if (event == BaseCallback.DISMISS_EVENT_TIMEOUT) {
                            denyPermissions()
                        }
                    }
                },
            )
            show()
        }
    }

    private fun denyPermissions(rememberChoice: Boolean = false) {
        logcat(WARN) { "Permissions: sitePermission ${sitePermissionRequest.resources.asList()} denied for $siteURL rememberChoice $rememberChoice" }
        try {
            if (permissionsHandledAutomatically.isNotEmpty()) {
                sitePermissionRequest.grant(permissionsHandledAutomatically.toTypedArray())
            } else {
                sitePermissionRequest.deny()

                if (rememberChoice) {
                    sitePermissionRequest.resources.forEach { permission ->
                        sitePermissionsRepository.sitePermissionPermanentlySaved(
                            siteURL,
                            permission,
                            DENY_ALWAYS,
                        )
                    }
                }
            }
        } catch (e: IllegalStateException) {
            // IllegalStateException is thrown when grant() or deny() have been called already.
            logcat(WARN) { "IllegalStateException when calling grant() or deny() site permissions" }
        }
    }

    private fun showSystemPermissionsDeniedDialog() {
        denyPermissions(permissionPermanent)
        val titleRes = when (permissionRequested) {
            SitePermissionsRequestedType.CAMERA -> R.string.systemPermissionDialogCameraDeniedTitle
            SitePermissionsRequestedType.AUDIO -> R.string.systemPermissionDialogAudioDeniedTitle
            SitePermissionsRequestedType.CAMERA_AND_AUDIO -> R.string.systemPermissionDialogCameraAndAudioDeniedTitle
            SitePermissionsRequestedType.LOCATION -> R.string.systemPermissionDialogLocationDeniedTitle
        }
        val contentRes = when (permissionRequested) {
            SitePermissionsRequestedType.CAMERA -> R.string.systemPermissionDialogCameraDeniedContent
            SitePermissionsRequestedType.AUDIO -> R.string.systemPermissionDialogAudioDeniedContent
            SitePermissionsRequestedType.CAMERA_AND_AUDIO -> R.string.systemPermissionDialogCameraAndAudioDeniedContent
            SitePermissionsRequestedType.LOCATION -> R.string.systemPermissionDialogLocationDeniedContent
        }
        TextAlertDialogBuilder(activity)
            .setTitle(titleRes)
            .setMessage(contentRes)
            .setPositiveButton(R.string.systemPermissionsDeniedDialogPositiveButton)
            .setNegativeButton(R.string.systemPermissionsDeniedDialogNegativeButton)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        val intent = Intent()
                        intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                        val uri = Uri.fromParts("package", activity.packageName, null)
                        intent.data = uri
                        activity.startActivity(intent)
                    }
                },
            )
            .show()
    }

    private fun storeFavicon(url: String) {
        appCoroutineScope.launch {
            faviconManager.persistCachedFavicon(tabId, url)
        }
    }

    companion object {
        private const val DRM_LEARN_MORE_ANNOTATION = "drm_learn_more_link"
        val DRM_LEARN_MORE_URL = "https://duckduckgo.com/duckduckgo-help-pages/privacy/drm-permission/".toUri()
    }
}

enum class SitePermissionsRequestedType {
    CAMERA,
    AUDIO,
    CAMERA_AND_AUDIO,
    LOCATION,
}
