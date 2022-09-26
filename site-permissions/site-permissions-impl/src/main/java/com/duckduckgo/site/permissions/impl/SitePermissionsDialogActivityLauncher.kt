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
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.webkit.PermissionRequest
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.ActivityResultCaller
import androidx.annotation.StringRes
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.ui.view.toPx
import com.duckduckgo.site.permissions.api.SitePermissionsDialogLauncher
import com.duckduckgo.site.permissions.impl.R.layout
import com.duckduckgo.site.permissions.impl.pixels.SitePermissionsPixel.PixelParameter
import com.duckduckgo.site.permissions.impl.pixels.SitePermissionsPixel.PixelValue
import com.duckduckgo.site.permissions.impl.pixels.SitePermissionsPixel.SitePermissionsPixelName
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.snackbar.Snackbar.SnackbarLayout
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesBinding(ActivityScope::class)
@SingleInstanceIn(ActivityScope::class)
class SitePermissionsDialogActivityLauncher @Inject constructor(
    private val systemPermissionsHelper: SystemPermissionsHelper,
    private val sitePermissionsRepository: SitePermissionsRepository,
    private val faviconManager: FaviconManager,
    private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val pixel: Pixel
) : SitePermissionsDialogLauncher {

    private lateinit var sitePermissionRequest: PermissionRequest
    private lateinit var activity: Activity
    private lateinit var permissionRequested: SitePermissionsRequestedType
    private var siteURL: String = ""
    private var tabId: String = ""

    override fun registerPermissionLauncher(caller: ActivityResultCaller) {
        systemPermissionsHelper.registerPermissionLaunchers(
            caller,
            this::onResultSystemPermissionRequest,
            this::onResultMultipleSystemPermissionsRequest
        )
    }

    override fun askForSitePermission(
        activity: Activity,
        url: String,
        tabId: String,
        permissionsRequested: Array<String>,
        request: PermissionRequest
    ) {
        sitePermissionRequest = request
        siteURL = url
        this.tabId = tabId
        this.activity = activity

        when {
            permissionsRequested.size == 2 -> {
                showSitePermissionsRationaleDialog(
                    R.string.sitePermissionsMicAndCameraDialogTitle, url, this::askForMicAndCameraPermissions, PixelValue.BOTH
                )
            }
            permissionsRequested.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE) -> {
                showSitePermissionsRationaleDialog(R.string.sitePermissionsMicDialogTitle, url, this::askForMicPermissions, PixelValue.MIC)
            }
            permissionsRequested.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE) -> {
                showSitePermissionsRationaleDialog(R.string.sitePermissionsCameraDialogTitle, url, this::askForCameraPermissions, PixelValue.CAMERA)
            }
        }
    }

    private fun showSitePermissionsRationaleDialog(
        @StringRes titleRes: Int,
        url: String,
        onPermissionAllowed: () -> Unit,
        pixelParamValue: String
    ) {
        pixel.fire(SitePermissionsPixelName.SITE_PERMISSION_DIALOG_SHOWN, mapOf(PixelParameter.SITE_PERMISSION to pixelParamValue))
        val dialog = AlertDialog.Builder(activity)
        val view: View = LayoutInflater.from(activity).inflate(layout.dialog_site_permissions, null)
        val title = view.findViewById<TextView>(R.id.sitePermissionsDialogTitle)
        title.text = String.format(activity.getString(titleRes), url.websiteFromGeoLocationsApiOrigin())
        val favicon = view.findViewById<ImageView>(R.id.sitePermissionDialogFavicon)
        appCoroutineScope.launch(dispatcherProvider.main()) {
            faviconManager.loadToViewFromLocalOrFallback(tabId, url, favicon)
        }
        dialog.apply {
            setView(view)
            setPositiveButton(R.string.sitePermissionsDialogAllowButton) { dialog, _ ->
                pixel.fire(SitePermissionsPixelName.SITE_PERMISSION_DIALOG_ALLOWED, mapOf(PixelParameter.SITE_PERMISSION to pixelParamValue))
                onPermissionAllowed()
            }
            setNegativeButton(R.string.sitePermissionsDialogDenyButton) { _, _ ->
                pixel.fire(SitePermissionsPixelName.SITE_PERMISSION_DIALOG_DENIED, mapOf(PixelParameter.SITE_PERMISSION to pixelParamValue))
            }
            show()
        }
    }

    private fun askForMicAndCameraPermissions() {
        permissionRequested = SitePermissionsRequestedType.CAMERA_AND_AUDIO
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
                        Manifest.permission.MODIFY_AUDIO_SETTINGS
                    )
                )
            }
            else -> {
                systemPermissionsHelper.requestMultiplePermissions(
                    arrayOf(
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.MODIFY_AUDIO_SETTINGS,
                        Manifest.permission.CAMERA
                    )
                )
            }
        }
    }

    private fun askForMicPermissions() {
        permissionRequested = SitePermissionsRequestedType.AUDIO
        if (systemPermissionsHelper.hasMicPermissionsGranted()) {
            systemPermissionGranted()
        } else {
            systemPermissionsHelper.requestMultiplePermissions(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.MODIFY_AUDIO_SETTINGS))
        }
    }

    private fun askForCameraPermissions() {
        permissionRequested = SitePermissionsRequestedType.CAMERA
        if (systemPermissionsHelper.hasCameraPermissionsGranted()) {
            systemPermissionGranted()
        } else {
            systemPermissionsHelper.requestPermission(Manifest.permission.CAMERA)
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

    private fun systemPermissionGranted() {
        when (permissionRequested) {
            SitePermissionsRequestedType.CAMERA -> {
                sitePermissionRequest.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
                sitePermissionsRepository.sitePermissionGranted(siteURL, tabId, PermissionRequest.RESOURCE_VIDEO_CAPTURE)
            }
            SitePermissionsRequestedType.AUDIO -> {
                sitePermissionRequest.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
                sitePermissionsRepository.sitePermissionGranted(siteURL, tabId, PermissionRequest.RESOURCE_AUDIO_CAPTURE)
            }
            SitePermissionsRequestedType.CAMERA_AND_AUDIO -> {
                sitePermissionRequest.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE, PermissionRequest.RESOURCE_AUDIO_CAPTURE))
                sitePermissionsRepository.sitePermissionGranted(siteURL, tabId, PermissionRequest.RESOURCE_VIDEO_CAPTURE)
                sitePermissionsRepository.sitePermissionGranted(siteURL, tabId, PermissionRequest.RESOURCE_AUDIO_CAPTURE)
            }
        }
    }

    private fun systemPermissionDenied() {
        when (systemPermissionsHelper.isPermissionsRejectedForever(activity)) {
            true -> showSystemPermissionsDeniedDialog()
            false -> showPermissionsDeniedSnackBar()
        }
    }

    private fun showPermissionsDeniedSnackBar() {
        val message =
            when (permissionRequested) {
                SitePermissionsRequestedType.CAMERA -> R.string.sitePermissionsCameraDeniedSnackBarMessage
                SitePermissionsRequestedType.AUDIO -> R.string.sitePermissionsMicDeniedSnackBarMessage
                SitePermissionsRequestedType.CAMERA_AND_AUDIO -> R.string.sitePermissionsCameraAndMicDeniedSnackBarMessage
            }

        val snackbar = Snackbar.make(activity.window.decorView.rootView, message, Snackbar.LENGTH_LONG)
        val layout = snackbar.view as SnackbarLayout
        layout.setPadding(0, 0, 0, 40.toPx())
        snackbar.apply {
            setAction(R.string.sitePermissionsDeniedSnackBarAction) { showSystemPermissionsDeniedDialog() }
            show()
        }
    }

    private fun showSystemPermissionsDeniedDialog() {
        val titleRes = when (permissionRequested) {
            SitePermissionsRequestedType.CAMERA -> R.string.systemPermissionDialogCameraDeniedTitle
            SitePermissionsRequestedType.AUDIO -> R.string.systemPermissionDialogAudioDeniedTitle
            SitePermissionsRequestedType.CAMERA_AND_AUDIO -> R.string.systemPermissionDialogCameraAndAudioDeniedTitle
        }
        val contentRes = when (permissionRequested) {
            SitePermissionsRequestedType.CAMERA -> R.string.systemPermissionDialogCameraDeniedContent
            SitePermissionsRequestedType.AUDIO -> R.string.systemPermissionDialogAudioDeniedContent
            SitePermissionsRequestedType.CAMERA_AND_AUDIO -> R.string.systemPermissionDialogCameraAndAudioDeniedContent
        }
        AlertDialog.Builder(activity).apply {
            setTitle(titleRes)
            setMessage(contentRes)
            setPositiveButton(R.string.systemPermissionsDeniedDialogPositiveButton) { dialog, _ ->
                val intent = Intent()
                intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                val uri = Uri.fromParts("package", activity.packageName, null)
                intent.data = uri
                context.startActivity(intent)
            }
            setNegativeButton(R.string.systemPermissionsDeniedDialogNegativeButton) { _, _ -> }
            show()
        }
    }
}

fun String.websiteFromGeoLocationsApiOrigin(): String {
    val webPrefix = "www."
    val uri = Uri.parse(this)
    val host = uri.host ?: return this

    return host
        .takeIf { it.startsWith(webPrefix, ignoreCase = true) }
        ?.drop(webPrefix.length)
        ?: host
}

enum class SitePermissionsRequestedType {
    CAMERA,
    AUDIO,
    CAMERA_AND_AUDIO;

    companion object {
        fun convertSystemPermissionToType(systemPermission: String): SitePermissionsRequestedType =
            when (systemPermission) {
                Manifest.permission.CAMERA -> CAMERA
                Manifest.permission.RECORD_AUDIO -> AUDIO
                else -> CAMERA_AND_AUDIO
            }

        fun SitePermissionsRequestedType.convertToPermissionRequest(): Array<String> =
            when (this) {
                CAMERA -> arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
                AUDIO -> arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                CAMERA_AND_AUDIO -> arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE, PermissionRequest.RESOURCE_AUDIO_CAPTURE)
            }
    }
}
