/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.app.browser.filechooser.camera.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.duckduckgo.di.scopes.FragmentScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface ExternalCameraSystemPermissionsHelper {
    fun hasCameraPermissionsGranted(): Boolean
    fun registerPermissionLaunchers(
        caller: ActivityResultCaller,
        onResultPermissionRequest: (Boolean) -> Unit,
    )
    fun requestPermission(permission: String)
    fun isPermissionsRejectedForever(activity: Activity): Boolean
}

@ContributesBinding(FragmentScope::class)
class RealExternalCameraSystemPermissionsHelperImpl @Inject constructor(
    private val context: Context,
) : ExternalCameraSystemPermissionsHelper {

    private lateinit var permissionLauncher: ActivityResultLauncher<String>
    private var currentPermissionRequested: String? = null

    override fun hasCameraPermissionsGranted(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    override fun registerPermissionLaunchers(
        caller: ActivityResultCaller,
        onResultPermissionRequest: (Boolean) -> Unit,
    ) {
        permissionLauncher = caller.registerForActivityResult(RequestPermission()) {
            onResultPermissionRequest.invoke(it)
        }
    }

    override fun requestPermission(permission: String) {
        if (this::permissionLauncher.isInitialized) {
            currentPermissionRequested = permission
            permissionLauncher.launch(permission)
        } else {
            throw IllegalAccessException("registerPermissionLaunchers() needs to be called before requestPermission()")
        }
    }

    override fun isPermissionsRejectedForever(activity: Activity): Boolean =
        currentPermissionRequested?.let { !ActivityCompat.shouldShowRequestPermissionRationale(activity, it) } ?: true
}
