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

import com.android.tools.lint.checks.infrastructure.TestFiles
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import com.android.tools.lint.checks.infrastructure.TestMode
import com.duckduckgo.lint.ui.DaxTextColorUsageDetector.Companion.INVALID_DAX_TEXT_COLOR_USAGE
import org.junit.Test

class DaxTextColorUsageDetectorTest {

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
        """.trimIndent()
    ).indented()

    private val themeStubs = TestFiles.kotlin(
        """
        package com.duckduckgo.common.ui.compose.theme

        import androidx.compose.runtime.Composable
        import androidx.compose.ui.graphics.Color

        data class DuckDuckGoTextColors(
            val primary: Color,
            val secondary: Color,
            val tertiary: Color
        )

        object DuckDuckGoTheme {
            val textColors: DuckDuckGoTextColors
                @Composable
                get() = DuckDuckGoTextColors(
                    primary = Color(0xFF000000),
                    secondary = Color(0xFF666666),
                    tertiary = Color(0xFF999999)
                )
        }
        """.trimIndent()
    ).indented()

    private val daxTextStub = TestFiles.kotlin(
        """
        package com.duckduckgo.common.ui.compose.component.core.text

        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Modifier
        import androidx.compose.ui.graphics.Color
        import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme

        @Composable
        fun DaxText(
            text: String,
            modifier: Modifier = Modifier,
            color: Color = DuckDuckGoTheme.textColors.primary
        ) {
            // Implementation
        }
        """.trimIndent()
    ).indented()

    @Test
    fun whenColorFromDuckDuckGoThemeTextColorsThenNoWarning() {
        lint()
            .files(
                TestFiles.kt(
                    """
                    package com.example.test

                    import androidx.compose.runtime.Composable
                    import com.duckduckgo.common.ui.compose.component.core.text.DaxText
                    import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme

                    @Composable
                    fun TestScreen() {
                        DaxText(
                            text = "Hello",
                            color = DuckDuckGoTheme.textColors.primary
                        )
                    }
                    """.trimIndent()
                ).indented(),
                composeStubs,
                themeStubs,
                daxTextStub
            )
            .allowCompilationErrors()
            .issues(INVALID_DAX_TEXT_COLOR_USAGE)
            .skipTestModes(TestMode.WHITESPACE)
            .run()
            .expectClean()
    }

    @Test
    fun whenColorFromThemeVariableTextColorsThenNoWarning() {
        lint()
            .files(
                TestFiles.kt(
                    """
                    package com.example.test

                    import androidx.compose.runtime.Composable
                    import com.duckduckgo.common.ui.compose.component.core.text.DaxText
                    import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme

                    @Composable
                    fun TestScreen() {
                        val theme = DuckDuckGoTheme
                        DaxText(
                            text = "Hello",
                            color = theme.textColors.secondary
                        )
                    }
                    """.trimIndent()
                ).indented(),
                composeStubs,
                themeStubs,
                daxTextStub
            )
            .allowCompilationErrors()
            .issues(INVALID_DAX_TEXT_COLOR_USAGE)
            .skipTestModes(TestMode.WHITESPACE)
            .run()
            .expectClean()
    }

    @Test
    fun whenDefaultColorUsedThenNoWarning() {
        lint()
            .files(
                TestFiles.kt(
                    """
                    package com.example.test

                    import androidx.compose.runtime.Composable
                    import com.duckduckgo.common.ui.compose.component.core.text.DaxText

                    @Composable
                    fun TestScreen() {
                        DaxText(text = "Hello")
                    }
                    """.trimIndent()
                ).indented(),
                composeStubs,
                themeStubs,
                daxTextStub
            )
            .allowCompilationErrors()
            .issues(INVALID_DAX_TEXT_COLOR_USAGE)
            .run()
            .expectClean()
    }

    @Test
    fun whenArbitraryColorUsedThenWarning() {
        lint()
            .files(
                TestFiles.kt(
                    """
                    package com.example.test

                    import androidx.compose.runtime.Composable
                    import androidx.compose.ui.graphics.Color
                    import com.duckduckgo.common.ui.compose.component.core.text.DaxText

                    @Composable
                    fun TestScreen() {
                        DaxText(
                            text = "Hello",
                            color = Color.Red
                        )
                    }
                    """.trimIndent()
                ).indented(),
                composeStubs,
                themeStubs,
                daxTextStub
            )
            .allowCompilationErrors()
            .issues(INVALID_DAX_TEXT_COLOR_USAGE)
            .run()
            .expect(
                """
                src/com/example/test/test.kt:11: Warning: Use DuckDuckGoTheme.textColors instead of arbitrary Color values to maintain design system consistency and theme support.

