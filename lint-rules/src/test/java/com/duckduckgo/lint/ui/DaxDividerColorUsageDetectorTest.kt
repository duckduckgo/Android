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

import com.android.tools.lint.checks.infrastructure.TestFiles
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import com.android.tools.lint.checks.infrastructure.TestMode
import com.duckduckgo.lint.ui.DaxDividerColorUsageDetector.Companion.INVALID_DAX_DIVIDER_COLOR_USAGE
import org.junit.Test

class DaxDividerColorUsageDetectorTest {

    private val composeStubs = TestFiles.kotlin(
        """
        package androidx.compose.ui.graphics

        data class Color(val value: Long) {
            companion object {
                val Red = Color(0xFFFF0000)
                val Blue = Color(0xFF0000FF)
            }
        }

        package androidx.compose.runtime

        annotation class Composable

        package androidx.compose.ui

        class Modifier {
            companion object : Modifier()
        }
        """.trimIndent(),
    ).indented()

    private val themeStubs = TestFiles.kotlin(
        """
        package com.duckduckgo.common.ui.compose.theme

        import androidx.compose.runtime.Composable
        import androidx.compose.ui.graphics.Color

        data class DuckDuckGoSystemColors(
            val lines: Color,
        )

        data class DuckDuckGoBackgroundColors(
            val container: Color,
        )

        data class DuckDuckGoBrandColors(
            val accentBlue: Color,
        )

        data class DuckDuckGoColors(
            val system: DuckDuckGoSystemColors,
            val backgrounds: DuckDuckGoBackgroundColors,
            val brand: DuckDuckGoBrandColors,
        )

        object DuckDuckGoTheme {
            val colors: DuckDuckGoColors
                @Composable
                get() = DuckDuckGoColors(
                    system = DuckDuckGoSystemColors(lines = Color(0xFFCCCCCC)),
                    backgrounds = DuckDuckGoBackgroundColors(container = Color(0xFFEEEEEE)),
                    brand = DuckDuckGoBrandColors(accentBlue = Color(0xFF1E90FF)),
                )
        }

        val AlertGreen: Color
            @Composable
            get() = Color(0xFF21C000)
        """.trimIndent(),
    ).indented()

    private val daxDividerStubs = TestFiles.kotlin(
        """
        package com.duckduckgo.common.ui.compose.divider

        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Modifier
        import androidx.compose.ui.graphics.Color
        import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme

        @Composable
        fun DaxHorizontalDivider(
            modifier: Modifier = Modifier,
            color: Color = DuckDuckGoTheme.colors.system.lines,
        ) {
            // Implementation
        }

        @Composable
        fun DaxVerticalDivider(
            modifier: Modifier = Modifier,
            color: Color = DuckDuckGoTheme.colors.backgrounds.container,
        ) {
            // Implementation
        }
        """.trimIndent(),
    ).indented()

    @Test
    fun whenDaxHorizontalDividerWithoutColorThenNoWarning() {
        lint()
            .files(
                TestFiles.kt(
                    """
                    package com.example.test

                    import androidx.compose.runtime.Composable
                    import com.duckduckgo.common.ui.compose.divider.DaxHorizontalDivider

                    @Composable
                    fun TestScreen() {
                        DaxHorizontalDivider()
                    }
                    """.trimIndent(),
                ).indented(),
                composeStubs,
                themeStubs,
                daxDividerStubs,
            )
            .allowCompilationErrors()
            .issues(INVALID_DAX_DIVIDER_COLOR_USAGE)
            .run()
            .expectClean()
    }

    @Test
    fun whenDaxHorizontalDividerWithSystemLinesThenNoWarning() {
        lint()
            .files(
                TestFiles.kt(
                    """
                    package com.example.test

                    import androidx.compose.runtime.Composable
                    import com.duckduckgo.common.ui.compose.divider.DaxHorizontalDivider
                    import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme

                    @Composable
                    fun TestScreen() {
                        DaxHorizontalDivider(
                            color = DuckDuckGoTheme.colors.system.lines,
                        )
                    }
                    """.trimIndent(),
                ).indented(),
                composeStubs,
                themeStubs,
                daxDividerStubs,
            )
            .allowCompilationErrors()
            .issues(INVALID_DAX_DIVIDER_COLOR_USAGE)
            .skipTestModes(TestMode.WHITESPACE)
            .run()
            .expectClean()
    }

