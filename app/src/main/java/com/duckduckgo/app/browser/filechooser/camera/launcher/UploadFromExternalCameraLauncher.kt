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

package com.duckduckgo.app.browser.filechooser.camera.launcher

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.StringRes
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.filechooser.camera.CameraCaptureResultHandler
import com.duckduckgo.app.browser.filechooser.camera.launcher.UploadFromExternalCameraLauncher.CameraImageCaptureResult
import com.duckduckgo.app.browser.filechooser.camera.permission.ExternalCameraSystemPermissionsHelper
import com.duckduckgo.app.browser.filechooser.camera.postprocess.CameraCaptureDelayedDeleter
import com.duckduckgo.app.browser.filechooser.camera.postprocess.CameraCaptureImageMover
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.FragmentScope
import com.squareup.anvil.annotations.ContributesBinding
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Public API for launching the external camera app and capturing an image.
 * This launcher will internally handle necessary camera permissions.
 *
 * [registerForResult] should be called once in [Activity.onCreate]
 * [launch] should be called when it is time to launch the camera app.
 */
interface UploadFromExternalCameraLauncher {

    /**
     * Launches the external camera app to capture an image.
     * Before calling launch, you must register a callback to receive the result, using [registerForResult].
     */
    fun launch(input: String)

    /**
     * Registers a callback to receive the result of the camera capture.
     * This must be called before calling [launch].
     *
     * @param onResult will be called with the captured image or another result type if the capture failed.
     */
    fun registerForResult(
        caller: ActivityResultCaller,
        onResult: (CameraImageCaptureResult) -> Unit,
    )

    fun showPermissionRationaleDialog(activity: Activity, input: String)

    /**
     * Types of results that can be returned from the camera capture flow.
     */
    sealed interface CameraImageCaptureResult {

        /**
         * The image was captured successfully.
         * The included [file] is the location of the captured image.
         *
         * Note, this file should be considered temporary and will be automatically deleted after a short period of time.
         */
        data class ImageCaptured(val file: File) : CameraImageCaptureResult

        /**
         * The user denied permission to access the camera.
         */
        data class CouldNotCapturePermissionDenied(val input: String) : CameraImageCaptureResult

        /**
         * No image was captured, most likely because the user cancelled the camera capture flow.
         */
        data object NoImageCaptured : CameraImageCaptureResult

        /**
         * No image was captured as unable to integrate with the system camera.
         */
        data class ErrorAccessingCamera(@StringRes val messageId: Int) : CameraImageCaptureResult
    }
}

@ContributesBinding(FragmentScope::class)
class PermissionAwareExternalCameraLauncher @Inject constructor(
    private val permissionHelper: ExternalCameraSystemPermissionsHelper,
    private val imageMover: CameraCaptureImageMover,
    private val delayedDeleter: CameraCaptureDelayedDeleter,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
) : UploadFromExternalCameraLauncher {

    private lateinit var callback: (CameraImageCaptureResult) -> Unit
    private lateinit var launcher: ActivityResultLauncher<String?>

    override fun launch(input: String) {
        if (permissionHelper.hasCameraPermissionsGranted(input)) {
            Timber.d("camera permission already granted. launching camera now")
            launchCamera(input)
        } else {
            // ask for permission
            Timber.d("no camera permission yet, need to request camera permission before launching camera -- $input")
            when (input) {
                MediaStore.ACTION_IMAGE_CAPTURE, MediaStore.ACTION_VIDEO_CAPTURE ->
                    permissionHelper.requestPermission(Manifest.permission.CAMERA, input)
                MediaStore.Audio.Media.RECORD_SOUND_ACTION ->
                    permissionHelper.requestPermission(Manifest.permission.RECORD_AUDIO, input)
                else ->
                    Timber.d("Unknown permissions needed $input")
            }
        }
    }

    private fun launchCamera(input: String) {
        try {
            launcher.launch(input)
        } catch (e: Exception) {
            Timber.w(e, "exception launching camera / sound recorder")
            if (input == MediaStore.ACTION_IMAGE_CAPTURE || input == MediaStore.ACTION_VIDEO_CAPTURE) {
                callback.invoke(CameraImageCaptureResult.ErrorAccessingCamera(R.string.imageCaptureCameraUnavailable))
            } else if (input == MediaStore.Audio.Media.RECORD_SOUND_ACTION) {
                callback.invoke(CameraImageCaptureResult.ErrorAccessingCamera(R.string.audioCaptureSoundRecorderUnavailable))
            }
        }
    }

    override fun registerForResult(
        caller: ActivityResultCaller,
        onResult: (CameraImageCaptureResult) -> Unit,
    ) {
        callback = onResult
        registerPermissionLauncher(caller)
        launcher = caller.registerForActivityResult(CameraCaptureResultHandler()) { interimFile ->
            if (interimFile == null) {
                onResult(CameraImageCaptureResult.NoImageCaptured)
            } else {
                appCoroutineScope.launch(dispatchers.io()) {
                    val finalImage = moveCapturedImageToFinalLocation(interimFile)
                    if (finalImage == null) {
                        onResult(CameraImageCaptureResult.NoImageCaptured)
                    } else {
                        onResult(CameraImageCaptureResult.ImageCaptured(finalImage))
                    }
                }
            }
        }
    }

    override fun showPermissionRationaleDialog(activity: Activity, input: String) {
        if (permissionHelper.isPermissionsRejectedForever(activity)) {
            if (input == MediaStore.ACTION_IMAGE_CAPTURE || input == MediaStore.ACTION_VIDEO_CAPTURE) {
                showDialog(
                    activity,
                    R.string.imageCaptureCameraPermissionDeniedTitle,
                    R.string.imageCaptureCameraPermissionDeniedMessage,
                )
            } else if (input == MediaStore.Audio.Media.RECORD_SOUND_ACTION) {
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

    private fun onResultSystemPermissionRequest(granted: Boolean, input: String) {
        Timber.d("camera permission request received. granted=%s", granted)
        if (granted) {
            launchCamera(input)
        } else {
            callback(CameraImageCaptureResult.CouldNotCapturePermissionDenied(input))
        }
    }

    private suspend fun moveCapturedImageToFinalLocation(interimFile: Uri): File? {
        return imageMover.moveInternal(interimFile)?.also { finalImage ->
            delayedDeleter.scheduleDeletion(finalImage)
        }
    }
}
