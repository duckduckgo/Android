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
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build.VERSION_CODES
import androidx.activity.result.ActivityResultCaller
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.duckduckgo.app.voice.VoiceSearchLauncher.Event
import com.duckduckgo.app.voice.VoiceSearchLauncher.Source
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface VoiceSearchLauncher {
    fun registerResultsCallback(
        caller: ActivityResultCaller,
        activity: Activity,
        source: Source,
        onEvent: (Event) -> Unit
    )

    fun launch()

    enum class Source(val paramValueName: String) {
        BROWSER("browser"),
        WIDGET("widget")
    }

    sealed class Event {
        class VoiceRecognitionSuccess(val result: String) : Event()
        object SearchCancelled : Event()
    }
}

@ContributesBinding(AppScope::class)
@RequiresApi(VERSION_CODES.S)
class PermissionAwareVoiceSearchLauncher @Inject constructor(
    private val context: Context,
    private val permissionRequest: PermissionRequest,
    private val voiceSearchActivityLauncher: VoiceSearchActivityLauncher
) : VoiceSearchLauncher {

    override fun registerResultsCallback(
        caller: ActivityResultCaller,
        activity: Activity,
        source: Source,
        onEvent: (Event) -> Unit
    ) {
        permissionRequest.registerResultsCallback(caller, activity) {
            voiceSearchActivityLauncher.launch()
        }
        voiceSearchActivityLauncher.registerResultsCallback(caller, activity, source) {
            onEvent(it)
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
