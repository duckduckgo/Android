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

package com.duckduckgo.voice.impl

import android.app.Activity
import android.content.Context
import androidx.activity.result.ActivityResultCaller
import androidx.appcompat.app.AppCompatActivity
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.extensions.launchApplicationInfoSettings
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.voice.api.VoiceSearchLauncher.VoiceSearchMode
import com.duckduckgo.voice.impl.ActivityResultLauncherWrapper.Action.LaunchPermissionRequest
import com.duckduckgo.voice.impl.ActivityResultLauncherWrapper.Request
import com.duckduckgo.voice.impl.remoteconfig.VoiceSearchFeature
import com.duckduckgo.voice.store.VoiceSearchRepository
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface PermissionRequest {
    fun registerResultsCallback(
        caller: ActivityResultCaller,
        activity: Activity,
        onPermissionsGranted: () -> Unit,
        onVoiceSearchDisabled: () -> Unit = {},
    )

    fun launch(
        activity: Activity,
        mode: VoiceSearchMode?,
    )
}

@ContributesBinding(ActivityScope::class)
class MicrophonePermissionRequest @Inject constructor(
    private val pixel: Pixel,
    private val voiceSearchRepository: VoiceSearchRepository,
    private val voiceSearchPermissionDialogsLauncher: VoiceSearchPermissionDialogsLauncher,
    private val activityResultLauncherWrapper: ActivityResultLauncherWrapper,
    private val permissionRationale: PermissionRationale,
    private val voiceSearchFeature: VoiceSearchFeature,
) : PermissionRequest {
    private lateinit var voiceSearchDisabled: () -> Unit

    // Remembered from launch(): the permission result arrives long after, and the denial dialog's copy
    // and options differ for Duck.ai.
    private var pendingMode: VoiceSearchMode? = null

    private val newPermissionFlowEnabled: Boolean
        get() = voiceSearchFeature.newPermissionFlow().isEnabled()

    override fun registerResultsCallback(
        caller: ActivityResultCaller,
        activity: Activity,
        onPermissionsGranted: () -> Unit,
        onVoiceSearchDisabled: () -> Unit,
    ) {
        activityResultLauncherWrapper.register(
            caller,
            Request.Permission { granted ->
                when {
                    granted -> onPermissionsGranted()
                    // shouldShow() only stays true while the system is still willing to prompt; once it
                    // flips to false the user has denied twice and the prompt will never appear again.
                    permissionRationale.shouldShow(activity) -> showMicPermissionDeniedSnackbar(activity)
                    else -> showNoMicAccessDialog(activity)
                }
            },
        )
        voiceSearchDisabled = onVoiceSearchDisabled
    }

    override fun launch(
        activity: Activity,
        mode: VoiceSearchMode?,
    ) {
        pendingMode = mode
        if (newPermissionFlowEnabled || voiceSearchRepository.getHasAcceptedRationaleDialog()) {
            activityResultLauncherWrapper.launch(LaunchPermissionRequest)
        } else {
            voiceSearchPermissionDialogsLauncher.showPermissionRationale(
                activity,
                { handleRationaleAccepted() },
                { handleRationaleCancelled(activity) },
            )
        }
    }

    private fun showMicPermissionDeniedSnackbar(activity: Activity) {
        if (!newPermissionFlowEnabled) return
        if (activity.isFinishing || activity.isDestroyed) return
        voiceSearchPermissionDialogsLauncher.showMicPermissionDeniedSnackbar(
            activity,
            onAllowSelected = { activityResultLauncherWrapper.launch(LaunchPermissionRequest) },
        )
    }

    private fun showNoMicAccessDialog(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        if (newPermissionFlowEnabled) {
            voiceSearchPermissionDialogsLauncher.showMicAccessDeniedDialog(
                activity,
                mode = pendingMode,
                onChangePermissionsSelected = { (activity as? AppCompatActivity)?.launchApplicationInfoSettings() },
                onHideVoiceSearchSelected = { disableVoiceSearch() },
            )
        } else {
            voiceSearchPermissionDialogsLauncher.showNoMicAccessDialog(
                activity,
                { (activity as? AppCompatActivity)?.launchApplicationInfoSettings() },
                { showRemoveVoiceSearchDialog(activity) },
            )
        }
    }

    private fun handleRationaleAccepted() {
        pixel.fire(VoiceSearchPixelNames.VOICE_SEARCH_PRIVACY_DIALOG_ACCEPTED)
        voiceSearchRepository.acceptRationaleDialog()
        activityResultLauncherWrapper.launch(LaunchPermissionRequest)
    }

    private fun handleRationaleCancelled(context: Context) {
        pixel.fire(VoiceSearchPixelNames.VOICE_SEARCH_PRIVACY_DIALOG_REJECTED)
        showRemoveVoiceSearchDialog(context)
    }

    private fun showRemoveVoiceSearchDialog(context: Context) {
        voiceSearchPermissionDialogsLauncher.showRemoveVoiceSearchDialog(
            context,
            onRemoveVoiceSearch = { disableVoiceSearch() },
        )
    }

    private fun disableVoiceSearch() {
        voiceSearchRepository.setVoiceSearchUserEnabled(false)
        voiceSearchDisabled()
    }
}
