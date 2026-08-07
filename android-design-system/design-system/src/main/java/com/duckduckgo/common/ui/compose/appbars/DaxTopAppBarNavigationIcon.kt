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

package com.duckduckgo.common.ui.compose.appbars

import androidx.compose.runtime.Immutable

/**
 * The navigation icon shown at the start of a [DaxTopAppBar] or [DaxSearchTopAppBar].
 *
 * The set is deliberately closed rather than a caller-supplied slot: the icon and its content description
 * come from the design system so that every bar announces navigation the same way to accessibility services.
 */
@Immutable
sealed interface DaxTopAppBarNavigationIcon {
    /** Invoked when the navigation icon is tapped. */
    val onClick: () -> Unit

    /** A back arrow, for returning to the previous screen in the stack. */
    data class Back(override val onClick: () -> Unit) : DaxTopAppBarNavigationIcon

    /** A cross, for dismissing a screen presented modally. */
    data class Close(override val onClick: () -> Unit) : DaxTopAppBarNavigationIcon
}
