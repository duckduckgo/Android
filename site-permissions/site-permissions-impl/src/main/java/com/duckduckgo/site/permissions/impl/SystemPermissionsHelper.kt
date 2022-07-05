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
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.core.content.ContextCompat
import com.duckduckgo.di.scopes.ActivityScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

interface SystemPermissionsHelper {
    fun hasMicPermissionsGranted(): Boolean
    fun hasCameraPermissionsGranted(): Boolean
    fun registerPermissionLaunchers(
        caller: ActivityResultCaller,
        onResultPermissionRequest: (Boolean) -> Unit,
        onResultMultiplePermissionsRequest: (Map<String, Boolean>) -> Unit
    )
    fun requestPermission(permission: String)
    fun requestMultiplePermissions(permissions: Array<String>)
}

@ContributesBinding(ActivityScope::class)
@SingleInstanceIn(ActivityScope::class)
class SystemPermissionsHelperImp @Inject constructor(
    private val context: Context
) : SystemPermissionsHelper {

    private lateinit var permissionLauncher: ActivityResultLauncher<String>
    private lateinit var multiplePermissionsLauncher: ActivityResultLauncher<Array<String>>

    override fun hasMicPermissionsGranted(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.MODIFY_AUDIO_SETTINGS) == PackageManager.PERMISSION_GRANTED

    override fun hasCameraPermissionsGranted(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    override fun registerPermissionLaunchers(
        caller: ActivityResultCaller,
        onResultPermissionRequest: (Boolean) -> Unit,
        onResultMultiplePermissionsRequest: (Map<String, Boolean>) -> Unit
    ) {
        permissionLauncher = caller.registerForActivityResult(RequestPermission()) {
            onResultPermissionRequest.invoke(it)
        }

        multiplePermissionsLauncher = caller.registerForActivityResult(RequestMultiplePermissions()) {
            onResultMultiplePermissionsRequest(it)
        }
    }

    override fun requestPermission(permission: String) {
        if (this::permissionLauncher.isInitialized) {
            permissionLauncher.launch(permission)
        } else {
            throw IllegalAccessException("registerPermissionLaunchers() needs to be called before requestPermission()")
        }
    }

    override fun requestMultiplePermissions(permissions: Array<String>) {
        if (this::multiplePermissionsLauncher.isInitialized) {
            multiplePermissionsLauncher.launch(permissions)
        } else {
            throw IllegalAccessException("registerPermissionLaunchers() needs to be called before requestMultiplePermissions()")
        }
    }
}
