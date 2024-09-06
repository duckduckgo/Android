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

package com.duckduckgo.app.browser

import android.content.Context
import android.util.TypedValue
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring.StiffnessMediumLow
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation.weight
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.duckduckgo.app.browser.R as BrowserR
import com.duckduckgo.mobile.android.R
import kotlin.math.roundToInt

@Composable
fun Omnibar(
    url: String,
    tabCount: Int,
    onSearch: (query: String) -> Unit,
    onSearchCancelled: () -> Unit,
    onFireMenuClick: () -> Unit,
    onTabsMenuClick: () -> Unit,
    onBrowserMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isSearchFocused by remember { mutableStateOf(false) }

    Omnibar(
        webUrl = url,
        tabCount = tabCount,
        isSearchFocused = isSearchFocused,
        onSearch = {
            isSearchFocused = false
            onSearch(it)
        },
        onSearchFocused = { isSearchFocused = true },
        onSearchCancelled = {
            isSearchFocused = false
            onSearchCancelled()
        },
        onFireMenuClick = onFireMenuClick,
        onTabsMenuClick = onTabsMenuClick,
        onBrowserMenuClick = onBrowserMenuClick,
        modifier = modifier,
    )
}

@Composable
private fun Omnibar(
    webUrl: String,
    tabCount: Int,
    isSearchFocused: Boolean,
    onSearch: (query: String) -> Unit,
    onSearchFocused: () -> Unit,
    onSearchCancelled: () -> Unit,
    onFireMenuClick: () -> Unit,
    onTabsMenuClick: () -> Unit,
    onBrowserMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val composition by
        rememberLottieComposition(LottieCompositionSpec.RawRes(BrowserR.raw.protected_shield))
    val progress by animateLottieCompositionAsState(composition)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier =
        modifier
            .background(Color.White) // TODO theming
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = 8.dp),
    ) {
        SearchField(
            url = webUrl,
            modifier = Modifier.weight(1f),
            leadingIcon = {
                if (isSearchFocused) {
                    Icon(
                        painter = painterResource(BrowserR.drawable.ic_find_search_20_a05),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                } else {
                    PrivacyShield(
                        progress = { progress },
                        composition = composition,
                        modifier = Modifier.size(24.dp),
                    )
                }
            },
            onValueChange = {},
            onSearch = onSearch,
            onSearchFocused = onSearchFocused,
            onSearchCancelled = onSearchCancelled,
            hint = stringResource(BrowserR.string.omnibarInputHint),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier =
            Modifier
                .animateContentSize()
                .then(if (isSearchFocused) Modifier.width(0.dp) else Modifier),
        ) {
            OmnibarIcon(
                painter = painterResource(BrowserR.drawable.ic_fire),
                contentDescription = stringResource(BrowserR.string.fireMenu),
                tint = Color.Black,
                onClick = onFireMenuClick,
            )

            TabMenuIcon(tabCount = tabCount, tint = Color.Black, onClick = onTabsMenuClick)

            OmnibarIcon(
                painter = painterResource(R.drawable.ic_menu_vertical_24),
                contentDescription = stringResource(BrowserR.string.browserPopupMenu),
                tint = Color.Black,
                onClick = onBrowserMenuClick,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun OmnibarPreview() {
    Omnibar(
        url = "duckduckgo.com",
        tabCount = 66,
        onSearch = {},
        onSearchCancelled = {},
        onFireMenuClick = {},
        onTabsMenuClick = {},
        onBrowserMenuClick = {},
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun SearchField(
    url: String,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable () -> Unit,
    onValueChange: (String) -> Unit,
    onSearch: (query: String) -> Unit,
    onSearchFocused: () -> Unit,
    onSearchCancelled: () -> Unit,
    hint: String = "",
) {
    var textValue by remember { mutableStateOf(TextFieldValue(text = url)) }

    LaunchedEffect(url) { textValue = TextFieldValue(text = url) }

    // We really need a Theme, this is just a hack to use existing attrs
    val daxColorBlue =
        colorResource(LocalContext.current.getColorFromAttrs(R.attr.daxColorAccentBlue).resourceId)
    val backgroundColor = colorResource(R.color.black6)
    var borderColor by remember { mutableStateOf(Color.Transparent) }
    val keyboardController = LocalSoftwareKeyboardController.current
    var isTextFieldFocused by remember { mutableStateOf(false) }
    var searchCleared by remember { mutableStateOf(false) }
    val isHintVisible = textValue.text == hint
    var isSubmittingQuery by remember { mutableStateOf(false) }
    // TODO if we HAD to we could model these states with an enum, prefarably we wouldn't have to if
    //   we use MaterialComponents

    var keepWholeSelection by remember { mutableStateOf(false) }
    if (keepWholeSelection) {
        // in case onValueChange was not called immediately after onFocusChanged
        // the selection will be transferred correctly, so we don't need to redefine it anymore
        SideEffect { keepWholeSelection = false }
    }

    BasicTextField(
        modifier =
        modifier
            .defaultMinSize(minHeight = 40.dp)
            .background(backgroundColor, RoundedCornerShape(8.dp))
            // TODO it would be nice to animate this in
            .border(2.dp, borderColor, RoundedCornerShape(8.dp))
            .onFocusChanged {
                if (it.isFocused) {
                    isSubmittingQuery = false
                    isTextFieldFocused = true
                    borderColor = daxColorBlue
                    textValue = textValue.copy(selection = TextRange(0, textValue.text.length))
                    keepWholeSelection = true
                    onSearchFocused()
                } else {
                    isTextFieldFocused = false
                    borderColor = Color.Transparent
                    // TODO searchCancelled should probably eventually reset the url in a
                    //   ViewModel, rather than doing it here
                    if (!isSubmittingQuery || isHintVisible) {
                        searchCleared = false
                        textValue = TextFieldValue(url)
                        onSearchCancelled()
                    }
                }
            },
        value =
            if (searchCleared) {
                TextFieldValue(hint)
            } else {
                textValue
            },
        onValueChange = { value ->
            searchCleared = false
            // clear the hint as the user types
            textValue =
                if (isHintVisible) {
                    value.copy(text = value.text[0].toString())
                } else if (value.text.isBlank()) {
                    TextFieldValue(hint)
                } else {
                    // Hack for manual selection of the whole text. Fun times.
                    if (keepWholeSelection) {
                        keepWholeSelection = false
                        textValue.copy(selection = TextRange(0, textValue.text.length))
                    } else {
                        value
                    }
                }
            onValueChange(textValue.text)
        },
        textStyle =
            MaterialTheme.typography.bodyMedium.copy(
                color = if (isHintVisible) colorResource(R.color.black60) else Color.Black
            ),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions =
            KeyboardActions(
                onSearch = {
                    keyboardController?.hide()
                    isSubmittingQuery = true
                    onSearch(textValue.text)
                }
            ),
        decorationBox = { innerTextField ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                leadingIcon()

                Box(Modifier.weight(1f)) { innerTextField() }

                if (isTextFieldFocused) {
                    OmnibarIcon(
                        painter = painterResource(R.drawable.ic_close_24),
                        contentDescription = "Clear",
                        tint = colorResource(R.color.black60),
                        onClick = {
                            searchCleared = true
                            textValue = TextFieldValue(hint)
                        },
                    )
                }
            }
        },
    )
}

// hack, we'd need to migrate theme first
@Composable
fun getColor(color: Int): Color {
    return colorResource(LocalContext.current.getColorFromAttrs(color).resourceId)
}

fun Context.getColorFromAttrs(attr: Int): TypedValue {
    return TypedValue().apply { theme.resolveAttribute(attr, this, true) }
}

@Composable
private fun TabMenuIcon(
    tabCount: Int,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        OmnibarIcon(
            painter = painterResource(BrowserR.drawable.ic_tabs),
            contentDescription = stringResource(BrowserR.string.tabsMenuItem),
            tint = tint,
            onClick = onClick,
        )

        var oldTabCount by remember { mutableIntStateOf(tabCount) }

        SideEffect { oldTabCount = tabCount }

        val countString = tabCount.toString()
        val oldCountString = oldTabCount.toString()

        Row {
            countString.indices.forEach { index ->
                val targetChar = getTargetChar(index, oldCountString, countString)

                AnimatedContent(
                    targetState = targetChar,
                    transitionSpec = {
                        if (targetState > initialState) {
                                increaseTitleCountAnimation
                            } else {
                                decreaseTitleCountAnimation
                            }
                            .using(SizeTransform(clip = false))
                    },
                    label = "AnimatedTitleCount",
                ) { char ->
                    // TODO how would we account for infinity?
                    Text(
                        text = char.toString(),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }
    }
}

private fun getTargetChar(index: Int, oldString: String, newString: String): Char {
    val oldChar = oldString.getOrNull(index)
    val newChar = newString[index]

    return if (oldChar == newChar) {
        oldString[index]
    } else {
        newString[index]
    }
}

private const val SLIDE_HEIGHT_FACTOR = 0.75f

private val fadeAnimationSpec = spring(stiffness = StiffnessMediumLow, visibilityThreshold = 0.5f)

private val increaseTitleCountAnimation =
    (slideInVertically { height -> (height * SLIDE_HEIGHT_FACTOR).roundToInt() } +
            fadeIn(fadeAnimationSpec))
        .togetherWith(
            slideOutVertically { height -> (-height * SLIDE_HEIGHT_FACTOR).roundToInt() } +
                fadeOut(fadeAnimationSpec)
        )

private val decreaseTitleCountAnimation =
    (slideInVertically { height -> (-height * SLIDE_HEIGHT_FACTOR).roundToInt() } +
            fadeIn(fadeAnimationSpec))
        .togetherWith(
            slideOutVertically { height -> (height * SLIDE_HEIGHT_FACTOR).roundToInt() } +
                fadeOut(fadeAnimationSpec)
        )

@Composable
private fun OmnibarIcon(
    painter: Painter,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Icon(
        painter = painter,
        contentDescription = contentDescription,
        tint = tint,
        modifier =
            modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(bounded = false),
                onClick = onClick,
            ),
    )
}

@Composable
fun PrivacyShield(
    progress: () -> Float,
    composition: LottieComposition?,
    modifier: Modifier = Modifier,
) {
    LottieAnimation(
        progress = progress,
        composition = composition,
        modifier = modifier.offset(50.dp).scale(6f),
        clipToCompositionBounds = false,
        maintainOriginalImageBounds = true,
    )
}