                Examples:
                • DuckDuckGoTheme.textColors.primary
                • DuckDuckGoTheme.textColors.secondary

                For one-off cases requiring custom colors, use good judgement or consider raising it in the Android Design System AOR. [InvalidDaxTextColorUsage]
                        color = Color.Red
                                ~~~~~~~~~
                0 errors, 1 warnings
                """.trimIndent()
            )
    }

    @Test
    fun whenColorConstructorUsedThenWarning() {
        lint()
            .files(
                TestFiles.kt(
                    """
                    package com.example.test

                    import androidx.compose.runtime.Composable
                    import androidx.compose.ui.graphics.Color
                    import com.duckduckgo.common.ui.compose.component.core.text.DaxText

                    @Composable
                    fun TestScreen() {
                        DaxText(
                            text = "Hello",
                            color = Color(0xFF123456)
                        )
                    }
                    """.trimIndent()
                ).indented(),
                composeStubs,
                themeStubs,
                daxTextStub
            )
            .allowCompilationErrors()
            .issues(INVALID_DAX_TEXT_COLOR_USAGE)
            .run()
            .expect(
                """
                src/com/example/test/test.kt:11: Warning: Use DuckDuckGoTheme.textColors instead of arbitrary Color values to maintain design system consistency and theme support.

                Examples:
                • DuckDuckGoTheme.textColors.primary
                • DuckDuckGoTheme.textColors.secondary

                For one-off cases requiring custom colors, use good judgement or consider raising it in the Android Design System AOR. [InvalidDaxTextColorUsage]
                        color = Color(0xFF123456)
                                ~~~~~~~~~~~~~~~~~
                0 errors, 1 warnings
                """.trimIndent()
            )
    }

    @Test
    fun whenMultipleDaxTextCallsWithMixedColorsThenWarningsForInvalidOnes() {
        lint()
            .files(
                TestFiles.kt(
                    """
                    package com.example.test

                    import androidx.compose.runtime.Composable
                    import androidx.compose.ui.graphics.Color
                    import com.duckduckgo.common.ui.compose.component.core.text.DaxText
                    import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme

                    @Composable
                    fun TestScreen() {
                        DaxText(
                            text = "Valid",
                            color = DuckDuckGoTheme.textColors.primary
                        )

                        DaxText(
                            text = "Invalid",
                            color = Color.Blue
                        )

                        DaxText(
                            text = "Also Valid",
                            color = DuckDuckGoTheme.textColors.secondary
                        )

                        DaxText(
                            text = "Also Invalid",
                            color = Color(0xFFFF0000)
                        )
                    }
                    """.trimIndent()
                ).indented(),
                composeStubs,
                themeStubs,
                daxTextStub
            )
            .allowCompilationErrors()
            .issues(INVALID_DAX_TEXT_COLOR_USAGE)
            .skipTestModes(TestMode.WHITESPACE)
            .run()
            .expect(
                """
                src/com/example/test/test.kt:17: Warning: Use DuckDuckGoTheme.textColors instead of arbitrary Color values to maintain design system consistency and theme support.

                Examples:
                • DuckDuckGoTheme.textColors.primary
                • DuckDuckGoTheme.textColors.secondary

                For one-off cases requiring custom colors, use good judgement or consider raising it in the Android Design System AOR. [InvalidDaxTextColorUsage]
                        color = Color.Blue
                                ~~~~~~~~~~
                src/com/example/test/test.kt:27: Warning: Use DuckDuckGoTheme.textColors instead of arbitrary Color values to maintain design system consistency and theme support.

                Examples:
                • DuckDuckGoTheme.textColors.primary
                • DuckDuckGoTheme.textColors.secondary

                For one-off cases requiring custom colors, use good judgement or consider raising it in the Android Design System AOR. [InvalidDaxTextColorUsage]
                        color = Color(0xFFFF0000)
                                ~~~~~~~~~~~~~~~~~
                0 errors, 2 warnings
                """.trimIndent()
            )
    }

    @Test
    fun whenDaxTextUsedWithoutColorParameterThenNoWarning() {
        lint()
            .files(
                TestFiles.kt(
                    """
                    package com.example.test

                    import androidx.compose.runtime.Composable
                    import com.duckduckgo.common.ui.compose.component.core.text.DaxText

                    @Composable
                    fun TestScreen() {
                        DaxText(text = "Hello World")
                    }
                    """.trimIndent()
                ).indented(),
                composeStubs,
                themeStubs,
                daxTextStub
            )
            .allowCompilationErrors()
            .issues(INVALID_DAX_TEXT_COLOR_USAGE)
            .run()
            .expectClean()
    }
}
