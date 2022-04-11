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

import android.Manifest
import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.voice.impl.ActivityResultLauncherWrapper.Action
import com.duckduckgo.mobile.android.voice.impl.ActivityResultLauncherWrapper.Action.LaunchPermissionRequest
import com.duckduckgo.mobile.android.voice.impl.ActivityResultLauncherWrapper.Action.LaunchVoiceSearch
import com.duckduckgo.mobile.android.voice.impl.ActivityResultLauncherWrapper.Request
import com.duckduckgo.mobile.android.voice.impl.ActivityResultLauncherWrapper.Request.Permission
import com.duckduckgo.mobile.android.voice.impl.ActivityResultLauncherWrapper.Request.ResultFromVoiceSearch
import com.duckduckgo.mobile.android.voice.impl.listeningmode.VoiceSearchActivity
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface ActivityResultLauncherWrapper {
    fun register(
        caller: ActivityResultCaller,
        request: Request
    )

    fun launch(action: Action)

    sealed class Request {
        data class Permission(val onResult: (Boolean) -> Unit) : Request()
        data class ResultFromVoiceSearch(
            val onResult: (Int, String) -> Unit,
        ) : Request()
    }

    enum class Action {
        LaunchPermissionRequest,
        LaunchVoiceSearch
    }
}

@ContributesBinding(ActivityScope::class)
class RealActivityResultLauncherWrapper @Inject constructor(
    private val context: Context
) : ActivityResultLauncherWrapper {

    private lateinit var permissionLauncher: ActivityResultLauncher<String>
    private lateinit var voiceSearchActivityLaucher: ActivityResultLauncher<Intent>

    override fun register(
        caller: ActivityResultCaller,
        request: Request
    ) {
        when (request) {
            is Permission -> registerPermissionRequest(caller, request.onResult)
            is ResultFromVoiceSearch -> registerResultFromVoiceSearch(caller, request.onResult)
        }
    }

    private fun registerResultFromVoiceSearch(
        caller: ActivityResultCaller,
        onResult: (Int, String) -> Unit,
    ) {
        voiceSearchActivityLaucher = caller.registerForActivityResult(StartActivityForResult()) {
            onResult(it.resultCode, it.data?.getStringExtra(VoiceSearchActivity.EXTRA_VOICE_RESULT) ?: "")
        }
    }

    private fun registerPermissionRequest(
        caller: ActivityResultCaller,
        onResult: (Boolean) -> Unit
    ) {
        permissionLauncher = caller.registerForActivityResult(RequestPermission()) {
            onResult(it)
        }
    }

    override fun launch(action: Action) {
        when (action) {
            LaunchPermissionRequest -> launchPermissionRequest()
            LaunchVoiceSearch -> launchVoiceSearch()
        }
    }

    private fun launchVoiceSearch() {
        voiceSearchActivityLaucher.launch(Intent(context, VoiceSearchActivity::class.java))
    }

    private fun launchPermissionRequest() {
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
}
