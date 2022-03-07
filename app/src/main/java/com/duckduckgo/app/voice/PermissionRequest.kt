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

package com.duckduckgo.app.voice

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface PermissionRequest {
    fun registerResultsCallback(
        fragment: Fragment,
        onPermissionsGranted: () -> Unit
    )

    fun launch()
}

@ContributesBinding(AppScope::class)
class MicrophonePermissionRequest @Inject constructor(
    private val pixel: Pixel,
    private val preferences: VoiceSearchSharedPreferences
) : PermissionRequest {
    companion object {
        private const val SCHEME_PACKAGE = "package"
    }

    private lateinit var permissionLauncher: ActivityResultLauncher<String>
    private lateinit var activity: Activity

    override fun registerResultsCallback(
        fragment: Fragment,
        onPermissionsGranted: () -> Unit
    ) {
        permissionLauncher = fragment.registerForActivityResult(RequestPermission()) { result ->
            fragment.context?.let {
                if (result) {
                    onPermissionsGranted()
                } else {
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)) {
                        preferences.setPermissionDeclinedForever(true)
                    }
                }
            }
        }
        activity = fragment.requireActivity()
    }

    override fun launch() {
        if (preferences.hasPermissionDeclinedForever()) {
            showNoMicAccessDialog(activity)
        } else {
            showPermissionRationale(activity)
        }
    }

    private fun showNoMicAccessDialog(context: Context) {
        AlertDialog.Builder(context)
            .setTitle(R.string.voiceSearchPermissionRejectedDialogTitle)
            .setMessage(R.string.voiceSearchPermissionRejectedDialogMessage)
            .setPositiveButton(R.string.voiceSearchPermissionRejectedDialogPositiveAction) { _, _ ->
                context.launchDuckDuckGoSettings()
            }
            .setNegativeButton(R.string.cancel) { _, _ -> }
            .show()
    }

    private fun showPermissionRationale(context: Context) {
        AlertDialog.Builder(context)
            .setTitle(R.string.voiceSearchPermissionRationaleTitle)
            .setMessage(R.string.voiceSearchPermissionRationaleDescription)
            .setPositiveButton(R.string.voiceSearchPermissionRationalePositiveAction) { _, _ ->
                pixel.fire(AppPixelName.VOICE_SEARCH_PRIVACY_DIALOG_ACCEPTED)
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                pixel.fire(AppPixelName.VOICE_SEARCH_PRIVACY_DIALOG_REJECTED)
            }
            .show()
    }

    private fun Context.launchDuckDuckGoSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts(SCHEME_PACKAGE, packageName, null)
        }
        startActivity(intent)
    }
}
