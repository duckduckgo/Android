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

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.android.tools.screenshot.PreviewTest

/** Renders every [designSystemScreenshots] case with Compose Preview Screenshot Testing. */
@SuppressLint("ComposePreviewPublic")
@PreviewTest
@Preview(showBackground = true, device = "spec:width=360dp,height=640dp,dpi=240")
@Composable
fun DesignSystemScreenshotTest(@PreviewParameter(DesignSystemScreenshots::class) case: ScreenshotCase) {
    ThemedScreenshotHost(case)
}

class DesignSystemScreenshots : PreviewParameterProvider<ScreenshotCase> {
    override val values: Sequence<ScreenshotCase> = designSystemScreenshots.asSequence()
    override fun getDisplayName(index: Int): String = designSystemScreenshots[index].name
}
