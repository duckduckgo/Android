/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.tabs.rules.visitedsites

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons.AutoMirrored
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.tabs.rules.Async
import com.duckduckgo.app.tabs.rules.Fail
import com.duckduckgo.app.tabs.rules.Incomplete
import com.duckduckgo.app.tabs.rules.Success
import com.duckduckgo.app.tabs.rules.TabRulesVisitedSitesScreen
import com.duckduckgo.app.tabs.rules.visitedsites.VisitedSite.ExistingTab
import com.duckduckgo.app.tabs.rules.visitedsites.VisitedSite.HistoricalSite
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.di.scopes.ActivityScope
import javax.inject.Inject
import com.duckduckgo.mobile.android.R as CommonR
import com.duckduckgo.app.browser.R as AppR

val LocalFaviconManager = compositionLocalOf<FaviconManager> { error("No LocalFaviconManager provided") }

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(TabRulesVisitedSitesScreen::class)
class TabRulesVisitedSitesActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var tabRulesVisitedSitesViewModel: TabRulesVisitedSitesViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DuckDuckGoTheme {
                TabRulesVisitedSitesScreen(
                    viewModel = tabRulesVisitedSitesViewModel,
                    onBackPressed = { finish() },
                )
            }
        }
    }
}

@Composable
private fun TabRulesVisitedSitesScreen(
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TabRulesVisitedSitesViewModel = viewModel(),
) {
    val viewState by viewModel.state.collectAsStateWithLifecycle()

    TabRulesVisitedSitesScreen(
        visitedSites = viewState.visitedSites,
        onCheckedChanged = { isChecked, visitedSite ->
            viewModel.onAddSiteRuleStateChanged(isChecked, visitedSite)
        },
        onBackPressed = onBackPressed,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabRulesVisitedSitesScreen(
    visitedSites: Async<List<VisitedSite>>,
    onCheckedChanged: (isChecked: Boolean, visitedSite: VisitedSite) -> Unit,
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        containerColor = DuckDuckGoTheme.colors.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarColors(
                    containerColor = DuckDuckGoTheme.colors.background,
                    scrolledContainerColor = DuckDuckGoTheme.colors.background,
                    navigationIconContentColor = DuckDuckGoTheme.colors.backgroundInverted,
                    titleContentColor = DuckDuckGoTheme.colors.backgroundInverted,
                    actionIconContentColor = DuckDuckGoTheme.colors.backgroundInverted,
                ),
                title = {
                    Text(
                        text = "Add Website",
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(AutoMirrored.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { contentPadding ->

        Column(
            modifier = modifier
                    .padding(contentPadding)
                    .fillMaxSize()
                    .padding(vertical = 16.dp),
        ) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (visitedSites) {
                    is Incomplete -> { /* Loading */
                    }

                    is Success -> {
                        items(visitedSites()) { site ->
                            VisitedSiteItem(
                                site = site,
                                onCheckedChanged = { isChecked ->
                                    onCheckedChanged(isChecked, site)
                                },
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }

                    is Fail -> Unit
                }
            }
        }
    }
}

@Composable
private fun VisitedSiteItem(
    site: VisitedSite,
    onCheckedChanged: (isChecked: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardColors(
            containerColor = DuckDuckGoTheme.colors.surface,
            contentColor = DuckDuckGoTheme.colors.primaryText,
            disabledContainerColor = DuckDuckGoTheme.colors.buttonColors.primaryContainerDisabled,
            disabledContentColor = DuckDuckGoTheme.colors.buttonColors.primaryTextDisabled,
        ),
        shape = DuckDuckGoTheme.shapes.small,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                    .padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 4.dp)
                    .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {

            Image(
                painter = site.faviconBitmap?.let { remember { BitmapPainter(it.asImageBitmap()) } }
                    ?: painterResource(id = AppR.drawable.ic_globe_20),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )

            Column(
                modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
            ) {
                Text(
                    text = when(site) {
                        is ExistingTab -> "${site.count} tab${if (site.count > 1) "s" else ""}"
                        is HistoricalSite -> "${site.count} visit${if (site.count > 1) "s" else ""}"
                    },
                    style = DuckDuckGoTheme.typography.caption,
                )

                Spacer(Modifier.size(8.dp))

                Text(
                    text = site.title,
                    style = DuckDuckGoTheme.typography.body1,
                )

                Spacer(Modifier.size(4.dp))

                Text(
                    text = site.displayUrl,
                    style = DuckDuckGoTheme.typography.body2,
                )
            }

            AddedStateIcon(
                isAdded = site.isEnabled,
                onCheckedChange = onCheckedChanged,
            )
        }
    }
}

@Composable
private fun AddedStateIcon(
    isAdded: Boolean,
    onCheckedChange: (isAdded: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = {
            onCheckedChange.invoke(!isAdded)
        },
        modifier = modifier,
    ) {
        val painter = if (isAdded) {
            painterResource(id = CommonR.drawable.ic_check_recolorable_24)
        } else {
            painterResource(id = CommonR.drawable.ic_add_circle_24)
        }

        val tint = if (isAdded) {
            Color.Unspecified
        } else {
            DuckDuckGoTheme.colors.primaryIcon
        }

        val contentDescription = if (isAdded) {
            "Remove Website" // TODO make better!
        } else {
            "Add Website"
        }

        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = tint,
        )
    }
}
