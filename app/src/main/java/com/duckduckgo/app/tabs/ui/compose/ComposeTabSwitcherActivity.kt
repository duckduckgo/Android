/*
 * Copyright (c) 2025 DuckDuckGo
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

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.duckduckgo.app.tabs.ui.compose

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size.Companion.Zero
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.browser.tabpreview.WebViewPreviewPersister
import com.duckduckgo.app.tabs.model.TabSwitcherData.LayoutType.GRID
import com.duckduckgo.app.tabs.model.TabSwitcherData.LayoutType.LIST
import com.duckduckgo.app.tabs.ui.GridViewColumnCalculator
import com.duckduckgo.app.tabs.ui.TabSwitcherActivity.Companion.EXTRA_KEY_DELETED_TAB_IDS
import com.duckduckgo.app.tabs.ui.compose.ComposeTabSwitcherActivity.Companion.ComposeTabSwitcherScreenParams
import com.duckduckgo.app.tabs.ui.compose.ComposeTabSwitcherCommand.Close
import com.duckduckgo.app.tabs.ui.compose.ComposeTabSwitcherCommand.CloseAllTabsRequest
import com.duckduckgo.app.tabs.ui.compose.ComposeTabSwitcherCommand.DismissAnimatedTileDismissalDialog
import com.duckduckgo.app.tabs.ui.compose.ComposeTabSwitcherCommand.ShowAnimatedTileDismissalDialog
import com.duckduckgo.app.tabs.ui.compose.ComposeTabSwitcherItem.NewTab
import com.duckduckgo.app.tabs.ui.compose.ComposeTabSwitcherItem.WebTab
import com.duckduckgo.app.tabs.ui.compose.ComposeTabSwitcherViewState.Mode.Normal
import com.duckduckgo.app.tabs.ui.compose.ComposeTabSwitcherViewState.Mode.Selection
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.compose.component.tabs.NewTabGridCard
import com.duckduckgo.common.ui.compose.component.tabs.NewTabListCard
import com.duckduckgo.common.ui.compose.component.tabs.WebTabGridCard
import com.duckduckgo.common.ui.compose.component.tabs.WebTabListCard
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.R
import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams
import java.util.ArrayList
import javax.inject.Inject
import kotlin.jvm.java
import kotlin.math.abs
import com.duckduckgo.app.browser.R as BrowserR

val fakeComposeFeatureFlag = true

private const val TAB_GRID_COLUMN_WIDTH_DP = 180
private const val TAB_GRID_MAX_COLUMN_COUNT = 4

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(
    paramsType = ComposeTabSwitcherScreenParams::class,
    screenName = "tabSwitcher",
)
class ComposeTabSwitcherActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var faviconManager: FaviconManager

    @Inject
    lateinit var webViewPreviewPersister: WebViewPreviewPersister

    @Inject
    lateinit var gridViewColumnCalculator: GridViewColumnCalculator

    private val viewModel: ComposeTabSwitcherViewModel by bindViewModel()

    @OptIn(ExperimentalMaterialApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val viewState by viewModel.viewState.collectAsStateWithLifecycle()

            val gridNumberOfColumns = remember {
                gridViewColumnCalculator.calculateNumberOfColumns(
                    TAB_GRID_COLUMN_WIDTH_DP,
                    TAB_GRID_MAX_COLUMN_COUNT,
                )
            }

            // TODO we could _use_ a CompositionLocalProvider here, not sure it make sense moving to the VM
            val numberOfColumns by remember(viewState.layoutType) {
                derivedStateOf {
                    when (viewState.layoutType) {
                        GRID -> gridNumberOfColumns
                        LIST -> 1
                    }
                }
            }

            LaunchedEffect(viewModel.commands) {
                viewModel.commands.collect(::processCommand)
            }

            DuckDuckGoTheme(isDesignExperimentEnabled = true) {
                TabSwitcherScreen(
                    viewModel = viewModel,
                    numberOfColumns = numberOfColumns,
                )
            }
        }
    }

    private fun processCommand(command: ComposeTabSwitcherCommand) {
        when (command) {
            Close -> finishAfterTransition()
            CloseAllTabsRequest -> {
                // TODO
            }
            is ComposeTabSwitcherCommand.CloseAndShowUndoMessage -> {
                // TODO missing skipTabPurge?
                setResult(
                    RESULT_OK,
                    Intent().apply {
                        putStringArrayListExtra(EXTRA_KEY_DELETED_TAB_IDS, ArrayList(command.deletedTabIds))
                    },
                )
                finishAfterTransition()
            }
            DismissAnimatedTileDismissalDialog -> {
                // TODO
            }
            ShowAnimatedTileDismissalDialog -> {
                // TODO
            }

            is ComposeTabSwitcherCommand.ShowUndoDeleteTabsMessage -> {
                // TODO
            }
        }
    }

    companion object {

        data class ComposeTabSwitcherScreenParams(val selectedTabId: String? = null) :
            ActivityParams

        fun intent(
            context: Context,
            selectedTabId: String? = null,
        ): Intent = Intent(context, ComposeTabSwitcherActivity::class.java).apply {
            putExtra(EXTRA_KEY_SELECTED_TAB, selectedTabId)
        }

        const val EXTRA_KEY_SELECTED_TAB = "selected"
    }
}
