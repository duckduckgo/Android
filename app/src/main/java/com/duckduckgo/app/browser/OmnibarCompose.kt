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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition
import com.duckduckgo.app.browser.R as BrowserR
import com.duckduckgo.mobile.android.R

@Composable
fun Omnibar(
    url: String,
    onSearch: () -> Unit,
    onSearchCancelled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isSearchFocused by remember { mutableStateOf(false) }

    Omnibar(
        webUrl = url,
        isSearchFocused = isSearchFocused,
        onSearch = {
            isSearchFocused = true
            onSearch()
        },
        onSearchCancelled = {
            isSearchFocused = false
            onSearchCancelled()
        },
        modifier = modifier,
    )
}

@Composable
private fun Omnibar(
    webUrl: String,
    isSearchFocused: Boolean,
    onSearch: () -> Unit,
    onSearchCancelled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        backgroundColor = Color.White,
        modifier = modifier,
        contentColor = Color.Black, // TODO theming
        contentPadding = PaddingValues(4.dp),
    ) {
        SearchField(
            value = webUrl,
            modifier = Modifier.height(56.dp).weight(1f),
            leadingIcon = {
                if (isSearchFocused) {
                    Icon(
                        painter = painterResource(BrowserR.drawable.ic_find_search_20_a05),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                } else {
                        PrivacyShield(modifier = Modifier.size(24.dp))
                }
            },
            onValueChange = {},
            onSearch = onSearch,
            onSearchCancelled = onSearchCancelled,
            hint = "Search DuckDuckGo",
        )

        IconButton(onClick = { /*TODO*/ }) {
            Icon(
                painter = painterResource(BrowserR.drawable.ic_fire),
                contentDescription = stringResource(BrowserR.string.fireMenu),
                tint = Color.Black,
            )
        }

        IconButton(onClick = { /*TODO*/ }) {
            Icon(
                painter = painterResource(BrowserR.drawable.ic_tabs),
                contentDescription = stringResource(BrowserR.string.fireMenu),
                tint = Color.Black,
            )
        }

        IconButton(onClick = { /*TODO*/ }) {
            Icon(
                painter = painterResource(R.drawable.ic_menu_vertical_24),
                contentDescription = stringResource(BrowserR.string.fireMenu),
                tint = Color.Black,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun OmnibarPreview() {
    Omnibar(
        "duckduckgo.com",
        onSearch = {},
        onSearchCancelled = {},
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun SearchField(
    value: String,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable () -> Unit,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSearchCancelled: () -> Unit,
    focusRequester: FocusRequester = remember { FocusRequester() },
    hint: String = "",
) {
    // We really need a Theme, this is just a hack to use existing attrs
    val daxColorBlue =
        colorResource(LocalContext.current.getColorFromAttrs(R.attr.daxColorAccentBlue).resourceId)
    val backgroundColor = colorResource(R.color.black6)
    var textValue by remember { mutableStateOf(TextFieldValue(value)) }
    var borderColor by remember { mutableStateOf(Color.Transparent) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var isTextFieldFocused by remember { mutableStateOf(false) }

    if (value != textValue.text) {
        textValue = TextFieldValue(value)
    }

    BasicTextField(
        modifier =
            modifier
                .focusRequester(focusRequester)
                .onFocusChanged {
                    if (it.isFocused) {
                        isTextFieldFocused = true
                        borderColor = daxColorBlue
                    } else {
                        isTextFieldFocused = false
                        borderColor = Color.Transparent
                        onSearchCancelled()
                    }
                }
                .padding(4.dp)
                .border(width = 2.dp, color = borderColor, shape = RoundedCornerShape(8.dp))
                .background(color = backgroundColor, shape = RoundedCornerShape(8.dp)),
        value = textValue,
        onValueChange = {
            textValue = it
            onValueChange(it.text)
        },
        textStyle = MaterialTheme.typography.body1,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions =
            KeyboardActions(
                onSearch = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    onSearch()
                }
            ),
        decorationBox = { innerTextField ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                leadingIcon()

                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            text = hint,
                            color = colorResource(R.color.black60),
                            style = MaterialTheme.typography.body1,
                        )
                    }

                    innerTextField()
                }

                if (isTextFieldFocused) {
                    IconButton(
                        onClick = {
                            textValue = TextFieldValue("")
                            focusRequester.requestFocus()
                        },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close_24),
                            contentDescription = "Clear",
                            tint = colorResource(R.color.black60),
                        )
                    }
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
fun PrivacyShield(modifier: Modifier = Modifier) {
    val composition by
        rememberLottieComposition(LottieCompositionSpec.RawRes(BrowserR.raw.protected_shield))
    LottieAnimation(
        composition = composition,
        modifier = modifier.offset(50.dp).scale(6f),
        clipToCompositionBounds = false,
        maintainOriginalImageBounds = true,
    )
}
