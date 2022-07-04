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

import android.app.AlertDialog
import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.webkit.PermissionRequest
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.site.permissions.api.SitePermissionsDialogLauncher
import com.duckduckgo.site.permissions.api.SitePermissionsManager
import com.duckduckgo.site.permissions.impl.R.layout
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@ContributesBinding(ActivityScope::class)
class SitePermissionsDialogActivityLauncher @Inject constructor(
    private val systemPermissionsHelper: SystemPermissionsHelper,
    private val sitePermissionsManager: SitePermissionsManager
) : SitePermissionsDialogLauncher {

    override fun askForSitePermission(context: Context, url: String, permissionsRequested: Array<String>) {
        when {
            permissionsRequested.size == 2 -> {
                when {
                    systemPermissionsHelper.hasMicPermissionsGranted() && systemPermissionsHelper.hasCameraPermissionsGranted() -> {
                        askForMicAndCameraPermissions(context, url)
                    }
                    systemPermissionsHelper.hasMicPermissionsGranted() -> askForMicPermissions(context, url)
                    systemPermissionsHelper.hasCameraPermissionsGranted() -> askForCameraPermissions(context, url)
                    else -> Toast.makeText(context, "No permissions allowed", Toast.LENGTH_SHORT).show()
                }
            }
            permissionsRequested.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE) -> {
                if (systemPermissionsHelper.hasMicPermissionsGranted()) {
                    askForMicPermissions(context, url)
                } else {
                    Toast.makeText(context, "No Mic permissions allowed", Toast.LENGTH_SHORT).show()
                }
            }
            permissionsRequested.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE) -> {
                if (systemPermissionsHelper.hasCameraPermissionsGranted()) {
                    askForCameraPermissions(context, url)
                } else {
                    Toast.makeText(context, "No Camera permissions allowed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun askForMicAndCameraPermissions(context: Context, url: String) {
        val onPermissionAllow = {} //TODO
        showSitePermissionsDialog(context, R.string.sitePermissionsMicAndCameraDialogTitle, url, onPermissionAllow)
    }

    private fun askForMicPermissions(context: Context, url: String) {
        val onPermissionAllow = {} //TODO
        showSitePermissionsDialog(context, R.string.sitePermissionsMicDialogTitle, url, onPermissionAllow)
    }

    private fun askForCameraPermissions(context: Context, url: String) {
        val onPermissionAllow = {} //TODO
        showSitePermissionsDialog(context, R.string.sitePermissionsCameraDialogTitle, url, onPermissionAllow)
    }

    private fun showSitePermissionsDialog(
        context: Context,
        @StringRes titleRes: Int,
        url: String,
        onPermissionAllow: () -> Unit
    ) {
        val dialog = AlertDialog.Builder(context)
        val view: View = LayoutInflater.from(context).inflate(layout.dialog_site_permissions, null)
        val title = view.findViewById<TextView>(R.id.sitePermissionsDialogTitle)
        title.text = String.format(context.getString(titleRes), url.websiteFromGeoLocationsApiOrigin())
        dialog.setView(view)
        dialog.apply {
            setPositiveButton(R.string.sitePermissionsDialogAllowButton) { _, _ -> onPermissionAllow.invoke() }
            setNegativeButton(R.string.sitePermissionsDialogDenyButton) { _, _ -> Toast.makeText(context, "Deny", Toast.LENGTH_SHORT).show() }
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
