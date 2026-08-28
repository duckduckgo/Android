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

class DaxContextMenuContentDetectorTest {

    private val scopeStubs = kotlin(
        """
        package com.duckduckgo.common.ui.compose.contextmenu
        class DaxContextMenuScope {
            fun DaxDefaultItem(text: String) {}
            fun DaxIconItem(text: String) {}
            fun DaxInsetItem(text: String) {}
        }
        fun painterResource(id: Int): Int = id
        fun DaxContextMenu(
            expanded: Boolean,
            onDismissRequest: () -> Unit,
            content: (DaxContextMenuScope.() -> Unit)? = null,
        ) {}
        fun DaxContextMenuIconButton(
            iconPainter: Int,
            content: (DaxContextMenuScope.() -> Unit)? = null,
        ) {}
        """,
    ).indented()

    private fun caller(body: String) = kotlin(
        """
        package com.test
        import com.duckduckgo.common.ui.compose.contextmenu.*
        fun Text(text: String) {}
        fun Button(text: String) {}
        fun Column(content: () -> Unit) {}
        fun screen() { $body }
        """,
    ).indented()

    private fun run(body: String) = lint()
        .files(scopeStubs, caller(body))
        .issues(DaxContextMenuContentDetector.INVALID_DAX_CONTEXT_MENU_CONTENT_USAGE)
        .run()

    @Test
    fun whenContextMenuContentUsesScopeMemberThenNoWarning() {
        run("""DaxContextMenu(expanded = true, onDismissRequest = {}, content = { DaxDefaultItem("x") })""").expectClean()
    }

    @Test
    fun whenIconButtonContentUsesScopeMemberThenNoWarning() {
        run("""DaxContextMenuIconButton(iconPainter = 0, content = { DaxIconItem("x") })""").expectClean()
    }

    @Test
    fun whenContextMenuContentUsesArbitraryComposableThenWarning() {
        run("""DaxContextMenu(expanded = true, onDismissRequest = {}, content = { Text("x") })""").expectWarningCount(1)
    }

    @Test
    fun whenIconButtonContentUsesArbitraryComposableThenWarning() {
        run("""DaxContextMenuIconButton(iconPainter = 0, content = { Button("x") })""").expectWarningCount(1)
    }

    @Test
    fun whenContextMenuContentMixesAllowedAndArbitraryThenWarning() {
        run(
            """DaxContextMenu(expanded = true, onDismissRequest = {}, content = { DaxDefaultItem("x"); Text("x") })""",
        ).expectWarningCount(1)
    }

    @Test
    fun whenIconButtonContentMixesAllowedAndArbitraryThenWarning() {
        run("""DaxContextMenuIconButton(iconPainter = 0, content = { DaxIconItem("x"); Button("x") })""").expectWarningCount(1)
    }

    @Test
    fun whenScopeClassUnresolvedThenClean() {
        lint()
            .files(caller("""DaxContextMenu(expanded = true, onDismissRequest = {}, content = { Text("x") })"""))
            .issues(DaxContextMenuContentDetector.INVALID_DAX_CONTEXT_MENU_CONTENT_USAGE)
            .allowCompilationErrors()
            .run()
            .expectClean()
    }

    @Test
    fun whenScopeMemberTakesNonScopeValueArgumentThenNoWarning() {
        run(
            """DaxContextMenu(expanded = true, onDismissRequest = {}, content = { DaxDefaultItem(painterResource(1).toString()) })""",
        ).expectClean()
    }

    @Test
    fun whenContextMenuContentWrapsScopeMemberInLayoutThenWarning() {
        run("""DaxContextMenu(expanded = true, onDismissRequest = {}, content = { Column { DaxDefaultItem("x") } })""").expectWarningCount(1)
    }

    @Test
    fun whenIconButtonContentWrapsScopeMemberInLayoutThenWarning() {
        run("""DaxContextMenuIconButton(iconPainter = 0, content = { Column { DaxIconItem("x") } })""").expectWarningCount(1)
    }
}
