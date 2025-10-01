/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.duckchat.impl.ui.filechooser.capture.launcher

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.StringRes
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.ui.filechooser.capture.MediaCaptureResultHandler
import com.duckduckgo.duckchat.impl.ui.filechooser.capture.launcher.UploadFromExternalMediaAppLauncher.MediaCaptureResult
import com.duckduckgo.duckchat.impl.ui.filechooser.capture.permission.ExternalMediaSystemPermissionsHelper
import com.duckduckgo.duckchat.impl.ui.filechooser.capture.postprocess.MediaCaptureDelayedDeleter
import com.duckduckgo.duckchat.impl.ui.filechooser.capture.postprocess.MediaCaptureImageMover
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.asLog
import logcat.logcat
import java.io.File
import javax.inject.Inject

/**
 * Public API for launching any external media capturing app (e.g camera, sound recorder) and capturing.
 * This launcher will internally handle necessary permissions.
 *
 * [registerForResult] should be called once in [Activity.onCreate]
 * [launch] should be called when it is time to launch the media capturing app.
 */
interface UploadFromExternalMediaAppLauncher {

    /**
     * Launches the external media capturing app with a given action.
     * Before calling launch, you must register a callback to receive the result, using [registerForResult].
     *
     * @param inputAction This is used to inform what type of media was requested.
     */
    fun launch(inputAction: String)

    /**
     * Registers a callback to receive the result of the capture.
     * This must be called before calling [launch].
     *
     * @param onResult will be called with the captured content or another result type if the capture failed.
     */
    fun registerForResult(
        caller: ActivityResultCaller,
        onResult: (MediaCaptureResult) -> Unit,
    )

    /**
     * Shows the permission rationale dialog.
     *
     * @param inputAction This is used to inform what type of media was requested.
     */
    fun showPermissionRationaleDialog(activity: Activity, inputAction: String)

    /**
     * Types of results that can be returned from the media capture flow (e.g camera, sound recorder).
     */
    sealed interface MediaCaptureResult {

        /**
         * The media was captured successfully.
         * The included [file] is the location of the captured media.
         *
         * Note, this file should be considered temporary and will be automatically deleted after a short period of time.
         */
        data class MediaCaptured(val file: File) : MediaCaptureResult

        /**
         * The user denied permission.
         *
         * @param inputAction This is used to inform what type of media was requested.
         */
        data class CouldNotCapturePermissionDenied(val inputAction: String) : MediaCaptureResult

        /**
         * No media was captured, most likely because the user cancelled the capture flow.
         */
        data object NoMediaCaptured : MediaCaptureResult

        /**
         * No media was captured due to an error accessing the media app.
         *
         * @param messageId The message to be shown as error.
         */
        data class ErrorAccessingMediaApp(@StringRes val messageId: Int) : MediaCaptureResult
    }
}

@ContributesBinding(ActivityScope::class)
class PermissionAwareExternalMediaAppLauncher @Inject constructor(
    private val permissionHelper: ExternalMediaSystemPermissionsHelper,
    private val imageMover: MediaCaptureImageMover,
    private val delayedDeleter: MediaCaptureDelayedDeleter,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
) : UploadFromExternalMediaAppLauncher {

    private lateinit var callback: (MediaCaptureResult) -> Unit
    private lateinit var launcher: ActivityResultLauncher<String?>

    override fun launch(inputAction: String) {
        if (permissionHelper.hasMediaPermissionsGranted(inputAction)) {
            logcat { "permission already granted for $inputAction. launching app now" }
            launchMediaApp(inputAction)
        } else {
            // ask for permission
            logcat { "no permission yet for $inputAction, need to request permission before launching" }
            when (inputAction) {
                MediaStore.ACTION_IMAGE_CAPTURE, MediaStore.ACTION_VIDEO_CAPTURE ->
                    permissionHelper.requestPermission(Manifest.permission.CAMERA, inputAction)
                MediaStore.Audio.Media.RECORD_SOUND_ACTION ->
                    permissionHelper.requestPermission(Manifest.permission.RECORD_AUDIO, inputAction)
                else ->
                    logcat { "Unknown permissions needed for $inputAction" }
            }
        }
    }

    private fun launchMediaApp(inputAction: String) {
        try {
            launcher.launch(inputAction)
        } catch (e: Exception) {
            logcat { "exception launching camera / sound recorder: ${e.asLog()}" }
            if (inputAction == MediaStore.ACTION_IMAGE_CAPTURE || inputAction == MediaStore.ACTION_VIDEO_CAPTURE) {
                callback.invoke(MediaCaptureResult.ErrorAccessingMediaApp(R.string.imageCaptureCameraUnavailable))
            } else if (inputAction == MediaStore.Audio.Media.RECORD_SOUND_ACTION) {
                callback.invoke(MediaCaptureResult.ErrorAccessingMediaApp(R.string.audioCaptureSoundRecorderUnavailable))
            }
        }
    }

    override fun registerForResult(
        caller: ActivityResultCaller,
        onResult: (MediaCaptureResult) -> Unit,
    ) {
        callback = onResult
        registerPermissionLauncher(caller)
        launcher = caller.registerForActivityResult(MediaCaptureResultHandler()) { interimFile ->
            if (interimFile == null) {
                onResult(MediaCaptureResult.NoMediaCaptured)
            } else {
                appCoroutineScope.launch(dispatchers.io()) {
                    val finalImage = moveCapturedImageToFinalLocation(interimFile)
                    if (finalImage == null) {
                        onResult(MediaCaptureResult.NoMediaCaptured)
                    } else {
                        onResult(MediaCaptureResult.MediaCaptured(finalImage))
                    }
                }
            }
        }
    }

    override fun showPermissionRationaleDialog(activity: Activity, inputAction: String) {
        if (permissionHelper.isPermissionsRejectedForever(activity)) {
            if (inputAction == MediaStore.ACTION_IMAGE_CAPTURE || inputAction == MediaStore.ACTION_VIDEO_CAPTURE) {
                showDialog(
                    activity,
                    R.string.imageCaptureCameraPermissionDeniedTitle,
                    R.string.imageCaptureCameraPermissionDeniedMessage,
                )
            } else if (inputAction == MediaStore.Audio.Media.RECORD_SOUND_ACTION) {
                showDialog(
                    activity,
                    R.string.audioCaptureSoundRecorderPermissionDeniedTitle,
                    R.string.audioCaptureSoundRecorderPermissionDeniedMessage,
                )
            }
        }
    }

    private fun showDialog(activity: Activity, @StringRes titleId: Int, @StringRes messageId: Int) {
        TextAlertDialogBuilder(activity)
            .setTitle(titleId)
            .setMessage(messageId)
            .setPositiveButton(R.string.imageCaptureCameraPermissionDeniedPositiveButton)
            .setNegativeButton(R.string.imageCaptureCameraPermissionDeniedNegativeButton)
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

    private fun registerPermissionLauncher(caller: ActivityResultCaller) {
        permissionHelper.registerPermissionLaunchers(caller, this::onResultSystemPermissionRequest)
    }

    private fun onResultSystemPermissionRequest(granted: Boolean, inputAction: String) {
        logcat { "permission request received for $inputAction. granted=$granted" }
        if (granted) {
            launchMediaApp(inputAction)
        } else {
            callback(MediaCaptureResult.CouldNotCapturePermissionDenied(inputAction))
        }
    }

    private suspend fun moveCapturedImageToFinalLocation(interimFile: Uri): File? {
        return imageMover.moveInternal(interimFile)?.also { finalImage ->
            delayedDeleter.scheduleDeletion(finalImage)
        }
    }
}
