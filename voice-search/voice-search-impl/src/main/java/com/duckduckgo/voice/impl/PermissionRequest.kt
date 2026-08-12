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
import androidx.activity.result.ActivityResultCaller
import com.duckduckgo.common.utils.extensions.launchApplicationInfoSettings
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.voice.api.VoiceSearchLauncher.VoiceSearchMode
import com.duckduckgo.voice.impl.ActivityResultLauncherWrapper.Action.LaunchPermissionRequest
import com.duckduckgo.voice.impl.ActivityResultLauncherWrapper.Request
import com.duckduckgo.voice.store.VoiceSearchRepository
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface PermissionRequest {
    fun registerResultsCallback(
        caller: ActivityResultCaller,
        activity: Activity,
        onPermissionsGranted: () -> Unit,
        /** Invoked when the flow ends without voice search starting, so callers can undo anything they staged for it. */
        onRequestAborted: () -> Unit = {},
        onVoiceSearchDisabled: () -> Unit = {},
    )

    fun launch(
        activity: Activity,
        mode: VoiceSearchMode?,
    )
}

@ContributesBinding(ActivityScope::class)
class MicrophonePermissionRequest @Inject constructor(
    private val voiceSearchRepository: VoiceSearchRepository,
    private val voiceSearchPermissionDialogsLauncher: VoiceSearchPermissionDialogsLauncher,
    private val activityResultLauncherWrapper: ActivityResultLauncherWrapper,
    private val permissionRationale: PermissionRationale,
) : PermissionRequest {
    private lateinit var voiceSearchDisabled: () -> Unit
    private var requestAborted: () -> Unit = {}

    // Remembered from launch(): the permission result arrives long after, and the denial dialog's copy
    // and options differ for Duck.ai.
    private var pendingMode: VoiceSearchMode? = null

    override fun registerResultsCallback(
        caller: ActivityResultCaller,
        activity: Activity,
        onPermissionsGranted: () -> Unit,
        onRequestAborted: () -> Unit,
        onVoiceSearchDisabled: () -> Unit,
    ) {
        activityResultLauncherWrapper.register(
            caller,
            Request.Permission { granted ->
                when {
                    granted -> {
                        voiceSearchRepository.setMicPermissionPreviouslyDenied(false)
                        onPermissionsGranted()
                    }
                    permissionRationale.shouldShow(activity) -> {
                        voiceSearchRepository.setMicPermissionPreviouslyDenied(true)
                        showMicPermissionDeniedSnackbar(activity)
                        requestAborted()
                    }
                    !voiceSearchRepository.getMicPermissionPreviouslyDenied() -> {
                        showMicPermissionDeniedSnackbar(activity)
                        requestAborted()
                    }

                    else -> showMicAccessDeniedDialog(activity)
                }
            },
        )
        voiceSearchDisabled = onVoiceSearchDisabled
        requestAborted = onRequestAborted
    }

    override fun launch(
        activity: Activity,
        mode: VoiceSearchMode?,
    ) {
        pendingMode = mode
        activityResultLauncherWrapper.launch(LaunchPermissionRequest)
    }

    private fun showMicPermissionDeniedSnackbar(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        voiceSearchPermissionDialogsLauncher.showMicPermissionDeniedSnackbar(
            activity,
            onAllowSelected = { activityResultLauncherWrapper.launch(LaunchPermissionRequest) },
        )
    }

    private fun showMicAccessDeniedDialog(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) {
            requestAborted()
            return
        }
        voiceSearchPermissionDialogsLauncher.showMicAccessDeniedDialog(
            activity,
            mode = pendingMode,
            onChangePermissionsSelected = { activity.launchApplicationInfoSettings() },
            onHideVoiceSearchSelected = { disableVoiceSearch() },
            onCancelled = { requestAborted() },
        )
    }

    private fun disableVoiceSearch() {
        voiceSearchRepository.setVoiceSearchUserEnabled(false)
        voiceSearchDisabled()
    }
}