    @Test
    fun whenDaxVerticalDividerWithBackgroundsContainerThenNoWarning() {
        lint()
            .files(
                TestFiles.kt(
                    """
                    package com.example.test

                    import androidx.compose.runtime.Composable
                    import com.duckduckgo.common.ui.compose.divider.DaxVerticalDivider
                    import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme

                    @Composable
                    fun TestScreen() {
                        DaxVerticalDivider(
                            color = DuckDuckGoTheme.colors.backgrounds.container,
                        )
                    }
                    """.trimIndent(),
                ).indented(),
                composeStubs,
                themeStubs,
                daxDividerStubs,
            )
            .allowCompilationErrors()
            .issues(INVALID_DAX_DIVIDER_COLOR_USAGE)
            .skipTestModes(TestMode.WHITESPACE)
            .run()
            .expectClean()
    }

    @Test
    fun whenDaxHorizontalDividerWithBrandAccentThenNoWarning() {
        lint()
            .files(
                TestFiles.kt(
                    """
                    package com.example.test

                    import androidx.compose.runtime.Composable
                    import com.duckduckgo.common.ui.compose.divider.DaxHorizontalDivider
                    import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme

                    @Composable
                    fun TestScreen() {
                        DaxHorizontalDivider(
                            color = DuckDuckGoTheme.colors.brand.accentBlue,
                        )
                    }
                    """.trimIndent(),
                ).indented(),
                composeStubs,
                themeStubs,
                daxDividerStubs,
            )
            .allowCompilationErrors()
            .issues(INVALID_DAX_DIVIDER_COLOR_USAGE)
            .skipTestModes(TestMode.WHITESPACE)
            .run()
            .expectClean()
    }

    @Test
    fun whenDaxHorizontalDividerWithDefaultsObjectUsingThemeColorThenNoWarning() {
        lint()
            .files(
                TestFiles.kt(
                    """
                    package com.example.test

                    import androidx.compose.runtime.Composable
                    import androidx.compose.ui.graphics.Color
                    import com.duckduckgo.common.ui.compose.divider.DaxHorizontalDivider
                    import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme

                    object ExampleDividerDefaults {
                        val lineColor: Color
                            @Composable
                            get() = DuckDuckGoTheme.colors.system.lines
                    }

                    @Composable
                    fun TestScreen() {
                        DaxHorizontalDivider(
                            color = ExampleDividerDefaults.lineColor,
                        )
                    }
                    """.trimIndent(),
                ).indented(),
                composeStubs,
                themeStubs,
                daxDividerStubs,
            )
            .allowCompilationErrors()
            .issues(INVALID_DAX_DIVIDER_COLOR_USAGE)
            .skipTestModes(TestMode.WHITESPACE)
            .run()
            .expectClean()
    }

    @Test
    fun whenDaxVerticalDividerWithDefaultsObjectUsingStaticThemeColorThenNoWarning() {
        lint()
            .files(
                TestFiles.kt(
                    """
                    package com.example.test

                    import androidx.compose.runtime.Composable
                    import androidx.compose.ui.graphics.Color
                    import com.duckduckgo.common.ui.compose.divider.DaxVerticalDivider
                    import com.duckduckgo.common.ui.compose.theme.AlertGreen

                    object ExampleDividerDefaults {
                        val statusColor: Color
                            @Composable
                            get() = AlertGreen
                    }

                    @Composable
                    fun TestScreen() {
                        DaxVerticalDivider(
                            color = ExampleDividerDefaults.statusColor,
                        )
                    }
                    """.trimIndent(),
                ).indented(),
                composeStubs,
                themeStubs,
                daxDividerStubs,
            )
            .allowCompilationErrors()
            .issues(INVALID_DAX_DIVIDER_COLOR_USAGE)
            .skipTestModes(TestMode.WHITESPACE, TestMode.IMPORT_ALIAS)
            .run()
            .expectClean()
    }

    @Test
    fun whenDaxHorizontalDividerWithDefaultsObjectUsingArbitraryColorThenWarning() {
        lint()
            .files(
                TestFiles.kt(
                    """
                    package com.example.test

                    import androidx.compose.runtime.Composable
                    import androidx.compose.ui.graphics.Color
                    import com.duckduckgo.common.ui.compose.divider.DaxHorizontalDivider

                    object ExampleDividerDefaults {
                        val invalidColor: Color
                            @Composable
                            get() = Color.Red
                    }

                    @Composable
                    fun TestScreen() {
                        DaxHorizontalDivider(
                            color = ExampleDividerDefaults.invalidColor,
                        )
                    }
                    """.trimIndent(),
                ).indented(),
                composeStubs,
                themeStubs,
                daxDividerStubs,
            )
            .allowCompilationErrors()
            .issues(INVALID_DAX_DIVIDER_COLOR_USAGE)
            .skipTestModes(TestMode.WHITESPACE)
            .run()
            .expectContains("InvalidDaxDividerColorUsage")
            .expectContains("color = ExampleDividerDefaults.invalidColor")
            .expectContains("0 errors, 1 warnings")
    }

