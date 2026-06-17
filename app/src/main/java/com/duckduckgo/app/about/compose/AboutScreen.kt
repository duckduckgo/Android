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

package com.duckduckgo.app.about.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.duckduckgo.app.browser.R
import com.duckduckgo.common.ui.compose.appbars.DaxTopAppBar
import com.duckduckgo.common.ui.compose.divider.DaxHorizontalDivider
import com.duckduckgo.common.ui.compose.layout.DaxScaffold
import com.duckduckgo.common.ui.compose.listitem.DaxOneLineListItem
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.mobile.android.R as CommonR

@Composable
fun AboutScreen(
    version: String,
    onBack: () -> Unit,
    onLinkClick: (String) -> Unit,
    onPrivacyPolicyClick: () -> Unit,
    onVersionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    DaxScaffold(
        modifier = modifier,
        topBar = {
            DaxTopAppBar(
                title = stringResource(R.string.aboutActivityTitleNew),
                navigationIcon = { Back(onClick = onBack) },
                shadow = true,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                top = dimensionResource(CommonR.dimen.keyline_7),
                bottom = dimensionResource(CommonR.dimen.keyline_5),
            ),
        ) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(CommonR.drawable.logo_full),
                        contentDescription = stringResource(R.string.duckDuckGoLogoDescription),
                        modifier = Modifier.size(150.dp),
                    )
                }
            }
            item {
                DaxText(
                    text = stringResource(R.string.aboutHeaderNewTagLine),
                    style = DuckDuckGoTheme.typography.h3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dimensionResource(CommonR.dimen.keyline_4)),
                )
            }
            item {
                DaxText(
                    text = "...",
                    style = DuckDuckGoTheme.typography.title,
                    color = DuckDuckGoTheme.colors.text.secondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            top = dimensionResource(CommonR.dimen.keyline_3),
                            bottom = dimensionResource(CommonR.dimen.keyline_6),
                        ),
                )
            }
            item {
                DaxText(
                    text = context.aboutDescription(
                        resId = R.string.aboutDescriptionBrandUpdate2025,
                        linkColor = DuckDuckGoTheme.colors.brand.accentBlue,
                        onLinkClick = onLinkClick,
                    ),
                    style = DuckDuckGoTheme.typography.body1,
                    color = DuckDuckGoTheme.colors.text.secondary,
                    modifier = Modifier.padding(horizontal = dimensionResource(CommonR.dimen.keyline_4)),
                )
            }
            item {
                DaxHorizontalDivider(modifier = Modifier.padding(vertical = dimensionResource(CommonR.dimen.keyline_2)))
            }
            item {
                DaxOneLineListItem(
                    text = stringResource(R.string.settingsPrivacyPolicyDuckduckgo),
                    modifier = Modifier.clickable { onPrivacyPolicyClick() },
                    containerColor = DuckDuckGoTheme.colors.backgrounds.background,
                )
            }
            item {
                AboutVersionListItem(
                    primary = stringResource(R.string.settingsVersion),
                    secondary = version,
                    onClick = onVersionClick,
                )
            }
        }
    }
}

/**
 * Local stopgap for the missing ADS Compose two-line list item (primary + secondary,
 * clickable). Backlog: add DaxTwoLineListItem to the design system.
 */
@Composable
private fun AboutVersionListItem(
    primary: String,
    secondary: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        DaxText(text = primary, style = DuckDuckGoTheme.typography.body1)
        DaxText(
            text = secondary,
            style = DuckDuckGoTheme.typography.body2,
            color = DuckDuckGoTheme.colors.text.secondary,
        )
    }
}

@PreviewLightDark
@Composable
private fun AboutScreenPreview() {
    DuckDuckGoTheme {
        AboutScreen(
            version = "5.123.0 (5123000)",
            onBack = {},
            onLinkClick = {},
            onPrivacyPolicyClick = {},
            onVersionClick = {},
        )
    }
}
