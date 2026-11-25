package com.example.ui.tabs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.BrowserTab
import com.example.privacy.PrivacyEngine
import com.example.ui.theme.DaxCoral
import com.example.ui.theme.DaxGreen50
import com.example.viewmodel.BrowserViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabSwitcherScreen(
    viewModel: BrowserViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showOverflowMenu by remember { mutableStateOf(false) }

    BackHandler { onBack() }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "${uiState.tabs.size} ${if (uiState.tabs.size == 1) "Tab" else "Tabs"}",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Navigate up",
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = viewModel::toggleTabLayoutMode,
                        modifier = Modifier.testTag("toggle_tab_layout_button"),
                    ) {
                        Icon(
                            imageVector = if (uiState.isGridTabLayout) Icons.Outlined.ViewAgenda else Icons.Outlined.GridView,
                            contentDescription = "Switch tab layout",
                        )
                    }
                    IconButton(
                        onClick = {
                            viewModel.openNewTab(isFireTab = true)
                            onBack()
                        },
                        modifier = Modifier.testTag("new_fire_tab_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.LocalFireDepartment,
                            contentDescription = "New Fire Tab",
                            tint = DaxCoral,
                        )
                    }
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "More tab options")
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("New Tab") },
                                leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.openNewTab(isFireTab = false)
                                    onBack()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("New Fire Tab") },
                                leadingIcon = { Icon(Icons.Outlined.LocalFireDepartment, contentDescription = null, tint = DaxCoral) },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.openNewTab(isFireTab = true)
                                    onBack()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Close All Tabs") },
                                leadingIcon = { Icon(Icons.Outlined.DeleteSweep, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.closeAllTabs()
                                    onBack()
                                },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    viewModel.openNewTab(isFireTab = false)
                    onBack()
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("new_tab_fab"),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "New Tab")
            }
        },
    ) { innerPadding ->
        if (uiState.isGridTabLayout) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                itemsIndexed(uiState.tabs, key = { _, tab -> tab.id }) { index, tab ->
                    TabCard(
                        tab = tab,
                        isSelected = index == uiState.activeTabIndex,
                        onSelect = {
                            viewModel.selectTab(index)
                            onBack()
                        },
                        onClose = { viewModel.closeTab(tab.id) },
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                itemsIndexed(uiState.tabs, key = { _, tab -> tab.id }) { index, tab ->
                    TabListRow(
                        tab = tab,
                        isSelected = index == uiState.activeTabIndex,
                        onSelect = {
                            viewModel.selectTab(index)
                            onBack()
                        },
                        onClose = { viewModel.closeTab(tab.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TabCard(
    tab: BrowserTab,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
) {
    val border = if (isSelected) {
        BorderStroke(2.dp, if (tab.isFireTab) DaxCoral else MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        border = border,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .height(152.dp)
            .clickable(onClick = onSelect)
            .testTag("tab_card_${tab.id}"),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (tab.isFireTab) DaxCoral.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
                    )
                    .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (tab.isFireTab) Icons.Outlined.LocalFireDepartment else Icons.Outlined.Public,
                    contentDescription = null,
                    tint = if (tab.isFireTab) DaxCoral else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = tab.title.ifBlank { "New Tab" },
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("close_tab_${tab.id}"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close tab",
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (tab.isNewTabPage) {
                        if (tab.isFireTab) "Private Fire Tab" else "New Tab Page"
                    } else {
                        PrivacyEngine.extractHost(tab.url) ?: tab.url
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                if (tab.blockedTrackers.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = DaxGreen50.copy(alpha = 0.14f),
                    ) {
                        Text(
                            text = "${tab.blockedTrackers.size} trackers blocked",
                            style = MaterialTheme.typography.labelSmall,
                            color = DaxGreen50,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabListRow(
    tab: BrowserTab,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (tab.isFireTab) DaxCoral.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (tab.isFireTab) Icons.Outlined.LocalFireDepartment else Icons.Outlined.Public,
                    contentDescription = null,
                    tint = if (tab.isFireTab) DaxCoral else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tab.title.ifBlank { "New Tab" },
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (tab.isNewTabPage) "New Tab Page" else tab.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.Close, contentDescription = "Close tab")
            }
        }
    }
}
