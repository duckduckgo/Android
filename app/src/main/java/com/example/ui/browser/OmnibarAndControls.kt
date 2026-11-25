package com.example.ui.browser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NorthWest
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AutocompleteSuggestion
import com.example.model.BrowserTab
import com.example.model.SuggestionType
import com.example.ui.theme.DaxCoral
import com.example.ui.theme.DaxGreen50

@Composable
fun BrowserOmnibar(
    activeTab: BrowserTab,
    omnibarText: String,
    isOmnibarFocused: Boolean,
    tabCount: Int,
    isPrivacyProtected: Boolean,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    onTextChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onReloadOrStop: () -> Unit,
    onPrivacyShieldClick: () -> Unit,
    onFireClick: () -> Unit,
    onTabsClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Address / Search Field Pill
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!activeTab.isNewTabPage && !isOmnibarFocused) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .clickable(onClick = onPrivacyShieldClick)
                                    .testTag("privacy_shield_button"),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = if (isPrivacyProtected) Icons.Rounded.Shield else Icons.Outlined.Shield,
                                    contentDescription = "Privacy Dashboard",
                                    tint = if (isPrivacyProtected) DaxGreen50 else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                                if (activeTab.blockedTrackers.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(DaxGreen50),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = activeTab.blockedTrackers.size.coerceAtMost(99).toString(),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = if (activeTab.isSecure) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                                contentDescription = if (activeTab.isSecure) "HTTPS encrypted" else "Unencrypted connection",
                                tint = if (activeTab.isSecure) DaxGreen50 else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(19.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                        }

                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (omnibarText.isEmpty()) {
                                Text(
                                    text = "Search or type URL",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            BasicTextField(
                                value = omnibarText,
                                onValueChange = onTextChanged,
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Uri,
                                    imeAction = ImeAction.Go,
                                ),
                                keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester)
                                    .onFocusChanged { onFocusChanged(it.isFocused) }
                                    .testTag("omnibar_input"),
                            )
                        }

                        if (isOmnibarFocused && omnibarText.isNotEmpty()) {
                            IconButton(
                                onClick = { onTextChanged("") },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("omnibar_clear_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Clear",
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        } else if (!activeTab.isNewTabPage && !isOmnibarFocused) {
                            IconButton(
                                onClick = onReloadOrStop,
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("omnibar_refresh_button"),
                            ) {
                                Icon(
                                    imageVector = if (activeTab.isLoading) Icons.Outlined.Close else Icons.Outlined.Refresh,
                                    contentDescription = if (activeTab.isLoading) "Stop loading" else "Refresh",
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }

                if (!isOmnibarFocused) {
                    Spacer(Modifier.width(2.dp))
                    IconButton(
                        onClick = onFireClick,
                        modifier = Modifier
                            .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                            .testTag("fire_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.LocalFireDepartment,
                            contentDescription = "Clear tabs and data",
                            tint = DaxCoral,
                        )
                    }

                    IconButton(
                        onClick = onTabsClick,
                        modifier = Modifier
                            .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                            .testTag("tabs_button"),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = tabCount.coerceAtMost(99).toString(),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    IconButton(
                        onClick = onMenuClick,
                        modifier = Modifier
                            .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                            .testTag("browser_menu_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = "More options",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            if (activeTab.isLoading && !activeTab.isNewTabPage) {
                LinearProgressIndicator(
                    progress = { (activeTab.progress / 100f).coerceIn(0.05f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
    }
}

@Composable
fun AutocompleteOverlay(
    suggestions: List<AutocompleteSuggestion>,
    onSelect: (AutocompleteSuggestion) -> Unit,
    onFillQuery: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxWidth(),
    ) {
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(suggestions, key = { "${it.type}_${it.title}_${it.targetUrl}" }) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(item) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val icon = when (item.type) {
                        SuggestionType.DIRECT_URL -> Icons.Outlined.Public
                        SuggestionType.BOOKMARK -> Icons.Outlined.BookmarkBorder
                        SuggestionType.HISTORY -> Icons.Outlined.History
                        SuggestionType.SEARCH -> Icons.Outlined.Search
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (item.subtitle.isNotBlank()) {
                            Text(
                                text = item.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    IconButton(
                        onClick = { onFillQuery(item.title) },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.NorthWest,
                            contentDescription = "Insert into search bar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
fun FindInPageBar(
    visible: Boolean,
    query: String,
    activeMatch: Int,
    totalMatches: Int,
    onQueryChange: (String) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    AnimatedVisibility(visible = visible) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("find_in_page_input"),
                            decorationBox = { inner ->
                                if (query.isEmpty()) {
                                    Text(
                                        text = "Find in page",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                inner()
                            },
                        )
                        if (query.isNotEmpty()) {
                            Text(
                                text = "$activeMatch/$totalMatches",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                IconButton(onClick = onPrev) {
                    Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Previous match")
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Next match")
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close find in page")
                }
            }
        }
    }
}
