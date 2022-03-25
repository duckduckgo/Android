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

package com.duckduckgo.mobile.android.voice.impl

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.core.app.ActivityCompat
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface PermissionRequest {
    fun registerResultsCallback(
        caller: ActivityResultCaller,
        activity: Activity,
        onPermissionsGranted: () -> Unit
    )

    fun launch()
}

@ContributesBinding(AppScope::class)
class MicrophonePermissionRequest @Inject constructor(
    private val pixel: Pixel,
    private val voiceSearchChecksStore: VoiceSearchChecksStore,
    private val voiceSearchPermissionDialogsLauncher: VoiceSearchPermissionDialogsLauncher
) : PermissionRequest {
    companion object {
        private const val SCHEME_PACKAGE = "package"
    }

    private lateinit var permissionLauncher: ActivityResultLauncher<String>
    private lateinit var _activity: Activity

    override fun registerResultsCallback(
        caller: ActivityResultCaller,
        activity: Activity,
        onPermissionsGranted: () -> Unit
    ) {
        _activity = activity
        permissionLauncher = caller.registerForActivityResult(RequestPermission()) { result ->
            if (result) {
                onPermissionsGranted()
            } else {
                if (!ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)) {
                    voiceSearchChecksStore.declinePermissionForever(true)
                }
            }
        }
    }

    override fun launch() {
        if (voiceSearchChecksStore.hasPermissionDeclinedForever()) {
            voiceSearchPermissionDialogsLauncher.showNoMicAccessDialog(
                _activity,
                { _activity.launchDuckDuckGoSettings() }
            )
        } else {
            if (voiceSearchChecksStore.hasAcceptedRationaleDialog()) {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                voiceSearchPermissionDialogsLauncher.showPermissionRationale(
                    _activity,
                    { handleRationaleAccepted() },
                    { handleRationaleCancelled() }
                )
            }
        }
    }

    private fun Context.launchDuckDuckGoSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts(SCHEME_PACKAGE, packageName, null)
        }
        _activity.startActivity(intent)
    }

    private fun handleRationaleAccepted() {
        pixel.fire(VoiceSearchPixelNames.VOICE_SEARCH_PRIVACY_DIALOG_ACCEPTED)
        voiceSearchChecksStore.acceptRationaleDialog(true)
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun handleRationaleCancelled() {
        pixel.fire(VoiceSearchPixelNames.VOICE_SEARCH_PRIVACY_DIALOG_REJECTED)
    }
}
