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

package com.duckduckgo.duckchat.internal.clienttools

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.duckduckgo.common.ui.compose.appbars.DaxTopAppBar
import com.duckduckgo.common.ui.compose.appbars.DaxTopAppBarNavigationIcon
import com.duckduckgo.common.ui.compose.layout.DaxScaffold
import com.duckduckgo.common.ui.compose.listitem.DaxOneLineListItem
import com.duckduckgo.common.ui.compose.listitem.DaxSectionHeader
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.duckchat.internal.R
import com.duckduckgo.duckchat.internal.clienttools.DuckAiClientToolsDevViewModel.BrowserToolsFlag
import com.duckduckgo.duckchat.internal.clienttools.DuckAiClientToolsDevViewModel.FlagGroup
import com.duckduckgo.duckchat.internal.clienttools.DuckAiClientToolsDevViewModel.FlagRow
import com.duckduckgo.duckchat.internal.clienttools.DuckAiClientToolsDevViewModel.ViewState

@Composable
fun DuckAiClientToolsDevScreen(
    viewState: ViewState,
    onBack: () -> Unit,
    onFlagToggled: (BrowserToolsFlag, Boolean) -> Unit,
) {
    DaxScaffold(
        topBar = {
            DaxTopAppBar(
                title = stringResource(R.string.devSettingsDuckAiClientToolsTitle),
                navigationIcon = DaxTopAppBarNavigationIcon.Back(onBack),
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
        ) {
            val groups = viewState.flags.groupBy { it.flag.group }
            FlagGroup.entries.forEach { group ->
                val rows = groups[group] ?: return@forEach
                DaxSectionHeader(title = stringResource(group.headerRes()), overflowMenuEnabled = false)
                rows.forEach { row ->
                    DaxOneLineListItem(
                        primaryText = row.flag.flagName,
                        trailingContent = { Switch(checked = row.enabled, onCheckedChange = { onFlagToggled(row.flag, it) }) },
                    )
                }
            }
        }
    }
}

private fun FlagGroup.headerRes(): Int = when (this) {
    FlagGroup.GENERAL -> R.string.devSettingsDuckAiClientToolsFlagsHeader
    FlagGroup.TOOLS -> R.string.devSettingsDuckAiClientToolsToolsHeader
}

@PreviewLightDark
@Composable
private fun DuckAiClientToolsDevScreenPreview() {
    DuckDuckGoTheme {
        DuckAiClientToolsDevScreen(
            viewState = ViewState(BrowserToolsFlag.entries.map { FlagRow(it, enabled = it != BrowserToolsFlag.SKIP_PERMISSION_PROMPT) }),
            onBack = {},
            onFlagToggled = { _, _ -> },
        )
    }
}
