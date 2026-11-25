package com.example.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.model.OmnibarPosition
import com.example.model.ThemeMode
import com.example.ui.theme.DaxCoral
import com.example.viewmodel.BrowserViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: BrowserViewModel,
    onOpenPrivacySettings: () -> Unit,
    onOpenDataClearing: () -> Unit,
    onOpenAppearance: () -> Unit,
    onBack: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val fireproofSites by viewModel.fireproofSites.collectAsStateWithLifecycle()

    BackHandler { onBack() }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Navigate up")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding),
        ) {
            SettingsSectionLabel("Protections")
            SettingsNavRow(
                icon = Icons.Outlined.Shield,
                title = "Privacy & Ad Blocking",
                subtitle = if (settings.trackerBlockingEnabled) "Tracker blocking active" else "Tracker blocking paused",
                onClick = onOpenPrivacySettings,
                testTag = "settings_privacy_row",
            )
            SettingsNavRow(
                icon = Icons.Outlined.LocalFireDepartment,
                title = "Data Clearing",
                subtitle = "${fireproofSites.size} fireproofed sites • Auto-clear ${if (settings.autoClearOnExit) "On" else "Off"}",
                onClick = onOpenDataClearing,
                testTag = "settings_data_clearing_row",
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

            SettingsSectionLabel("General")
            SettingsNavRow(
                icon = Icons.Outlined.Palette,
                title = "Appearance",
                subtitle = "${settings.themeMode.name.lowercase().replaceFirstChar { it.uppercase() }} theme • Omnibar ${settings.omnibarPosition.name.lowercase()}",
                onClick = onOpenAppearance,
                testTag = "settings_appearance_row",
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

            SettingsSectionLabel("About")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("DuckDuckGo Privacy Browser", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "Version ${BuildConfig.VERSION_NAME} • ${settings.totalTrackersBlocked} trackers blocked",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySettingsScreen(
    viewModel: BrowserViewModel,
    onBack: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    BackHandler { onBack() }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Privacy & Ad Blocking") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Navigate up")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding),
        ) {
            SettingsToggleRow(
                title = "Block Third-Party Trackers",
                subtitle = "Intercepts known advertising, analytics, and fingerprinting scripts before they load.",
                checked = settings.trackerBlockingEnabled,
                onCheckedChange = viewModel::setTrackerBlockingEnabled,
            )
            SettingsToggleRow(
                title = "Automatic HTTPS Upgrades",
                subtitle = "Automatically upgrades unencrypted HTTP links to HTTPS whenever possible.",
                checked = settings.httpsUpgradesEnabled,
                onCheckedChange = viewModel::setHttpsUpgradesEnabled,
            )
            SettingsToggleRow(
                title = "Strip Tracking Link Parameters",
                subtitle = "Removes utm_*, fbclid, gclid, and similar tracking tags from URLs.",
                checked = settings.stripTrackingParamsEnabled,
                onCheckedChange = viewModel::setStripTrackingParamsEnabled,
            )
            SettingsToggleRow(
                title = "Global Privacy Control (GPC)",
                subtitle = "Sends Sec-GPC: 1 signals telling websites not to sell or share your personal data.",
                checked = settings.gpcEnabled,
                onCheckedChange = viewModel::setGpcEnabled,
            )
            SettingsToggleRow(
                title = "Block Ads on YouTube",
                subtitle = "Hides intrusive ad containers and overlays during video playback.",
                checked = settings.youtubeAdBlockingEnabled,
                onCheckedChange = viewModel::setYoutubeAdBlockingEnabled,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataClearingSettingsScreen(
    viewModel: BrowserViewModel,
    onOpenFireproofSites: () -> Unit,
    onBack: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val fireproofSites by viewModel.fireproofSites.collectAsStateWithLifecycle()
    BackHandler { onBack() }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Data Clearing") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Navigate up")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            SettingsNavRow(
                icon = Icons.Outlined.Whatshot,
                title = "Fireproof Sites",
                subtitle = "${fireproofSites.size} site(s) excluded from Fire Button clearing",
                onClick = onOpenFireproofSites,
                testTag = "fireproof_sites_row",
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsToggleRow(
                title = "Automatically Clear Data on Exit",
                subtitle = "Clears cookies and browsing history when the app closes, preserving Fireproof Sites.",
                checked = settings.autoClearOnExit,
                onCheckedChange = viewModel::setAutoClearOnExit,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FireproofSitesScreen(
    viewModel: BrowserViewModel,
    onBack: () -> Unit,
) {
    val sites by viewModel.fireproofSites.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var domainInput by remember { mutableStateOf("") }

    BackHandler { onBack() }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Fireproof Sites") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Navigate up")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Outlined.Add, contentDescription = "Add Fireproof Site")
            }
        },
    ) { innerPadding ->
        if (sites.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Outlined.Whatshot, contentDescription = null, tint = DaxCoral)
                Spacer(Modifier.height(12.dp))
                Text("No Fireproof Sites", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Fireproof sites keep their cookies and sign-ins when you tap the Fire button.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                items(sites, key = { it.domain }) { site ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Whatshot, contentDescription = null, tint = DaxCoral)
                        Spacer(Modifier.width(14.dp))
                        Text(site.domain, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.removeFireproofSite(site.domain) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Remove fireproof site")
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Fireproof Site") },
            text = {
                OutlinedTextField(
                    value = domainInput,
                    onValueChange = { domainInput = it },
                    label = { Text("Domain (e.g. github.com)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addFireproofSite(domainInput)
                        domainInput = ""
                        showAddDialog = false
                    },
                    enabled = domainInput.isNotBlank(),
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsScreen(
    viewModel: BrowserViewModel,
    onBack: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    BackHandler { onBack() }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Appearance") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Navigate up")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding),
        ) {
            SettingsSectionLabel("Theme")
            ThemeMode.entries.forEach { mode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setThemeMode(mode) }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = settings.themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = when (mode) {
                            ThemeMode.SYSTEM -> "System Default"
                            ThemeMode.LIGHT -> "Light"
                            ThemeMode.DARK -> "Dark"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

            SettingsSectionLabel("Address Bar Placement")
            OmnibarPosition.entries.forEach { pos ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setOmnibarPosition(pos) }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = settings.omnibarPosition == pos,
                        onClick = { viewModel.setOmnibarPosition(pos) },
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = if (pos == OmnibarPosition.TOP) "Top" else "Bottom",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionLabel(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
    )
}

@Composable
private fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String = "",
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .let { if (testTag.isNotEmpty()) it.testTag(testTag) else it },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
