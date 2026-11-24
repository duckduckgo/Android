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
        fun DaxOnboardingDialog(title: String, body: () -> Unit, content: (DaxOnboardingDialogContentScope.() -> Unit)? = null) {}
        fun DaxContextualOnboardingDialog(title: String, content: (DaxOnboardingDialogContentScope.() -> Unit)? = null) {}
        fun DaxContextualOnboardingDialog(title: String, body: () -> Unit, content: (DaxOnboardingDialogContentScope.() -> Unit)? = null) {}
        """,
    ).indented()

    private val composeStubs = kotlin(
        """
        package androidx.compose.foundation
        object Modifier
        object ScrollState
        fun Modifier.verticalScroll(state: ScrollState): Modifier = this
        fun Modifier.horizontalScroll(state: ScrollState): Modifier = this
        fun Column(modifier: Modifier = Modifier, content: () -> Unit) {}
        """,
    ).indented()

    private val lazyStubs = listOf(
        kotlin("package androidx.compose.foundation.lazy\nfun LazyColumn(content: () -> Unit) {}\nfun LazyRow(content: () -> Unit) {}"),
        kotlin("package androidx.compose.foundation.lazy.grid\nfun LazyVerticalGrid(content: () -> Unit) {}"),
        kotlin("package androidx.compose.foundation.lazy.staggeredgrid\nfun LazyVerticalStaggeredGrid(content: () -> Unit) {}"),
    )

    private val otherDialog = kotlin(
        """
        package com.other
        fun DaxOnboardingDialog(title: String, content: () -> Unit) {}
        """,
    ).indented()

    private fun caller(body: String) = kotlin(
        """
        package com.test
        import com.duckduckgo.common.ui.compose.onboarding.*
        import androidx.compose.foundation.*
        import androidx.compose.foundation.lazy.*
        import androidx.compose.foundation.lazy.grid.*
        import androidx.compose.foundation.lazy.staggeredgrid.*
        fun ScrollingSection() { LazyColumn {} }
        object Analytics { fun verticalScroll() {} }
        fun screen() { $body }
        """,
    ).indented()

    private fun run(body: String) = lint()
        .files(stubs, composeStubs, *lazyStubs.toTypedArray(), otherDialog, caller(body))
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

    @Test
    fun whenBodyHasLazyColumnThenWarning() {
        run("""DaxOnboardingDialog(title = "x", body = { LazyColumn {} })""").expectWarningCount(1)
    }

    @Test
    fun whenContextualBodyHasVerticalScrollModifierThenWarning() {
        run("""DaxContextualOnboardingDialog(title = "x", body = { Column(Modifier.verticalScroll(ScrollState)) {} })""")
            .expectWarningCount(1)
    }

    @Test
    fun whenBodyAndContentHaveVerticalLazyGridsThenWarningForEach() {
        run("""DaxOnboardingDialog(title = "x", body = { LazyVerticalGrid {} }, content = { LazyVerticalStaggeredGrid {} })""")
            .expectWarningCount(2)
    }

    @Test
    fun whenContentLambdaIsStoredInALocalValThenWarning() {
        run(
            """
            val options: DaxOnboardingDialogContentScope.() -> Unit = { LazyColumn {} }
            DaxOnboardingDialog(title = "x", content = options)
            """.trimIndent(),
        ).expectWarningCount(1)
    }

    @Test
    fun whenDialogHasTheSameNameInAnotherPackageThenNoWarning() {
        run("""com.other.DaxOnboardingDialog(title = "x", content = { LazyColumn {} })""").expectClean()
    }

    @Test
    fun whenCallsResolveToUnrelatedFunctionsThenNoWarning() {
        run(
            """
            fun LazyColumn(content: () -> Unit) {}
            DaxOnboardingDialog(title = "x", content = { LazyColumn {}; Analytics.verticalScroll() })
            """.trimIndent(),
        ).expectClean()
    }
}
