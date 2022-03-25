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

import android.app.AlertDialog
import android.content.Context
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface VoiceSearchPermissionDialogsLauncher {
    fun showNoMicAccessDialog(
        context: Context,
        onSettingsLaunchSelected: () -> Unit = {},
        onSettingsLaunchDeclined: () -> Unit = {}
    )

    fun showPermissionRationale(
        context: Context,
        onRationaleAccepted: () -> Unit = {},
        onRationaleDeclined: () -> Unit = {}
    )
}

@ContributesBinding(AppScope::class)
class RealVoiceSearchPermissionDialogsLauncher @Inject constructor() : VoiceSearchPermissionDialogsLauncher {
    override fun showNoMicAccessDialog(
        context: Context,
        onSettingsLaunchSelected: () -> Unit,
        onSettingsLaunchDeclined: () -> Unit
    ) {
        AlertDialog.Builder(context)
            .setTitle(R.string.voiceSearchPermissionRejectedDialogTitle)
            .setMessage(R.string.voiceSearchPermissionRejectedDialogMessage)
            .setPositiveButton(R.string.voiceSearchPermissionRejectedDialogPositiveAction) { _, _ ->
                onSettingsLaunchSelected()
            }
            .setNegativeButton(R.string.voiceSearchNegativeAction) { _, _ ->
                onSettingsLaunchDeclined()
            }
            .show()
    }

    override fun showPermissionRationale(
        context: Context,
        onRationaleAccepted: () -> Unit,
        onRationaleDeclined: () -> Unit
    ) {

        AlertDialog.Builder(context)
            .setTitle(R.string.voiceSearchPermissionRationaleTitle)
            .setMessage(R.string.voiceSearchPermissionRationaleDescription)
            .setPositiveButton(R.string.voiceSearchPermissionRationalePositiveAction) { _, _ ->
                onRationaleAccepted()
            }
            .setNegativeButton(R.string.voiceSearchNegativeAction) { _, _ ->
                onRationaleDeclined()
            }
            .show()
    }
}
