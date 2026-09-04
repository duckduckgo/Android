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

package com.duckduckgo.lint.ui

import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.Test

class DaxContextMenuItemContentDetectorTest {

    private val scopeStubs = kotlin(
        """
        package com.duckduckgo.common.ui.compose.contextmenu
        class DaxContextMenuItemTrailingScope { fun Icon(painter: Int = 0) {} }
        fun painterResource(id: Int): Int = id
        fun DaxDefaultContextMenuItem(
            text: String,
            trailingIcon: (DaxContextMenuItemTrailingScope.() -> Unit)? = null,
        ) {}
        fun DaxIconContextMenuItem(
            text: String,
            trailingIcon: (DaxContextMenuItemTrailingScope.() -> Unit)? = null,
        ) {}
        fun DaxInsetContextMenuItem(
            text: String,
            trailingIcon: (DaxContextMenuItemTrailingScope.() -> Unit)? = null,
        ) {}
        object DaxContextMenuScope {
            fun DaxDefaultItem(
                text: String,
                trailingIcon: (DaxContextMenuItemTrailingScope.() -> Unit)? = null,
            ) {}
            fun DaxIconItem(
                text: String,
                trailingIcon: (DaxContextMenuItemTrailingScope.() -> Unit)? = null,
            ) {}
            fun DaxInsetItem(
                text: String,
                trailingIcon: (DaxContextMenuItemTrailingScope.() -> Unit)? = null,
            ) {}
        }
        fun DaxContextMenu(content: DaxContextMenuScope.() -> Unit) {}
        """,
    ).indented()

    private fun caller(body: String) = kotlin(
        """
        package com.test
        import com.duckduckgo.common.ui.compose.contextmenu.*
        fun Text(text: String) {}
        fun Image(painter: Int) {}
        fun Column(content: () -> Unit) {}
        fun screen() { $body }
        """,
    ).indented()

    private fun run(body: String) = lint()
        .files(scopeStubs, caller(body))
        .issues(DaxContextMenuItemContentDetector.INVALID_DAX_CONTEXT_MENU_ITEM_CONTENT_USAGE)
        .run()

    @Test
    fun whenTrailingIconUsesScopeMemberThenNoWarning() {
        run("""DaxDefaultContextMenuItem(text = "x", trailingIcon = { Icon() })""").expectClean()
    }

    @Test
    fun whenTrailingIconUsesArbitraryComposableThenWarning() {
        run("""DaxDefaultContextMenuItem(text = "x", trailingIcon = { Text("x") })""").expectWarningCount(1)
    }

    @Test
    fun whenIconContextMenuItemTrailingIconArbitraryThenWarning() {
        run("""DaxIconContextMenuItem(text = "x", trailingIcon = { Image(1) })""").expectWarningCount(1)
    }

    @Test
    fun whenInsetContextMenuItemTrailingIconArbitraryThenWarning() {
        run("""DaxInsetContextMenuItem(text = "x", trailingIcon = { Text("x") })""").expectWarningCount(1)
    }

    @Test
    fun whenTrailingIconMixesAllowedAndArbitraryThenWarning() {
        run("""DaxDefaultContextMenuItem(text = "x", trailingIcon = { Icon(); Text("x") })""").expectWarningCount(1)
    }

    @Test
    fun whenScopeClassUnresolvedThenClean() {
        lint()
            .files(caller("""DaxDefaultContextMenuItem(text = "x", trailingIcon = { Text("x") })"""))
            .issues(DaxContextMenuItemContentDetector.INVALID_DAX_CONTEXT_MENU_ITEM_CONTENT_USAGE)
            .allowCompilationErrors()
            .run()
            .expectClean()
    }

    @Test
    fun whenScopeMemberTakesNonScopeValueArgumentThenNoWarning() {
        run("""DaxDefaultContextMenuItem(text = "x", trailingIcon = { Icon(painterResource(1)) })""").expectClean()
    }

    @Test
    fun whenTrailingIconWrapsScopeMemberInLayoutThenWarning() {
        run("""DaxDefaultContextMenuItem(text = "x", trailingIcon = { Column { Icon() } })""").expectWarningCount(1)
    }

    @Test
    fun whenScopedDefaultItemTrailingIconArbitraryThenWarning() {
        run("""DaxContextMenu { DaxDefaultItem(text = "x", trailingIcon = { Text("x") }) }""").expectWarningCount(1)
    }

    @Test
    fun whenScopedIconItemTrailingIconArbitraryThenWarning() {
        run("""DaxContextMenu { DaxIconItem(text = "x", trailingIcon = { Image(1) }) }""").expectWarningCount(1)
    }

    @Test
    fun whenScopedInsetItemTrailingIconArbitraryThenWarning() {
        run("""DaxContextMenu { DaxInsetItem(text = "x", trailingIcon = { Text("x") }) }""").expectWarningCount(1)
    }

    @Test
    fun whenScopedItemTrailingIconUsesScopeMemberThenNoWarning() {
        run("""DaxContextMenu { DaxIconItem(text = "x", trailingIcon = { Icon() }) }""").expectClean()
    }
}
