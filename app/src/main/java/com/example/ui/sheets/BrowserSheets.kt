package com.example.ui.sheets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.BrowserTab
import com.example.privacy.PrivacyEngine
import com.example.ui.theme.DaxCoral
import com.example.ui.theme.DaxGreen50

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserMenuSheet(
    activeTab: BrowserTab,
    isBookmarked: Boolean,
    isFavorite: Boolean,
    isFireproofed: Boolean,
    isPrivacyProtected: Boolean,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onRefresh: () -> Unit,
    onNewTab: () -> Unit,
    onNewFireTab: () -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShare: () -> Unit,
    onFindInPage: () -> Unit,
    onToggleDesktopSite: () -> Unit,
    onToggleFireproof: () -> Unit,
    onTogglePrivacyProtection: () -> Unit,
    onPrint: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenFireDialog: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            // Shortcut Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                MenuShortcutButton(
                    icon = Icons.AutoMirrored.Outlined.ArrowBack,
                    label = "Back",
                    enabled = activeTab.canGoBack,
                    onClick = onBack,
                )
                MenuShortcutButton(
                    icon = Icons.AutoMirrored.Outlined.ArrowForward,
                    label = "Forward",
                    enabled = activeTab.canGoForward,
                    onClick = onForward,
                )
                MenuShortcutButton(
                    icon = Icons.Outlined.Refresh,
                    label = "Refresh",
                    enabled = !activeTab.isNewTabPage,
                    onClick = onRefresh,
                )
                MenuShortcutButton(
                    icon = Icons.Outlined.AddBox,
                    label = "New Tab",
                    enabled = true,
                    onClick = onNewTab,
                )
                MenuShortcutButton(
                    icon = Icons.Outlined.Settings,
                    label = "Settings",
                    enabled = true,
                    onClick = onOpenSettings,
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            MenuRowItem(
                icon = Icons.Outlined.LocalFireDepartment,
                title = "New Fire Tab",
                tint = DaxCoral,
                onClick = onNewFireTab,
                testTag = "menu_new_fire_tab",
            )

            if (!activeTab.isNewTabPage) {
                MenuRowItem(
                    icon = if (isBookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                    title = if (isBookmarked) "Remove Bookmark" else "Add Bookmark",
                    onClick = onToggleBookmark,
                    testTag = "menu_bookmark",
                )
                MenuRowItem(
                    icon = if (isFavorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                    title = if (isFavorite) "Unpin from Favorites" else "Pin to Favorites",
                    onClick = onToggleFavorite,
                    testTag = "menu_favorite",
                )
                MenuRowItem(
                    icon = Icons.Outlined.Share,
                    title = "Share",
                    onClick = onShare,
                    testTag = "menu_share",
                )
                MenuRowItem(
                    icon = Icons.Outlined.FindInPage,
                    title = "Find in Page",
                    onClick = onFindInPage,
                    testTag = "menu_find_in_page",
                )
                MenuRowItem(
                    icon = Icons.Outlined.Computer,
                    title = if (activeTab.isDesktopMode) "Mobile Site" else "Desktop Site",
                    onClick = onToggleDesktopSite,
                    testTag = "menu_desktop_site",
                )
                MenuRowItem(
                    icon = Icons.Outlined.Whatshot,
                    title = if (isFireproofed) "Remove Fireproofing" else "Fireproof This Site",
                    tint = if (isFireproofed) DaxCoral else MaterialTheme.colorScheme.onSurface,
                    onClick = onToggleFireproof,
                    testTag = "menu_fireproof_site",
                )
                MenuRowItem(
                    icon = Icons.Outlined.Shield,
                    title = if (isPrivacyProtected) "Disable Privacy Protection" else "Enable Privacy Protection",
                    onClick = onTogglePrivacyProtection,
                    testTag = "menu_toggle_privacy",
                )
                MenuRowItem(
                    icon = Icons.Outlined.Print,
                    title = "Print Page",
                    onClick = onPrint,
                    testTag = "menu_print",
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }

            MenuRowItem(
                icon = Icons.Outlined.BookmarkBorder,
                title = "Bookmarks",
                onClick = onOpenBookmarks,
                testTag = "menu_bookmarks",
            )
            MenuRowItem(
                icon = Icons.Outlined.History,
                title = "History",
                onClick = onOpenHistory,
                testTag = "menu_history",
            )
            MenuRowItem(
                icon = Icons.Outlined.Download,
                title = "Downloads",
                onClick = onOpenDownloads,
                testTag = "menu_downloads",
            )
            MenuRowItem(
                icon = Icons.Outlined.LocalFireDepartment,
                title = "Delete Tabs and Data",
                tint = MaterialTheme.colorScheme.error,
                onClick = onOpenFireDialog,
                testTag = "menu_fire_button",
            )
        }
    }
}

@Composable
private fun MenuShortcutButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        )
    }
}

