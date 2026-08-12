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

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.view.ViewGroup
import com.duckduckgo.common.ui.view.dialog.StackedAlertDialogBuilder
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.voice.api.VoiceSearchLauncher.VoiceSearchMode
import com.google.android.material.snackbar.Snackbar
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import com.duckduckgo.mobile.android.R as CommonR

interface VoiceSearchPermissionDialogsLauncher {
    fun showNoMicAccessDialog(
        context: Context,
        onSettingsLaunchSelected: () -> Unit = {},
        onSettingsLaunchDeclined: () -> Unit = {},
    )

    fun showMicAccessDeniedDialog(
        context: Context,
        mode: VoiceSearchMode?,
        onChangePermissionsSelected: () -> Unit = {},
        onHideVoiceSearchSelected: () -> Unit = {},
        onCancelled: () -> Unit = {},
    )

    fun showMicPermissionDeniedSnackbar(
        activity: Activity,
        onAllowSelected: () -> Unit = {},
    )

    fun showPermissionRationale(
        context: Context,
        onRationaleAccepted: () -> Unit = {},
        onRationaleDeclined: () -> Unit = {},
    )
}

@ContributesBinding(ActivityScope::class)
class RealVoiceSearchPermissionDialogsLauncher @Inject constructor() : VoiceSearchPermissionDialogsLauncher {

    companion object {
        private const val CHANGE_PERMISSIONS_BUTTON = 0
        private const val HIDE_VOICE_SEARCH_BUTTON = 1
    }

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

    override fun showMicAccessDeniedDialog(
        context: Context,
        mode: VoiceSearchMode?,
        onChangePermissionsSelected: () -> Unit,
        onHideVoiceSearchSelected: () -> Unit,
        onCancelled: () -> Unit,
    ) {
        // Duck.ai has no microphone in the address bar to hide, and its own wording for what the
        // permission unlocks.
        val isDuckAiMode = mode == VoiceSearchMode.DUCK_AI
        val message = if (isDuckAiMode) {
            R.string.voiceSearchMicAccessDeniedDialogMessageDuckAi
        } else {
            R.string.voiceSearchMicAccessDeniedDialogMessage
        }
        val buttons = buildList {
            add(R.string.voiceSearchMicAccessDeniedDialogChangePermissions)
            if (!isDuckAiMode) {
                add(R.string.voiceSearchMicAccessDeniedDialogHideVoiceSearch)
            }
            add(R.string.voiceSearchNegativeAction)
        }

        StackedAlertDialogBuilder(context)
            .setHeaderImageResource(CommonR.drawable.ic_microphone_24)
            .setTitle(R.string.voiceSearchMicAccessDeniedDialogTitle)
            .setMessage(message)
            .setStackedButtons(buttons)
            .addEventListener(
                object : StackedAlertDialogBuilder.EventListener() {
                    override fun onButtonClicked(position: Int) {
                        when (position) {
                            CHANGE_PERMISSIONS_BUTTON -> onChangePermissionsSelected()
                            HIDE_VOICE_SEARCH_BUTTON -> if (isDuckAiMode) onCancelled() else onHideVoiceSearchSelected()
                            else -> onCancelled()
                        }
                    }
                },
            )
            .show()
    }

    override fun showMicPermissionDeniedSnackbar(
        activity: Activity,
        onAllowSelected: () -> Unit,
    ) {
        val rootView = activity.window?.decorView?.rootView ?: return
        val snackbar = Snackbar.make(rootView, R.string.voiceSearchMicPermissionDeniedSnackbarMessage, Snackbar.LENGTH_LONG)
        if (activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            val layoutParams = snackbar.view.layoutParams as ViewGroup.MarginLayoutParams
            layoutParams.setMargins(layoutParams.leftMargin, layoutParams.topMargin, layoutParams.rightMargin, 32.toPx())
            snackbar.view.layoutParams = layoutParams
        }
        snackbar
            .setAction(R.string.voiceSearchMicPermissionDeniedSnackbarAction) { onAllowSelected() }
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
}