    @Test
    fun whenDaxHorizontalDividerWithArbitraryColorThenWarning() {
        lint()
            .files(
                TestFiles.kt(
                    """
                    package com.example.test

                    import androidx.compose.runtime.Composable
                    import androidx.compose.ui.graphics.Color
                    import com.duckduckgo.common.ui.compose.divider.DaxHorizontalDivider

                    @Composable
                    fun TestScreen() {
                        DaxHorizontalDivider(
                            color = Color.Red,
                        )
                    }
                    """.trimIndent(),
                ).indented(),
                composeStubs,
                themeStubs,
                daxDividerStubs,
            )
            .allowCompilationErrors()
            .issues(INVALID_DAX_DIVIDER_COLOR_USAGE)
            .run()
            .expectContains("InvalidDaxDividerColorUsage")
            .expectContains("color = Color.Red")
            .expectContains("0 errors, 1 warnings")
    }

    @Test
    fun whenDaxVerticalDividerWithColorConstructorThenWarning() {
        lint()
            .files(
                TestFiles.kt(
                    """
                    package com.example.test

                    import androidx.compose.runtime.Composable
                    import androidx.compose.ui.graphics.Color
                    import com.duckduckgo.common.ui.compose.divider.DaxVerticalDivider

                    @Composable
                    fun TestScreen() {
                        DaxVerticalDivider(
                            color = Color(0xFF123456),
                        )
                    }
                    """.trimIndent(),
                ).indented(),
                composeStubs,
                themeStubs,
                daxDividerStubs,
            )
            .allowCompilationErrors()
            .issues(INVALID_DAX_DIVIDER_COLOR_USAGE)
            .run()
            .expectContains("InvalidDaxDividerColorUsage")
            .expectContains("color = Color(0xFF123456)")
            .expectContains("0 errors, 1 warnings")
    }

    @Test
    fun whenNonDuckDuckGoColorsPathThenWarning() {
        lint()
            .files(
                TestFiles.kt(
                    """
                    package com.example.test

                    import androidx.compose.runtime.Composable
                    import androidx.compose.ui.graphics.Color
                    import com.duckduckgo.common.ui.compose.divider.DaxHorizontalDivider

                    data class OtherColors(val danger: Color)

                    object SomeOtherPalette {
                        val colors: OtherColors = OtherColors(danger = Color.Red)
                    }

                    @Composable
                    fun TestScreen() {
                        DaxHorizontalDivider(
                            color = SomeOtherPalette.colors.danger,
                        )
                    }
                    """.trimIndent(),
                ).indented(),
                composeStubs,
                themeStubs,
                daxDividerStubs,
            )
            .allowCompilationErrors()
            .issues(INVALID_DAX_DIVIDER_COLOR_USAGE)
            .skipTestModes(TestMode.WHITESPACE)
            .run()
            .expectContains("InvalidDaxDividerColorUsage")
            .expectContains("color = SomeOtherPalette.colors.danger")
            .expectContains("0 errors, 1 warnings")
    }

    @Test
    fun whenMixedValidAndInvalidCallsThenWarningsForInvalidOnly() {
        lint()
            .files(
                TestFiles.kt(
                    """
                    package com.example.test

                    import androidx.compose.runtime.Composable
                    import androidx.compose.ui.graphics.Color
                    import com.duckduckgo.common.ui.compose.divider.DaxHorizontalDivider
                    import com.duckduckgo.common.ui.compose.divider.DaxVerticalDivider
                    import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme

                    @Composable
                    fun TestScreen() {
                        DaxHorizontalDivider(
                            color = DuckDuckGoTheme.colors.system.lines,
                        )

                        DaxHorizontalDivider(
                            color = Color.Blue,
                        )

                        DaxVerticalDivider(
                            color = DuckDuckGoTheme.colors.backgrounds.container,
                        )

                        DaxVerticalDivider(
                            color = Color(0xFFFF0000),
                        )
                    }
                    """.trimIndent(),
                ).indented(),
                composeStubs,
                themeStubs,
                daxDividerStubs,
            )
            .allowCompilationErrors()
            .issues(INVALID_DAX_DIVIDER_COLOR_USAGE)
            .skipTestModes(TestMode.WHITESPACE)
            .run()
            .expectContains("color = Color.Blue")
            .expectContains("color = Color(0xFFFF0000)")
            .expectContains("0 errors, 2 warnings")
    }
}
