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

class DaxOnboardingDialogNestedScrollDetectorTest {

    private val stubs = kotlin(
        """
        package com.duckduckgo.common.ui.compose.onboarding
        object DaxOnboardingDialogContentScope
        fun DaxOnboardingDialog(title: String, content: (DaxOnboardingDialogContentScope.() -> Unit)? = null) {}
        fun DaxContextualOnboardingDialog(title: String, content: (DaxOnboardingDialogContentScope.() -> Unit)? = null) {}
        """,
    ).indented()

    private val composeStubs = kotlin(
        """
        package androidx.compose.foundation
        object Modifier
        object ScrollState
        fun Modifier.verticalScroll(state: ScrollState): Modifier = this
        fun Modifier.horizontalScroll(state: ScrollState): Modifier = this
        fun LazyColumn(content: () -> Unit) {}
        fun LazyRow(content: () -> Unit) {}
        fun Column(modifier: Modifier = Modifier, content: () -> Unit) {}
        """,
    ).indented()

    private fun caller(body: String) = kotlin(
        """
        package com.test
        import com.duckduckgo.common.ui.compose.onboarding.*
        import androidx.compose.foundation.*
        fun ScrollingSection() { LazyColumn {} }
        fun screen() { $body }
        """,
    ).indented()

    private fun run(body: String) = lint()
        .files(stubs, composeStubs, caller(body))
        .issues(DaxOnboardingDialogNestedScrollDetector.NESTED_SCROLL_IN_DAX_ONBOARDING_DIALOG)
        .run()

    @Test
    fun whenContentHasNoScrollerThenNoWarning() {
        run("""DaxOnboardingDialog(title = "x", content = { Column {} })""").expectClean()
    }

    @Test
    fun whenContentHasLazyColumnThenWarning() {
        run("""DaxOnboardingDialog(title = "x", content = { LazyColumn {} })""").expectWarningCount(1)
    }

    @Test
    fun whenContentHasVerticalScrollModifierThenWarning() {
        run("""DaxOnboardingDialog(title = "x") { Column(modifier = Modifier.verticalScroll(ScrollState)) {} }""").expectWarningCount(1)
    }

    @Test
    fun whenContextualContentHasNestedLazyColumnThenWarning() {
        run("""DaxContextualOnboardingDialog(title = "x", content = { Column { LazyColumn {} } })""").expectWarningCount(1)
    }

    @Test
    fun whenContentHasHorizontalScrollerThenNoWarning() {
        run("""DaxOnboardingDialog(title = "x", content = { LazyRow {}; Column(modifier = Modifier.horizontalScroll(ScrollState)) {} })""")
            .expectClean()
    }

    @Test
    fun whenScrollerIsOutsideContentSlotThenNoWarning() {
        run("""LazyColumn {}; DaxOnboardingDialog(title = "x", content = { Column {} })""").expectClean()
    }

    @Test
    fun whenScrollerIsHiddenInCustomComposableThenNoWarning() {
        run("""DaxOnboardingDialog(title = "x", content = { ScrollingSection() })""").expectClean()
    }
}
