/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.sync.impl.ui.v2

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PackageManager.FEATURE_CAMERA_ANY
import androidx.core.content.ContextCompat
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface CameraAccess {
    fun isHardwareAvailable(): Boolean
    fun isPermissionGranted(): Boolean
}

@ContributesBinding(AppScope::class, boundType = CameraAccess::class)
class RealCameraAccess @Inject constructor(
    private val context: Context,
) : CameraAccess {

    override fun isHardwareAvailable(): Boolean {
        return context.packageManager.hasSystemFeature(FEATURE_CAMERA_ANY)
    }

    override fun isPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }
}
