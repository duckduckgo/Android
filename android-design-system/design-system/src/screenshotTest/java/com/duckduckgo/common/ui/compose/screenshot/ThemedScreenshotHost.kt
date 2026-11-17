/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.common.ui.compose.screenshot

import android.view.ContextThemeWrapper
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.viewinterop.AndroidView
import com.duckduckgo.mobile.android.R

/**
 * Draws [case] inside a view that carries the app theme.
 *
 * Popups and dialogs get their own ComposeView, whose LocalContext is the host view's context rather than the one PreviewBox
 * themes, so `?attr/daxColor*` drawables inside them only resolve when the host view itself carries the theme.
 * The factory is typed as [View] because the preview engine discovers tests by reflecting on every class in this source set on a
 * plain JVM, where a method signature naming ComposeView fails to load.
 */
@Composable
internal fun ThemedScreenshotHost(case: ScreenshotCase) {
    AndroidView<View>(
        factory = { context ->
            ComposeView(ContextThemeWrapper(context, R.style.Theme_DuckDuckGo_Light)).apply {
                setContent { ScreenshotFrame(case) }
            }
        },
    )
}
