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

package com.duckduckgo.mobile.android.voice.impl

import android.app.Activity
import androidx.activity.result.ActivityResultCaller
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.voice.api.VoiceSearchLauncher
import com.duckduckgo.mobile.android.voice.api.VoiceSearchLauncher.Event
import com.duckduckgo.mobile.android.voice.api.VoiceSearchLauncher.Source
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@ContributesBinding(ActivityScope::class)
class PermissionAwareVoiceSearchLauncher @Inject constructor(
    private val permissionRequest: PermissionRequest,
    private val voiceSearchActivityLauncher: VoiceSearchActivityLauncher,
    private val voiceSearchPermissionCheck: VoiceSearchPermissionCheck
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
        if (voiceSearchPermissionCheck.hasRequiredPermissionsGranted()) {
            voiceSearchActivityLauncher.launch()
        } else {
            permissionRequest.launch()
        }
    }
}
