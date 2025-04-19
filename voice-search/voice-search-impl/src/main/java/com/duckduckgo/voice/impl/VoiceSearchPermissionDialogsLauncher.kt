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

package com.duckduckgo.voice.impl

import android.content.Context
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.di.scopes.ActivityScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface VoiceSearchPermissionDialogsLauncher {
    fun showNoMicAccessDialog(
        context: Context,
        onSettingsLaunchSelected: () -> Unit = {},
        onSettingsLaunchDeclined: () -> Unit = {},
    )

    fun showPermissionRationale(
        context: Context,
        onRationaleAccepted: () -> Unit = {},
        onRationaleDeclined: () -> Unit = {},
    )

    fun showRemoveVoiceSearchDialog(
        context: Context,
        onRemoveVoiceSearch: () -> Unit = {},
        onRemoveVoiceSearchCancelled: () -> Unit = {},
    )
}

@ContributesBinding(ActivityScope::class)
class RealVoiceSearchPermissionDialogsLauncher @Inject constructor(
    val pixel: Pixel,
) : VoiceSearchPermissionDialogsLauncher {
    override fun showNoMicAccessDialog(
        context: Context,
        onSettingsLaunchSelected: () -> Unit,
        onSettingsLaunchDeclined: () -> Unit,
    ) {
        TextAlertDialogBuilder(context)
            .setTitle(R.string.voiceSearchPermissionRejectedDialogTitle)
            .setMessage(R.string.voiceSearchPermissionRejectedDialogMessage)
            .setPositiveButton(R.string.voiceSearchPermissionRejectedDialogPositiveAction)
            .setNegativeButton(R.string.voiceSearchNegativeAction)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        onSettingsLaunchSelected()
                    }

                    override fun onNegativeButtonClicked() {
                        onSettingsLaunchDeclined()
                    }
                },
            )
            .show()
    }

    override fun showPermissionRationale(
        context: Context,
        onRationaleAccepted: () -> Unit,
        onRationaleDeclined: () -> Unit,
    ) {
        TextAlertDialogBuilder(context)
            .setTitle(R.string.voiceSearchPermissionRationaleTitle)
            .setMessage(R.string.voiceSearchPermissionRationaleDescription)
            .setPositiveButton(R.string.voiceSearchPermissionRationalePositiveAction)
            .setNegativeButton(R.string.voiceSearchNegativeAction)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        onRationaleAccepted()
                    }

                    override fun onNegativeButtonClicked() {
                        onRationaleDeclined()
                    }
                },
            )
            .show()
    }

    override fun showRemoveVoiceSearchDialog(
        context: Context,
        onRemoveVoiceSearch: () -> Unit,
        onRemoveVoiceSearchCancelled: () -> Unit,
    ) {
        TextAlertDialogBuilder(context)
            .setTitle(R.string.voiceSearchRemoveTitle)
            .setMessage(R.string.voiceSearchRemoveSubtitle)
            .setPositiveButton(R.string.voiceSearchRemovePositiveButton)
            .setNegativeButton(R.string.voiceSearchNegativeAction)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        onRemoveVoiceSearch()
                        pixel.fire(pixel = VoiceSearchPixelNames.VOICE_SEARCH_REMOVE_DIALOG_REMOVE)
                    }

                    override fun onNegativeButtonClicked() {
                        onRemoveVoiceSearchCancelled()
                        pixel.fire(pixel = VoiceSearchPixelNames.VOICE_SEARCH_REMOVE_DIALOG_CANCEL)
                    }
                },
            )
            .show()

        pixel.fire(pixel = VoiceSearchPixelNames.VOICE_SEARCH_REMOVE_DIALOG_SEEN)
    }
}