@Composable
private fun MenuRowItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface,
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
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = tint,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyDashboardSheet(
    activeTab: BrowserTab,
    isProtectionEnabled: Boolean,
    onDismiss: () -> Unit,
    onToggleProtection: () -> Unit,
    onReportBrokenSite: () -> Unit,
) {
    val host = PrivacyEngine.extractHost(activeTab.url) ?: activeTab.url
    val groupedTrackers = activeTab.blockedTrackers.groupBy { it.company }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (isProtectionEnabled) DaxGreen50.copy(alpha = 0.14f) else MaterialTheme.colorScheme.errorContainer,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isProtectionEnabled) Icons.Rounded.Shield else Icons.Outlined.Shield,
                        contentDescription = null,
                        tint = if (isProtectionEnabled) DaxGreen50 else MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = host.ifBlank { "New Tab" },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (isProtectionEnabled) "Privacy Protection is ON" else "Protections Disabled for Site",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isProtectionEnabled) DaxGreen50 else MaterialTheme.colorScheme.error,
                    )
                }
                Switch(
                    checked = isProtectionEnabled,
                    onCheckedChange = { onToggleProtection() },
                    modifier = Modifier.testTag("privacy_dashboard_site_switch"),
                )
            }

            Spacer(Modifier.height(16.dp))

            // Connection Status
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (activeTab.isSecure) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                        contentDescription = null,
                        tint = if (activeTab.isSecure) DaxGreen50 else MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (activeTab.isSecure) "Connection is Encrypted" else "Connection is Not Encrypted",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = if (activeTab.httpsUpgraded) {
                                "Automatically upgraded from HTTP to HTTPS"
                            } else if (activeTab.isSecure) {
                                "Verified HTTPS connection"
                            } else {
                                "Data sent over HTTP may be visible on your network"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Trackers Blocked (${activeTab.blockedTrackers.size})",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))

            if (groupedTrackers.isEmpty()) {
                Text(
                    text = if (isProtectionEnabled) {
                        "No hidden third-party trackers were detected on this page."
                    } else {
                        "Privacy Protection is disabled for this site."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                groupedTrackers.forEach { (company, requests) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(company, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                            Text(
                                text = requests.first().category.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = DaxGreen50.copy(alpha = 0.14f),
                        ) {
                            Text(
                                text = "${requests.size} blocked",
                                style = MaterialTheme.typography.labelSmall,
                                color = DaxGreen50,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = onReportBrokenSite,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.ReportProblem, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Site Not Working? Report Broken Site")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FireDialogSheet(
    onDismiss: () -> Unit,
    onDeleteThisTab: () -> Unit,
    onDeleteAll: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(DaxCoral.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.LocalFireDepartment,
                    contentDescription = null,
                    tint = DaxCoral,
                    modifier = Modifier.size(30.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Delete tabs and site data?",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Open tabs will close and browsing data will be cleared, except for Fireproofed sites.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))

            Button(
                onClick = onDeleteAll,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("fire_delete_all_button"),
            ) {
                Text("Delete All")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onDeleteThisTab,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("fire_delete_tab_button"),
            ) {
                Text("Delete This Tab")
            }
            Spacer(Modifier.height(4.dp))
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Cancel")
            }
        }
    }
}

@Composable
fun FireAnimationOverlay(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(300)),
    ) {
        val transition = rememberInfiniteTransition(label = "fire_pulse")
        val scale by transition.animateFloat(
            initialValue = 0.9f,
            targetValue = 1.25f,
            animationSpec = infiniteRepeatable(
                animation = tween(350, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "flame_scale",
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF1A0D0A), Color(0xFF8C2D14), DaxCoral),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Outlined.LocalFireDepartment,
                    contentDescription = "Clearing browsing data",
                    tint = Color.White,
                    modifier = Modifier
                        .size(96.dp)
                        .scale(scale),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Clearing tabs & site data...",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                )
            }
        }
    }
}
