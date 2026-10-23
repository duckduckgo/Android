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

package com.duckduckgo.site.permissions.impl.ui

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.duckduckgo.common.ui.view.dialog.DaxAlertDialog
import com.duckduckgo.common.ui.view.dialog.RadioListAlertDialogBuilder
import com.duckduckgo.common.ui.view.dialog.RadioListOption
import com.duckduckgo.site.permissions.impl.R
import com.duckduckgo.mobile.android.R as CommonR

private const val ASK_EACH_TIME_POSITION = 1

internal enum class GlobalPermission(
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
    @param:DrawableRes val blockedIcon: Int,
    @param:StringRes val dialogTitle: Int,
    @param:StringRes val dialogMessage: Int,
    @param:StringRes val settingTitle: Int = label,
) {
    LOCATION(
        R.string.sitePermissionsSettingsLocation,
        CommonR.drawable.ic_location_24,
        CommonR.drawable.ic_location_blocked_24,
        R.string.permissionSettingsLocationDialogTitle,
        R.string.permissionSettingsLocationDialogMessage,
    ),
    CAMERA(
        R.string.sitePermissionsSettingsCamera,
        CommonR.drawable.ic_video_24,
        CommonR.drawable.ic_video_blocked_24,
        R.string.permissionSettingsCameraDialogTitle,
        R.string.permissionSettingsCameraDialogMessage,
    ),
    MICROPHONE(
        R.string.sitePermissionsSettingsMicrophone,
        CommonR.drawable.ic_microphone_24,
        CommonR.drawable.ic_microphone_blocked_24,
        R.string.permissionSettingsMicrophoneDialogTitle,
        R.string.permissionSettingsMicrophoneDialogMessage,
    ),
    DRM(
        R.string.sitePermissionsSettingsDRM,
        CommonR.drawable.ic_video_player_24,
        CommonR.drawable.ic_video_player_blocked_24,
        R.string.permissionSettingsDrmDialogTitle,
        R.string.permissionSettingsDrmDialogMessage,
        R.string.permissionSettingsDrmTitle,
    ),
    ;

    companion object {
        fun from(@StringRes label: Int): GlobalPermission? = entries.firstOrNull { it.label == label }
    }
}

fun showGlobalPermissionDialog(
    context: Context,
    @StringRes permission: Int,
    askEnabled: Boolean,
    onSelectionChanged: (askEnabled: Boolean) -> Unit,
    onDismissed: () -> Unit,
    onSave: (askEnabled: Boolean) -> Unit,
): DaxAlertDialog? {
    val content = GlobalPermission.from(permission) ?: return null
    return RadioListAlertDialogBuilder(context)
        .setRebrandUpdate(true)
        .setCancelable(true)
        .setHeaderImageResource(content.icon)
        .setTitle(content.dialogTitle)
        .setMessage(content.dialogMessage)
        .setOptions(
            listOf(
                RadioListOption(R.string.permissionSettingsAskEachTime, isSelected = askEnabled),
                RadioListOption(R.string.sitePermissionsDialogNeverAllowButton, isSelected = !askEnabled),
            ),
        )
        .setPositiveButton(CommonR.string.dialogSave)
        .setNegativeButton(CommonR.string.cancel)
        .addEventListener(
            object : RadioListAlertDialogBuilder.EventListener() {
                override fun onRadioItemSelected(selectedItem: Int) {
                    onSelectionChanged(selectedItem == ASK_EACH_TIME_POSITION)
                }

                override fun onDialogDismissed() {
                    onDismissed()
                }

                override fun onPositiveButtonClicked(selectedItem: Int) {
                    onSave(selectedItem == ASK_EACH_TIME_POSITION)
                }
            },
        )
        .build()
        .also { it.show() }
}
