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

package com.duckduckgo.duckchat.impl.ui.filechooser.capture.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.provider.MediaStore
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.duckduckgo.di.scopes.ActivityScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlin.reflect.KFunction2

interface ExternalMediaSystemPermissionsHelper {
    fun hasMediaPermissionsGranted(inputAction: String): Boolean
    fun registerPermissionLaunchers(
        caller: ActivityResultCaller,
        onResultPermissionRequest: KFunction2<Boolean, String, Unit>,
    )
    fun requestPermission(permission: String, inputAction: String)
    fun isPermissionsRejectedForever(activity: Activity): Boolean
}

@ContributesBinding(ActivityScope::class)
class RealExternalMediaSystemPermissionsHelperImpl @Inject constructor(
    private val context: Context,
) : ExternalMediaSystemPermissionsHelper {

    private lateinit var permissionLauncher: ActivityResultLauncher<String>
    private var currentPermissionRequested: String? = null
    private var mediaStoreType: String = MediaStore.ACTION_IMAGE_CAPTURE

    override fun hasMediaPermissionsGranted(inputAction: String): Boolean {
        return when (inputAction) {
            MediaStore.ACTION_IMAGE_CAPTURE, MediaStore.ACTION_VIDEO_CAPTURE ->
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            MediaStore.Audio.Media.RECORD_SOUND_ACTION ->
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            else -> false
        }
    }

    override fun registerPermissionLaunchers(
        caller: ActivityResultCaller,
        onResultPermissionRequest: KFunction2<Boolean, String, Unit>,
    ) {
        permissionLauncher = caller.registerForActivityResult(RequestPermission()) {
            onResultPermissionRequest.invoke(it, mediaStoreType)
        }
    }

    override fun requestPermission(permission: String, inputAction: String) {
        if (this::permissionLauncher.isInitialized) {
            currentPermissionRequested = permission
            mediaStoreType = inputAction
            permissionLauncher.launch(permission)
        } else {
            throw IllegalAccessException("registerPermissionLaunchers() needs to be called before requestPermission()")
        }
    }

    override fun isPermissionsRejectedForever(activity: Activity): Boolean =
        currentPermissionRequested?.let { !ActivityCompat.shouldShowRequestPermissionRationale(activity, it) } ?: true
}
