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

package com.duckduckgo.duckchat.impl.ui.filechooser.capture.camera

import android.content.Context
import android.content.pm.PackageManager.FEATURE_CAMERA_ANY
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface CameraHardwareChecker {
    fun hasCameraHardware(): Boolean
}

@ContributesBinding(AppScope::class)
class CameraHardwareCheckerImpl @Inject constructor(
    private val context: Context,
) : CameraHardwareChecker {

    override fun hasCameraHardware(): Boolean {
        return with(context.packageManager) {
            kotlin.runCatching { hasSystemFeature(FEATURE_CAMERA_ANY) }.getOrDefault(false)
        }
    }
}
