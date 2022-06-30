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
import androidx.core.content.ContextCompat
import com.duckduckgo.di.scopes.ActivityScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface SystemPermissionsHelper {
    fun hasMicPermissionsGranted(context: Context): Boolean

    fun hasCameraPermissionsGranted(context: Context): Boolean

}

@ContributesBinding(ActivityScope::class)
class SystemPermissionsHelperImp @Inject constructor(): SystemPermissionsHelper {

    override fun hasMicPermissionsGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    override fun hasCameraPermissionsGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
}
