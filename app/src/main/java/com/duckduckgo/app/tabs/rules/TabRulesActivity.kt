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

package com.duckduckgo.app.tabs.rules

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.Icons.AutoMirrored
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.SwipeToDismissBoxValue.EndToStart
import androidx.compose.material3.SwipeToDismissBoxValue.Settled
import androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.autocomplete.api.formatIfUrl
import com.duckduckgo.app.browser.R
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.compose.theme.Black12
import com.duckduckgo.common.ui.compose.theme.Black30
import com.duckduckgo.mobile.android.R as CommonR
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.utils.toStringDropScheme
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(TabRulesScreen::class)
class TabRulesActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var tabRulesViewModel: TabRulesViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DuckDuckGoTheme {
                TabRulesScreen(
                    viewModel = tabRulesViewModel,
                    onBackPressed = { finish() },
                    onPickUrlFromList = {
                        globalActivityStarter.start(context = this, params = TabRulesVisitedSitesScreen)
                    },
                    onUrlEntered = {},
                    onToggleRule = { tabRule ->
                        tabRulesViewModel.onTabRuleEnabledChanged(tabRule.id, !tabRule.isEnabled)
                    },
                    onDeleteRule = { tabRule ->
                        tabRulesViewModel.onTabRuleDeleteClicked(tabRule.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun TabRulesScreen(
    onBackPressed: () -> Unit,
    onPickUrlFromList: () -> Unit,
    onUrlEntered: (String) -> Unit,
    onToggleRule: (TabRule) -> Unit,
    onDeleteRule: (TabRule) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TabRulesViewModel = viewModel(),
) {
    val viewState by viewModel.state.collectAsStateWithLifecycle()

    TabRulesScreen(
        tabRules = viewState.tabRules,
        manualUrl = viewState.manualUrl,
        onBackPressed = onBackPressed,
        onPickUrlFromList = onPickUrlFromList,
        onUrlEntered = onUrlEntered,
        onToggleRule = onToggleRule,
        onDeleteRule = onDeleteRule,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun TabRulesScreen(
    tabRules: List<TabRule>,
    manualUrl: String?,
    onBackPressed: () -> Unit,
    onPickUrlFromList: () -> Unit,
    onUrlEntered: (String) -> Unit,
    onToggleRule: (TabRule) -> Unit,
    onDeleteRule: (TabRule) -> Unit,
    modifier: Modifier = Modifier
) {
    var showBin by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        showBin = true
    }

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
                        text = "Tab Rules",
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
                contentPadding = PaddingValues(vertical = 16.dp),
                modifier = Modifier.weight(1f),
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    ) {
                        Text(
                            text = "Select websites that should only use one tab instead of opening multiple tabs",
                            style = DuckDuckGoTheme.typography.body1,
                            color = DuckDuckGoTheme.colors.primaryText,
                        )

                        Spacer(Modifier.height(16.dp))

                        Button(
                            onClick = onPickUrlFromList,
                            colors = ButtonColors(
                                containerColor = DuckDuckGoTheme.colors.buttonColors.primaryContainer,
                                contentColor = DuckDuckGoTheme.colors.buttonColors.primaryText,
                                disabledContainerColor = DuckDuckGoTheme.colors.buttonColors.primaryContainerDisabled,
                                disabledContentColor = DuckDuckGoTheme.colors.buttonColors.primaryTextDisabled,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            shape = DuckDuckGoTheme.shapes.small,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                            Text(text = "Choose", style = DuckDuckGoTheme.typography.button)
                        }

                        Spacer(Modifier.height(16.dp))

                        Text(
                            text = "Or enter a URL manually",
                            style = DuckDuckGoTheme.typography.body1,
                            color = DuckDuckGoTheme.colors.primaryText,
                        )

                        Spacer(Modifier.height(16.dp))

                        OutlinedTextField(
                            value = manualUrl ?: "",
                            onValueChange = onUrlEntered,
                            label = { Text("Enter website URL") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = DuckDuckGoTheme.shapes.small,
                            colors = TextFieldColors(
                                focusedTextColor = DuckDuckGoTheme.colors.primaryText,
                                unfocusedTextColor = DuckDuckGoTheme.colors.primaryText,
                                disabledTextColor = DuckDuckGoTheme.colors.textDisabled,
                                errorTextColor = DuckDuckGoTheme.colors.destructive,
                                focusedContainerColor = DuckDuckGoTheme.colors.background,
                                unfocusedContainerColor = DuckDuckGoTheme.colors.background,
                                disabledContainerColor = DuckDuckGoTheme.colors.background,
                                errorContainerColor = DuckDuckGoTheme.colors.background,
                                cursorColor = DuckDuckGoTheme.colors.accentBlue,
                                errorCursorColor = DuckDuckGoTheme.colors.destructive,
                                textSelectionColors = TextSelectionColors(
                                    handleColor = DuckDuckGoTheme.colors.accentBlue,
                                    backgroundColor = DuckDuckGoTheme.colors.accentBlue.copy(alpha = 0.24f),
                                ),
                                focusedIndicatorColor = DuckDuckGoTheme.colors.accentBlue,
                                unfocusedIndicatorColor = DuckDuckGoTheme.colors.textInputColors.enabledOutline,
                                disabledIndicatorColor = DuckDuckGoTheme.colors.primaryText.copy(alpha = 0.84f),
                                errorIndicatorColor = DuckDuckGoTheme.colors.destructive,
                                focusedLeadingIconColor = DuckDuckGoTheme.colors.primaryIcon,
                                unfocusedLeadingIconColor = DuckDuckGoTheme.colors.primaryIcon,
                                disabledLeadingIconColor = DuckDuckGoTheme.colors.iconDisabled,
                                errorLeadingIconColor = DuckDuckGoTheme.colors.primaryIcon,
                                focusedTrailingIconColor = DuckDuckGoTheme.colors.primaryIcon,
                                unfocusedTrailingIconColor = DuckDuckGoTheme.colors.primaryIcon,
                                disabledTrailingIconColor = DuckDuckGoTheme.colors.iconDisabled,
                                errorTrailingIconColor = DuckDuckGoTheme.colors.primaryIcon,
                                focusedLabelColor = DuckDuckGoTheme.colors.accentBlue,
                                unfocusedLabelColor = DuckDuckGoTheme.colors.secondaryText,
                                disabledLabelColor = DuckDuckGoTheme.colors.textDisabled,
                                errorLabelColor = DuckDuckGoTheme.colors.destructive,
                                focusedPlaceholderColor = DuckDuckGoTheme.colors.secondaryText,
                                unfocusedPlaceholderColor = DuckDuckGoTheme.colors.secondaryText,
                                disabledPlaceholderColor = DuckDuckGoTheme.colors.textDisabled,
                                errorPlaceholderColor = DuckDuckGoTheme.colors.destructive,
                                focusedSupportingTextColor = DuckDuckGoTheme.colors.secondaryText,
                                unfocusedSupportingTextColor = DuckDuckGoTheme.colors.secondaryText,
                                disabledSupportingTextColor = DuckDuckGoTheme.colors.textDisabled,
                                errorSupportingTextColor = DuckDuckGoTheme.colors.destructive,
                                focusedPrefixColor = DuckDuckGoTheme.colors.accentBlue,
                                unfocusedPrefixColor = DuckDuckGoTheme.colors.secondaryText,
                                disabledPrefixColor = DuckDuckGoTheme.colors.textDisabled,
                                errorPrefixColor = DuckDuckGoTheme.colors.destructive,
                                focusedSuffixColor = DuckDuckGoTheme.colors.accentBlue,
                                unfocusedSuffixColor = DuckDuckGoTheme.colors.secondaryText,
                                disabledSuffixColor = DuckDuckGoTheme.colors.textDisabled,
                                errorSuffixColor = DuckDuckGoTheme.colors.destructive,
                            ),
                        )
                    }
                }

                item {
                    Spacer(Modifier.height(16.dp))
                }

                stickyHeader {
                    Column(
                        modifier = Modifier
                            .background(color = DuckDuckGoTheme.colors.background),
                    ) {
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = DuckDuckGoTheme.colors.dividerColors.colorLines,
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                text = "Saved Rules",
                                style = DuckDuckGoTheme.typography.h4,
                                color = DuckDuckGoTheme.colors.secondaryText,
                            )
                        }
                    }
                }

                if (tabRules.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "No rules added yet",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    itemsIndexed(items = tabRules, key = { _, item: TabRule -> item.id }) { index, rule ->
                        val swipeToDismissBoxState = rememberSwipeToDismissBoxState(
                            positionalThreshold = {
                                it * .5f
                            },
                            confirmValueChange = {
                                when (it) {
                                    StartToEnd -> return@rememberSwipeToDismissBoxState false
                                    EndToStart -> {
                                        onDeleteRule(rule)
                                    }

                                    Settled -> return@rememberSwipeToDismissBoxState false
                                }
                                return@rememberSwipeToDismissBoxState true
                            },
                        )

                        SwipeToDismissBox(
                            state = swipeToDismissBoxState,
                            backgroundContent = {
                                Box(
                                    contentAlignment = Alignment.CenterEnd,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(DuckDuckGoTheme.colors.buttonColors.destructiveContainer, DuckDuckGoTheme.shapes.small)
                                        .padding(16.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(id = CommonR.drawable.ic_trash_24),
                                        contentDescription = "Delete tab rule for ${rule.url}",
                                        modifier = Modifier.size(24.dp),
                                        tint = DuckDuckGoTheme.colors.buttonColors.primaryText,
                                    )
                                }
                            },
                            enableDismissFromStartToEnd = false,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .animateItem(),
                        ) {
                            val scope = rememberCoroutineScope()

/*
                            val offsetAnimate by animateIntOffsetAsState(
                                targetValue = if (index == 0 && showBin) {
                                    IntOffset(
                                        with(LocalDensity.current) { -56.dp.roundToPx() },
                                        0,
                                    )
                                } else {
                                    IntOffset.Zero
                                },
                                animationSpec = tween(300),
                                finishedListener = { _ ->
                                    scope.launch {
                                        delay(200)
                                        showBin = false
                                    }
                                }
                            )
*/

                            TabRuleItem(
                                rule = rule,
                                onToggle = onToggleRule,
                                modifier = Modifier,
                            )
                        }

                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun TabRuleItem(
    rule: TabRule,
    onToggle: (rule: TabRule) -> Unit,
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
        Spacer(Modifier.width(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Image(
                painter = rule.faviconBitmap?.let { remember { BitmapPainter(it.asImageBitmap()) } }
                    ?: painterResource(id = R.drawable.ic_globe_20),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rule.title ?: rule.displayUrl,
                    style = DuckDuckGoTheme.typography.body1,
                )

                Spacer(Modifier.height(4.dp))

                if (rule.title != null) {
                    Text(
                        text = rule.displayUrl,
                        style = DuckDuckGoTheme.typography.body2,
                    )
                }
            }

            Switch(
                colors = SwitchColors(
                    checkedThumbColor = DuckDuckGoTheme.colors.switchColors.thumbOn,
                    checkedTrackColor = DuckDuckGoTheme.colors.switchColors.trackOn,
                    checkedBorderColor = DuckDuckGoTheme.colors.switchColors.trackOn,
                    checkedIconColor = DuckDuckGoTheme.colors.switchColors.thumbOn,
                    uncheckedThumbColor = DuckDuckGoTheme.colors.switchColors.thumbOff,
                    uncheckedTrackColor = DuckDuckGoTheme.colors.switchColors.trackOff,
                    uncheckedBorderColor = DuckDuckGoTheme.colors.switchColors.trackOff,
                    uncheckedIconColor = DuckDuckGoTheme.colors.switchColors.thumbOff,
                    disabledCheckedThumbColor = DuckDuckGoTheme.colors.switchColors.thumbOn,
                    disabledCheckedTrackColor = DuckDuckGoTheme.colors.switchColors.trackOn,
                    disabledCheckedBorderColor = DuckDuckGoTheme.colors.switchColors.trackOn,
                    disabledCheckedIconColor = DuckDuckGoTheme.colors.switchColors.thumbOn,
                    disabledUncheckedThumbColor = DuckDuckGoTheme.colors.switchColors.thumbOff,
                    disabledUncheckedTrackColor = DuckDuckGoTheme.colors.switchColors.trackOff,
                    disabledUncheckedBorderColor = DuckDuckGoTheme.colors.switchColors.trackOff,
                    disabledUncheckedIconColor = DuckDuckGoTheme.colors.switchColors.thumbOff,
                ),
                checked = rule.isEnabled,
                onCheckedChange = { onToggle(rule) },
            )
        }
    }
}

data class TabRule(
    val id: Long,
    val url: String,
    val title: String?,
    val faviconBitmap: Bitmap?,
    val isEnabled: Boolean,
    val createdAt: LocalDateTime,
) {

    val displayUrl = url.formatIfUrl()
}
