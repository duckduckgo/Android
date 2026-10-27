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

package com.duckduckgo.app.dev.settings.quiz

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import com.duckduckgo.common.ui.compose.Status
import com.duckduckgo.common.ui.compose.contextmenu.DaxContextMenu
import com.duckduckgo.common.ui.compose.listitem.DaxListItemIconSize
import com.duckduckgo.common.ui.compose.listitem.DaxOneLineListItem
import com.duckduckgo.common.ui.compose.listitem.DaxSectionHeader
import com.duckduckgo.common.ui.compose.listitem.DaxSettingsListItem
import com.duckduckgo.common.ui.compose.listitem.DaxTwoLineListItem
import com.duckduckgo.common.ui.menu.PopupMenu
import com.duckduckgo.common.ui.view.PopupMenuItemView
import com.duckduckgo.common.ui.view.listitem.OneLineListItem
import com.duckduckgo.common.ui.view.listitem.SectionHeaderListItem
import com.duckduckgo.common.ui.view.listitem.TwoLineListItem
import com.duckduckgo.mobile.android.R
import com.duckduckgo.app.browser.R as AppR

internal val listQuizRenderers: Map<QuestionId, QuizRenderer> = mapOf(
    QuestionId.OneLineListItem to quizRenderer(
        createView = { context ->
            inflateSample(context, AppR.layout.quiz_view_one_line_list_items).also { root ->
                root.findViewById<OneLineListItem>(AppR.id.quizOneLineOverflow).setTrailingIconClickListener { anchor ->
                    showOverflowMenu(root, anchor)
                }
                root.findViewById<OneLineListItem>(AppR.id.quizOneLineDisabled).isEnabled = false
            }
        },
        composeSample = {
            var automaticUpdates by remember { mutableStateOf(false) }
            Column {
                DaxOneLineListItem(
                    primaryText = "Privacy dashboard",
                    leadingContent = { Icon(painterResource(R.drawable.ic_globe_24), null, size = DaxListItemIconSize.Small) },
                    trailingContent = {
                        OverflowMenu { onClick -> Icon(painterResource(R.drawable.ic_menu_vertical_24), "More", onClick = onClick) }
                    },
                )
                DaxOneLineListItem(
                    primaryText = "Automatic updates",
                    inlineContent = { Pill("New") },
                    trailingContent = { Switch(checked = automaticUpdates, onCheckedChange = { automaticUpdates = it }) },
                )
                DaxOneLineListItem(
                    primaryText = "This disabled privacy setting has a long label that wraps safely",
                    enabled = false,
                )
            }
        },
    ),
    QuestionId.TwoLineListItem to quizRenderer(
        createView = { context ->
            inflateSample(context, AppR.layout.quiz_view_two_line_list_items).also { root ->
                root.findViewById<TwoLineListItem>(AppR.id.quizTwoLineOverflow).setTrailingIconClickListener { anchor ->
                    showOverflowMenu(root, anchor)
                }
                root.findViewById<TwoLineListItem>(AppR.id.quizTwoLineDisabled).isEnabled = false
            }
        },
        composeSample = {
            var automaticBackup by remember { mutableStateOf(false) }
            Column {
                DaxTwoLineListItem(
                    primaryText = "Sync bookmarks",
                    secondaryText = "Keep your bookmarks private",
                    leadingContent = { Icon(painterResource(R.drawable.ic_globe_24), null, size = DaxListItemIconSize.Small) },
                    trailingContent = {
                        OverflowMenu { onClick -> Icon(painterResource(R.drawable.ic_menu_vertical_24), "More", onClick = onClick) }
                    },
                )
                DaxTwoLineListItem(
                    primaryText = "Automatic backup",
                    secondaryText = "Back up bookmarks on Wi-Fi",
                    inlineContent = { Pill("Beta") },
                    trailingContent = { Switch(checked = automaticBackup, onCheckedChange = { automaticBackup = it }) },
                )
                DaxTwoLineListItem(
                    primaryText = "This disabled privacy setting has a long label that wraps safely",
                    secondaryText = "Its explanatory text also remains visible",
                    enabled = false,
                )
            }
        },
    ),
    QuestionId.SettingsListItem to quizRenderer(
        createView = { context -> inflateSample(context, AppR.layout.quiz_view_settings_list_items) },
        composeSample = {
            Column {
                DaxSettingsListItem(
                    primaryText = "Privacy Protection",
                    leadingContent = { Image(painterResource(R.drawable.ic_dax_icon), null, size = DaxListItemIconSize.Small) },
                    trailingContent = { StatusIndicator(Status.On) },
                )
                DaxSettingsListItem(
                    primaryText = "A long privacy setting label that wraps safely",
                    inlineContent = { Pill("Beta") },
                    leadingContent = { Image(painterResource(R.drawable.ic_dax_icon), null, size = DaxListItemIconSize.Small) },
                    trailingContent = { StatusIndicator(Status.On) },
                )
            }
        },
    ),
    QuestionId.SectionHeader to quizRenderer(
        createView = { context ->
            SectionHeaderListItem(context).apply {
                primaryText = "Privacy settings"
                revertUpperCaseTitleText()
            }
        },
        composeSample = { DaxSectionHeader(title = "Privacy settings") },
    ),
)

private fun showOverflowMenu(root: View, anchor: View) {
    val popupMenu = PopupMenu(LayoutInflater.from(anchor.context), AppR.layout.quiz_popup_menu)
    val menuView = popupMenu.contentView
    popupMenu.onMenuItemClicked(menuView.findViewById(AppR.id.quizMenuBookmark)) {}
    popupMenu.onMenuItemClicked(menuView.findViewById(AppR.id.quizMenuCopyLink)) {}
    menuView.findViewById<PopupMenuItemView>(AppR.id.quizMenuUnavailable).setDisabled()
    popupMenu.onMenuItemClicked(menuView.findViewById(AppR.id.quizMenuDelete)) {}
    popupMenu.show(root, anchor)
}

@Composable
private fun OverflowMenu(anchor: @Composable (onClick: () -> Unit) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    DaxContextMenu(
        anchor = { anchor { expanded = true } },
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        DaxIconItem(text = "Bookmark", painterLeadingIcon = painterResource(R.drawable.ic_bookmark_24), onClick = {})
        DaxDefaultItem(text = "Copy link", onClick = {}, trailingIcon = { Icon(painterResource(R.drawable.ic_copy_24), null) })
        DaxDefaultItem(text = "Unavailable", onClick = {}, enabled = false)
        DaxDefaultItem(text = "Delete", onClick = {}, isDestructive = true)
    }
}

private fun inflateSample(context: Context, layoutId: Int) =
    LayoutInflater.from(context).inflate(layoutId, null, false)
