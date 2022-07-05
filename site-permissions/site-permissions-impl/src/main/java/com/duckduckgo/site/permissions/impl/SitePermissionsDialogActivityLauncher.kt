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
import android.app.AlertDialog
import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.webkit.PermissionRequest
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultCaller
import androidx.annotation.StringRes
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.site.permissions.api.SitePermissionsDialogLauncher
import com.duckduckgo.site.permissions.impl.R.layout
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

@ContributesBinding(ActivityScope::class)
@SingleInstanceIn(ActivityScope::class)
class SitePermissionsDialogActivityLauncher @Inject constructor(
    private val systemPermissionsHelper: SystemPermissionsHelper
) : SitePermissionsDialogLauncher {

    private lateinit var sitePermissionRequest: PermissionRequest
    private lateinit var context: Context
    private lateinit var permissionRequested: SitePermissionsRequestedType

    override fun registerPermissionLauncher(caller: ActivityResultCaller) {
        systemPermissionsHelper.registerPermissionLaunchers(
            caller,
            this::onResultSystemPermissionRequest,
            this::onResultMultipleSystemPermissionsRequest
        )
    }

    override fun askForSitePermission(
        context: Context,
        url: String,
        permissionsRequested: Array<String>,
        request: PermissionRequest
    ) {
        sitePermissionRequest = request
        this.context = context

        when {
            permissionsRequested.size == 2 -> {
                showSitePermissionsRationaleDialog(R.string.sitePermissionsMicAndCameraDialogTitle, url, this::askForMicAndCameraPermissions)
            }
            permissionsRequested.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE) -> {
                showSitePermissionsRationaleDialog(R.string.sitePermissionsMicDialogTitle, url, this::askForMicPermissions)
            }
            permissionsRequested.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE) -> {
                showSitePermissionsRationaleDialog(R.string.sitePermissionsCameraDialogTitle, url, this::askForCameraPermissions)
            }
        }
    }

    private fun showSitePermissionsRationaleDialog(
        @StringRes titleRes: Int,
        url: String,
        onPermissionAllowed: () -> Unit
    ) {
        val dialog = AlertDialog.Builder(context)
        val view: View = LayoutInflater.from(context).inflate(layout.dialog_site_permissions, null)
        val title = view.findViewById<TextView>(R.id.sitePermissionsDialogTitle)
        title.text = String.format(context.getString(titleRes), url.websiteFromGeoLocationsApiOrigin())
        dialog.setView(view)
        dialog.apply {
            setPositiveButton(R.string.sitePermissionsDialogAllowButton) { dialog, _ ->
                onPermissionAllowed()
            }
            setNegativeButton(R.string.sitePermissionsDialogDenyButton) { dialog, _ ->
                Toast.makeText(context, "Deny", Toast.LENGTH_SHORT).show()
            }
            show()
        }
    }

    private fun askForMicAndCameraPermissions() {
        when {
            systemPermissionsHelper.hasMicPermissionsGranted() && systemPermissionsHelper.hasCameraPermissionsGranted() -> {
                systemPermissionGranted(SitePermissionsRequestedType.CAMERA_AND_AUDIO)
            }
            systemPermissionsHelper.hasMicPermissionsGranted() -> {
                systemPermissionGranted(SitePermissionsRequestedType.AUDIO)
                permissionRequested = SitePermissionsRequestedType.CAMERA
                systemPermissionsHelper.requestPermission(Manifest.permission.CAMERA)
            }
            systemPermissionsHelper.hasCameraPermissionsGranted() -> {
                systemPermissionGranted(SitePermissionsRequestedType.CAMERA)
                permissionRequested = SitePermissionsRequestedType.AUDIO
                systemPermissionsHelper.requestMultiplePermissions(
                    arrayOf(
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.MODIFY_AUDIO_SETTINGS
                    )
                )
            }
            else -> {
                permissionRequested = SitePermissionsRequestedType.CAMERA_AND_AUDIO
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
        if (systemPermissionsHelper.hasMicPermissionsGranted()) {
            systemPermissionGranted(SitePermissionsRequestedType.AUDIO)
        } else {
            permissionRequested = SitePermissionsRequestedType.AUDIO
            systemPermissionsHelper.requestMultiplePermissions(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.MODIFY_AUDIO_SETTINGS))
        }
    }

    private fun askForCameraPermissions() {
        if (systemPermissionsHelper.hasCameraPermissionsGranted()) {
            systemPermissionGranted(SitePermissionsRequestedType.CAMERA)
        } else {
            permissionRequested = SitePermissionsRequestedType.CAMERA
            systemPermissionsHelper.requestPermission(Manifest.permission.CAMERA)
        }
    }

    private fun onResultSystemPermissionRequest(granted: Boolean) {
        when (granted) {
            true -> systemPermissionGranted(permissionRequested)
            false -> systemPermissionDenied(permissionRequested)
        }
    }

    private fun onResultMultipleSystemPermissionsRequest(grantedPermissions: Map<String, Boolean>) {
        grantedPermissions.entries.forEach {
            when (it.value) {
                true -> systemPermissionGranted(SitePermissionsRequestedType.convertSystemPermissionToType(it.key))
                false -> systemPermissionDenied(SitePermissionsRequestedType.convertSystemPermissionToType(it.key))
            }
        }
    }

    private fun systemPermissionGranted(permissionType: SitePermissionsRequestedType) {
        when (permissionType) {
            SitePermissionsRequestedType.CAMERA -> sitePermissionRequest.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
            SitePermissionsRequestedType.AUDIO -> sitePermissionRequest.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
            SitePermissionsRequestedType.CAMERA_AND_AUDIO -> {
                sitePermissionRequest.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE, PermissionRequest.RESOURCE_AUDIO_CAPTURE))
            }
        }
    }

    private fun systemPermissionDenied(permissionType: SitePermissionsRequestedType) {
        //TODO show correct snackbar
        val message =
            when (permissionType) {
                SitePermissionsRequestedType.CAMERA -> "Camera denied"
                SitePermissionsRequestedType.AUDIO -> "Audio denied"
                SitePermissionsRequestedType.CAMERA_AND_AUDIO -> "Audio and camera denied"
            }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
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
    }
}
