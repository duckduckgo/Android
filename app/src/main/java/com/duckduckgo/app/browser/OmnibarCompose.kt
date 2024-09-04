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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.DuckDuckGoTheme
import com.duckduckgo.mobile.android.R
import com.duckduckgo.app.browser.R as BrowserR

@Composable
fun Omnibar(modifier: Modifier = Modifier) {
    TopAppBar(
        backgroundColor = Color.White,
        modifier = modifier,
        contentPadding = PaddingValues(4.dp)
    ) {
        SearchField(
            value = "Test",
            modifier = Modifier
                .height(56.dp)
                .fillMaxWidth(),
            leadingIcon = {
                Icon(
                    painter = painterResource(BrowserR.drawable.ic_find_search_20_a05),
                    contentDescription = "Search",
                    modifier = Modifier.size(24.dp)
                )
            },
            onValueChange = {},
            onSearch = {},
            hint = "Search DuckDuckGo"
        )
    }
}

@PreviewLightDark
@Composable
private fun OmnibarPreview() {
    Omnibar(modifier = Modifier.fillMaxWidth())
}

@Composable
fun SearchField(
    value: String,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable () -> Unit,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    onIconClick: (() -> Unit)? = null,
    focusRequester: FocusRequester = remember { FocusRequester() },
    hint: String = "",
) {
    var textValue by remember { mutableStateOf(TextFieldValue(value)) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    if (value != textValue.text) {
        textValue = TextFieldValue(value)
    }

    BasicTextField(
        modifier = modifier
            .focusRequester(focusRequester)
            .padding(4.dp)
            .background(
                color = colorResource(R.color.black6),
                shape = RoundedCornerShape(8.dp)
            ),
        value = textValue,
        onValueChange = {
            textValue = it
            onValueChange(it.text)
        },
        textStyle = MaterialTheme.typography.body1,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = {
                keyboardController?.hide()
                focusManager.clearFocus()
                onSearch()
            }
        ),
        decorationBox = { innerTextField ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                if (onIconClick != null) {
                    IconButton(
                        onClick = onIconClick
                    ) {
                        leadingIcon()
                    }
                } else {
                    leadingIcon()
                }

                Box(Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = hint,
                            color = colorResource(R.color.black60),
                            style = MaterialTheme.typography.body1
                        )
                    }

                    innerTextField()
                }
                if (textValue.text.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            textValue = TextFieldValue("")
                            onValueChange("")
                            focusRequester.requestFocus()
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear",
                            tint = colorResource(R.color.black60)
                        )
                    }
                }
            }
        }
    )
}
