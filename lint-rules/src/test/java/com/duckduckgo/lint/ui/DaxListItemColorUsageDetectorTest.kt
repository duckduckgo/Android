package com.duckduckgo.lint.ui

import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import com.android.tools.lint.checks.infrastructure.TestMode
import org.junit.Test

class DaxListItemColorUsageDetectorTest {

    private val stubs = kotlin(
        """
        package com.duckduckgo.common.ui.compose.listitem
        import androidx.compose.ui.graphics.Color
        object TextColors { val primary: Color = Color.Unspecified }
        object Colors { val destructive: Color = Color.Unspecified }
        object DuckDuckGoTheme { val textColors = TextColors; val colors = Colors }
        fun DaxOneLineListItem(text: String, primaryTextColor: Color = Color.Unspecified) {}
        fun DaxTwoLineListItem(primaryText: String, secondaryText: String, primaryTextColor: Color = Color.Unspecified, secondaryTextColor: Color = Color.Unspecified) {}
        """,
    ).indented()

    @Test
    fun whenColorIsArbitraryThenWarning() {
        lint().files(
            stubs,
            kotlin(
                """
                package com.test
                import androidx.compose.ui.graphics.Color
                import com.duckduckgo.common.ui.compose.listitem.DaxOneLineListItem
                fun screen() { DaxOneLineListItem(text = "x", primaryTextColor = Color.Red) }
                """,
            ).indented(),
        ).allowCompilationErrors()
            .issues(DaxListItemColorUsageDetector.INVALID_DAX_LIST_ITEM_COLOR_USAGE)
            .skipTestModes(TestMode.WHITESPACE)
            .run()
            .expectWarningCount(1)
    }

    @Test
    fun whenColorIsThemeColorThenClean() {
        lint().files(
            stubs,
            kotlin(
                """
                package com.test
                import com.duckduckgo.common.ui.compose.listitem.DaxOneLineListItem
                import com.duckduckgo.common.ui.compose.listitem.DuckDuckGoTheme
                fun screen() { DaxOneLineListItem(text = "x", primaryTextColor = DuckDuckGoTheme.textColors.primary) }
                """,
            ).indented(),
        ).allowCompilationErrors()
            .issues(DaxListItemColorUsageDetector.INVALID_DAX_LIST_ITEM_COLOR_USAGE)
            .skipTestModes(TestMode.WHITESPACE)
            .run()
            .expectClean()
    }

    @Test
    fun whenColorIsDestructiveThemeColorThenClean() {
        lint().files(
            stubs,
            kotlin(
                """
                package com.test
                import com.duckduckgo.common.ui.compose.listitem.DaxOneLineListItem
                import com.duckduckgo.common.ui.compose.listitem.DuckDuckGoTheme
                fun screen() { DaxOneLineListItem(text = "x", primaryTextColor = DuckDuckGoTheme.colors.destructive) }
                """,
            ).indented(),
        ).allowCompilationErrors()
            .issues(DaxListItemColorUsageDetector.INVALID_DAX_LIST_ITEM_COLOR_USAGE)
            .skipTestModes(TestMode.WHITESPACE)
            .run()
            .expectClean()
    }

    @Test
    fun whenTwoLineListItemPrimaryColorArbitraryThenWarning() {
        lint().files(
            stubs,
            kotlin(
                """
                package com.test
                import androidx.compose.ui.graphics.Color
                import com.duckduckgo.common.ui.compose.listitem.DaxTwoLineListItem
                fun screen() { DaxTwoLineListItem(primaryText = "x", secondaryText = "y", primaryTextColor = Color.Red) }
                """,
            ).indented(),
        ).allowCompilationErrors()
            .issues(DaxListItemColorUsageDetector.INVALID_DAX_LIST_ITEM_COLOR_USAGE)
            .skipTestModes(TestMode.WHITESPACE)
            .run()
            .expectWarningCount(1)
    }

    @Test
    fun whenTwoLineListItemSecondaryColorArbitraryThenWarning() {
        lint().files(
            stubs,
            kotlin(
                """
                package com.test
                import androidx.compose.ui.graphics.Color
                import com.duckduckgo.common.ui.compose.listitem.DaxTwoLineListItem
                fun screen() { DaxTwoLineListItem(primaryText = "x", secondaryText = "y", secondaryTextColor = Color.Red) }
                """,
            ).indented(),
        ).allowCompilationErrors()
            .issues(DaxListItemColorUsageDetector.INVALID_DAX_LIST_ITEM_COLOR_USAGE)
            .skipTestModes(TestMode.WHITESPACE)
            .run()
            .expectWarningCount(1)
    }

    @Test
    fun whenTwoLineListItemSecondaryColorIsThemeColorThenClean() {
        lint().files(
            stubs,
            kotlin(
                """
                package com.test
                import com.duckduckgo.common.ui.compose.listitem.DaxTwoLineListItem
                import com.duckduckgo.common.ui.compose.listitem.DuckDuckGoTheme
                fun screen() { DaxTwoLineListItem(primaryText = "x", secondaryText = "y", secondaryTextColor = DuckDuckGoTheme.textColors.primary) }
                """,
            ).indented(),
        ).allowCompilationErrors()
            .issues(DaxListItemColorUsageDetector.INVALID_DAX_LIST_ITEM_COLOR_USAGE)
            .skipTestModes(TestMode.WHITESPACE)
            .run()
            .expectClean()
    }
}
