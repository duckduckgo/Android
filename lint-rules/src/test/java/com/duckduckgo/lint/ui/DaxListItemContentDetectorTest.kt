/*
 * Copyright (c) 2025 DuckDuckGo
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

class DaxListItemContentDetectorTest {

    private val scopeStubs = kotlin(
        """
        package com.duckduckgo.common.ui.compose.listitem
        object DaxListItemTrailingScope { fun Switch() {}; fun Icon() {} }
        object DaxListItemLeadingScope { fun Icon() {} }
        fun DaxOneLineListItem(
            text: String,
            leadingContent: (DaxListItemLeadingScope.() -> Unit)? = null,
            trailingContent: (DaxListItemTrailingScope.() -> Unit)? = null,
        ) {}
        fun DaxTwoLineListItem(
            primaryText: String,
            secondaryText: String,
            leadingContent: (DaxListItemLeadingScope.() -> Unit)? = null,
            trailingContent: (DaxListItemTrailingScope.() -> Unit)? = null,
        ) {}
        fun DaxSettingsListItem(
            primaryText: String,
            leadingContent: (DaxListItemLeadingScope.() -> Unit)? = null,
        ) {}
        """,
    ).indented()

    private fun caller(body: String) = kotlin(
        """
        package com.test
        import com.duckduckgo.common.ui.compose.listitem.*
        fun BadSwitch() {}
        fun BadIcon() {}
        fun screen() { $body }
        """,
    ).indented()

    private fun run(body: String) = lint()
        .files(scopeStubs, caller(body))
        .issues(DaxListItemContentDetector.INVALID_DAX_LIST_ITEM_CONTENT_USAGE)
        .run()

    @Test
    fun whenSlotUsesScopeMemberThenNoWarning() {
        run("""DaxOneLineListItem(text = "x", trailingContent = { Switch() })""").expectClean()
    }

    @Test
    fun whenTrailingContentUsesArbitraryComposableThenWarning() {
        run("""DaxOneLineListItem(text = "x", trailingContent = { BadSwitch() })""").expectWarningCount(1)
    }

    @Test
    fun whenTrailingContentMixesAllowedAndArbitraryThenWarning() {
        run("""DaxOneLineListItem(text = "x", trailingContent = { Switch(); BadSwitch() })""").expectWarningCount(1)
    }

    @Test
    fun whenLeadingContentUsesArbitraryComposableThenWarning() {
        run("""DaxOneLineListItem(text = "x", leadingContent = { BadIcon() })""").expectWarningCount(1)
    }

    @Test
    fun whenTwoLineListItemTrailingContentArbitraryThenWarning() {
        run("""DaxTwoLineListItem(primaryText = "x", secondaryText = "y", trailingContent = { BadSwitch() })""").expectWarningCount(1)
    }

    @Test
    fun whenSettingsListItemLeadingContentArbitraryThenWarning() {
        run("""DaxSettingsListItem(primaryText = "x", leadingContent = { BadIcon() })""").expectWarningCount(1)
    }

    @Test
    fun whenScopeClassesUnresolvedThenClean() {
        lint()
            .files(caller("""DaxOneLineListItem(text = "x", trailingContent = { BadSwitch() })"""))
            .issues(DaxListItemContentDetector.INVALID_DAX_LIST_ITEM_CONTENT_USAGE)
            .allowCompilationErrors()
            .run()
            .expectClean()
    }
}
