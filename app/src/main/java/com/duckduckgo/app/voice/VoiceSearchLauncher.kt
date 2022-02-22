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

package com.duckduckgo.app.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface VoiceSearchLauncher {
    fun registerResultsCallback(
        fragment: Fragment,
        onSpeechResult: (String) -> Unit
    )

    fun launch()
}

@ContributesBinding(AppScope::class)
class PermissionAwareVoiceSearchLauncher @Inject constructor(
    private val context: Context,
    private val permissionRequest: PermissionRequest,
    private val voiceSearchActivityLauncher: VoiceSearchActivityLauncher
) : VoiceSearchLauncher {

    override fun registerResultsCallback(
        fragment: Fragment,
        onSpeechResult: (String) -> Unit
    ) {
        permissionRequest.registerResultsCallback(fragment) {
            voiceSearchActivityLauncher.launch()
        }
        voiceSearchActivityLauncher.registerResultsCallback(fragment) {
            onSpeechResult(it)
        }
    }

    override fun launch() {
        if (hasRequiredPermissionsGranted(context)) {
            voiceSearchActivityLauncher.launch()
        } else {
            permissionRequest.launch()
        }
    }

    private fun hasRequiredPermissionsGranted(context: Context): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
}
