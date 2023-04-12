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

package com.duckduckgo.sync.impl

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.core.content.ContextCompat
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.ActivityScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.*

interface PermissionRequest {
    fun registerResultsCallback(
        caller: ActivityResultCaller,
        onPermissionDenied: () -> Unit,
    )

    fun invokeOrRequestPermission(
        invoke: () -> Unit,
    )
}

@ContributesBinding(ActivityScope::class)
class StoragePermissionRequest @Inject constructor(
    val appBuildConfig: AppBuildConfig,
    val context: Context,
) : PermissionRequest {

    private var permissionRequest: ActivityResultLauncher<String>? = null

    private var onPermissionGranted: (() -> Unit)? = null
    override fun registerResultsCallback(
        caller: ActivityResultCaller,
        onPermissionDenied: () -> Unit,
    ) {
        permissionRequest = caller.registerForActivityResult(RequestPermission()) {
            if (it) {
                onPermissionGranted?.invoke()
            } else {
                onPermissionDenied.invoke()
            }
        }
    }

    override fun invokeOrRequestPermission(
        invoke: () -> Unit,
    ) {
        val permissionRequest = permissionRequest ?: throw IllegalStateException("registerResultsCallback must be called before invoking this method")

        if (hasWriteStoragePermission()) {
            invoke.invoke()
        } else {
            onPermissionGranted = invoke
            permissionRequest.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    private fun hasWriteStoragePermission(): Boolean {
        return minSdk30() ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    private fun minSdk30(): Boolean {
        return appBuildConfig.sdkInt >= Build.VERSION_CODES.R
    }
}
