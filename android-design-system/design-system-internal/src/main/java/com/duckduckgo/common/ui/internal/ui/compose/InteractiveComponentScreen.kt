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

package com.duckduckgo.common.ui.internal.ui.compose

import androidx.compose.runtime.Composable
import com.duckduckgo.common.ui.internal.ui.compose.dialog.InteractiveDaxDialogComponent

/**
 * Registry of full-screen interactive component playgrounds hosted by [InteractiveComponentActivity].
 * To add a new playground: add an entry here and a branch in [Content].
 */
enum class InteractiveComponentScreen(val title: String) {
    DAX_DIALOG("Interactive Dax Dialog"),
}

/** Resolves a screen to its playground composable. The single registration point. */
@Composable
fun InteractiveComponentScreen.Content() = when (this) {
    InteractiveComponentScreen.DAX_DIALOG -> InteractiveDaxDialogComponent()
}
