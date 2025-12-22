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
import com.duckduckgo.lint.ui.DaxTextFieldTrailingIconDetector.Companion.INVALID_DAX_TEXT_FIELD_TRAILING_ICON_USAGE
import org.junit.Test

class DaxTextFieldTrailingIconDetectorTest {

    private val composeStubs = TestFiles.kotlin(
        """
        package androidx.compose.ui.graphics

        data class Color(val value: Long)

        package androidx.compose.ui.graphics.painter

        interface Painter

        package androidx.compose.runtime

        annotation class Composable

        package androidx.compose.ui

        class Modifier {
            companion object : Modifier()
        }

        package androidx.compose.material3

        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Modifier
        import androidx.compose.ui.graphics.painter.Painter

        @Composable
        fun Icon(
            painter: Painter,
            contentDescription: String?,
            modifier: Modifier = Modifier
        ) {}

        @Composable
        fun IconButton(
            onClick: () -> Unit,
            modifier: Modifier = Modifier,
            enabled: Boolean = true,
            content: @Composable () -> Unit
        ) {}
        """.trimIndent(),
    ).indented()

    private val textFieldStateStub = TestFiles.kotlin(
        """
        package androidx.compose.foundation.text.input

        class TextFieldState(initialText: String = "")
        """.trimIndent(),
    ).indented()

    private val daxTextFieldStub = TestFiles.kotlin(
        """
        package com.duckduckgo.common.ui.compose.textfield

        import androidx.compose.foundation.text.input.TextFieldState
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Modifier
        import androidx.compose.ui.graphics.painter.Painter

        @Composable
        fun DaxTextField(
            state: TextFieldState,
            modifier: Modifier = Modifier,
            label: String? = null,
            trailingIcon: (@Composable DaxTextFieldTrailingIconScope.() -> Unit)? = null
        ) {
            // Implementation
        }

        object DaxTextFieldTrailingIconScope {
            @Composable
            fun DaxTextFieldTrailingIcon(
                painter: Painter,
                contentDescription: String?,
                modifier: Modifier = Modifier,
                enabled: Boolean = true,
                onClick: (() -> Unit)? = null
            ) {
                // Implementation
            }
            
            @Composable
            fun SomeComposable(){
                // Implementation
            }
        }
        """.trimIndent(),
    ).indented()

    private val painterStub = TestFiles.kotlin(
        """
        package androidx.compose.ui.res

        import androidx.compose.runtime.Composable
        import androidx.compose.ui.graphics.painter.Painter

        @Composable
        fun painterResource(id: Int): Painter = object : Painter {}
        """.trimIndent(),
    ).indented()

    @Test
    fun whenDaxTextFieldTrailingIconUsedThenNoWarning() {
        lint()
            .files(
                TestFiles.kt(
                    """
                    package com.example.test

                    import androidx.compose.foundation.text.input.TextFieldState
                    import androidx.compose.runtime.Composable
                    import androidx.compose.ui.res.painterResource
                    import com.duckduckgo.common.ui.compose.textfield.DaxTextField
                    import com.duckduckgo.common.ui.compose.textfield.DaxTextFieldTrailingIconScope.DaxTextFieldTrailingIcon

                    @Composable
                    fun TestScreen() {
                        DaxTextField(
                            state = TextFieldState(),
                            label = "Enter text",
                            trailingIcon = {
                                DaxTextFieldTrailingIcon(
                                    painter = painterResource(0),
                                    contentDescription = "Copy"
                                )
                            }
                        )
                    }
                    """.trimIndent(),
                ).indented(),
                composeStubs,
                textFieldStateStub,
                daxTextFieldStub,
                painterStub,
            )
            .allowCompilationErrors()
            .issues(INVALID_DAX_TEXT_FIELD_TRAILING_ICON_USAGE)
            .skipTestModes(TestMode.WHITESPACE, TestMode.REORDER_ARGUMENTS)
            .run()
            .expectClean()
    }

    @Test
    fun whenValidComposableFromDaxTextFieldTrailingIconScopeUsedThenNoWarning() {
        lint()
            .files(
                TestFiles.kt(
                    """
                    package com.example.test

                    import androidx.compose.foundation.text.input.TextFieldState
                    import androidx.compose.runtime.Composable
                    import androidx.compose.ui.res.painterResource
                    import com.duckduckgo.common.ui.compose.textfield.DaxTextField
                    import com.duckduckgo.common.ui.compose.textfield.DaxTextFieldTrailingIconScope.DaxTextFieldTrailingIcon

                    @Composable
                    fun TestScreen() {
                        DaxTextField(
                            state = TextFieldState(),
                            label = "Enter text",
                            trailingIcon = {
                                SomeComposable()
                            }
                        )
                    }
                    """.trimIndent(),
                ).indented(),
                composeStubs,
                textFieldStateStub,
                daxTextFieldStub,
                painterStub,
            )
            .allowCompilationErrors()
            .issues(INVALID_DAX_TEXT_FIELD_TRAILING_ICON_USAGE)
            .skipTestModes(TestMode.WHITESPACE, TestMode.REORDER_ARGUMENTS)
            .run()
            .expectClean()
    }

    @Test
    fun whenDaxTextFieldTrailingIconUsedWithoutScopePrefixThenNoWarning() {
        lint()
            .files(
                TestFiles.kt(
                    """
                    package com.example.test

                    import androidx.compose.foundation.text.input.TextFieldState
                    import androidx.compose.runtime.Composable
                    import androidx.compose.ui.res.painterResource
                    import com.duckduckgo.common.ui.compose.textfield.DaxTextField

                    @Composable
                    fun TestScreen() {
                        DaxTextField(
                            state = TextFieldState(),
                            label = "Enter text",
                            trailingIcon = {
                                DaxTextFieldTrailingIcon(
                                    painter = painterResource(0),
                                    contentDescription = "Copy"
                                )
                            }
                        )
                    }
                    """.trimIndent(),
                ).indented(),
                composeStubs,
                textFieldStateStub,
                daxTextFieldStub,
                painterStub,
            )
            .allowCompilationErrors()
            .issues(INVALID_DAX_TEXT_FIELD_TRAILING_ICON_USAGE)
            .skipTestModes(TestMode.WHITESPACE, TestMode.REORDER_ARGUMENTS)
            .run()
            .expectClean()
    }

    @Test
    fun whenNoTrailingIconProvidedThenNoWarning() {
        lint()
            .files(
                TestFiles.kt(
                    """
                    package com.example.test

                    import androidx.compose.foundation.text.input.TextFieldState
                    import androidx.compose.runtime.Composable
                    import com.duckduckgo.common.ui.compose.textfield.DaxTextField

                    @Composable
                    fun TestScreen() {
                        DaxTextField(
                            state = TextFieldState(),
                            label = "Enter text"
                        )
                    }
                    """.trimIndent(),
                ).indented(),
                composeStubs,
                textFieldStateStub,
                daxTextFieldStub,
            )
            .allowCompilationErrors()
            .issues(INVALID_DAX_TEXT_FIELD_TRAILING_ICON_USAGE)
            .run()
            .expectClean()
    }

    @Test
    fun whenIconComposableUsedInsteadOfDaxTextFieldTrailingIconThenWarning() {
        lint()
            .files(
                TestFiles.kt(
                    """
                    package com.example.test

                    import androidx.compose.foundation.text.input.TextFieldState
                    import androidx.compose.material3.Icon
                    import androidx.compose.runtime.Composable
                    import androidx.compose.ui.res.painterResource
                    import com.duckduckgo.common.ui.compose.textfield.DaxTextField

                    @Composable
                    fun TestScreen() {
                        DaxTextField(
                            state = TextFieldState(),
                            label = "Enter text",
                            trailingIcon = {
                                Icon(
                                    painter = painterResource(0),
                                    contentDescription = "Copy"
                                )
                            }
                        )
                    }
                    """.trimIndent(),
                ).indented(),
                composeStubs,
                textFieldStateStub,
                daxTextFieldStub,
                painterStub,
            )
            .allowCompilationErrors()
            .issues(INVALID_DAX_TEXT_FIELD_TRAILING_ICON_USAGE)
            .run()
            .expect(
                """
                src/com/example/test/test.kt:14: Warning: Use composables from DaxTextFieldTrailingIconScope instead of arbitrary composables 
                for the trailingIcon parameter to maintain design system consistency.

                Example:
                DaxTextField(
                    state = state,
                    trailingIcon = {
                        DaxTextFieldTrailingIcon(
                            painter = painterResource(R.drawable.ic_copy_24),
                            contentDescription = stringResource(R.string.icon_description)
                        )
                    }
                )

                This ensures consistent styling, spacing, and behavior across all text field icons in the app. [InvalidDaxTextFieldTrailingIconUsage]
                        trailingIcon = {
                                       ^
                0 errors, 1 warnings
                """.trimIndent(),
            )
    }

    @Test
    fun whenIconButtonUsedInsteadOfDaxTextFieldTrailingIconThenWarning() {
        lint()
            .files(
                TestFiles.kt(
                    """
                    package com.example.test

                    import androidx.compose.foundation.text.input.TextFieldState
                    import androidx.compose.material3.Icon
                    import androidx.compose.material3.IconButton
                    import androidx.compose.runtime.Composable
                    import androidx.compose.ui.res.painterResource
                    import com.duckduckgo.common.ui.compose.textfield.DaxTextField

                    @Composable
                    fun TestScreen() {
                        DaxTextField(
                            state = TextFieldState(),
                            label = "Enter text",
                            trailingIcon = {
                                IconButton(onClick = {}) {
                                    Icon(
                                        painter = painterResource(0),
                                        contentDescription = "Copy"
                                    )
                                }
                            }
                        )
                    }
                    """.trimIndent(),
                ).indented(),
                composeStubs,
                textFieldStateStub,
                daxTextFieldStub,
                painterStub,
            )
            .allowCompilationErrors()
            .issues(INVALID_DAX_TEXT_FIELD_TRAILING_ICON_USAGE)
            .run()
            .expect(
                """
                src/com/example/test/test.kt:15: Warning: Use composables from DaxTextFieldTrailingIconScope instead of arbitrary composables 
                for the trailingIcon parameter to maintain design system consistency.

                Example:
                DaxTextField(
                    state = state,
                    trailingIcon = {
                        DaxTextFieldTrailingIcon(
                            painter = painterResource(R.drawable.ic_copy_24),
                            contentDescription = stringResource(R.string.icon_description)
                        )
                    }
                )

                This ensures consistent styling, spacing, and behavior across all text field icons in the app. [InvalidDaxTextFieldTrailingIconUsage]
                        trailingIcon = {
                                       ^
                0 errors, 1 warnings
                """.trimIndent(),
            )
    }

    @Test
    fun whenMultipleDaxTextFieldCallsWithMixedIconsThenWarningsForInvalidOnes() {
        lint()
            .files(
                TestFiles.kt(
                    """
                    package com.example.test

                    import androidx.compose.foundation.text.input.TextFieldState
                    import androidx.compose.material3.Icon
                    import androidx.compose.runtime.Composable
                    import androidx.compose.ui.res.painterResource
                    import com.duckduckgo.common.ui.compose.textfield.DaxTextField
                    import com.duckduckgo.common.ui.compose.textfield.DaxTextFieldTrailingIconScope.DaxTextFieldTrailingIcon

                    @Composable
                    fun TestScreen() {
                        DaxTextField(
                            state = TextFieldState(),
                            label = "Valid field",
                            trailingIcon = {
                                DaxTextFieldTrailingIcon(
                                    painter = painterResource(0),
                                    contentDescription = "Copy"
                                )
                            }
                        )

                        DaxTextField(
                            state = TextFieldState(),
                            label = "Invalid field",
                            trailingIcon = {
                                Icon(
                                    painter = painterResource(0),
                                    contentDescription = "Copy"
                                )
                            }
                        )

                        DaxTextField(
                            state = TextFieldState(),
                            label = "Also valid field",
                            trailingIcon = {
                                DaxTextFieldTrailingIcon(
                                    painter = painterResource(0),
                                    contentDescription = "Clear"
                                )
                            }
                        )
                    }
                    """.trimIndent(),
                ).indented(),
                composeStubs,
                textFieldStateStub,
                daxTextFieldStub,
                painterStub,
            )
            .allowCompilationErrors()
            .issues(INVALID_DAX_TEXT_FIELD_TRAILING_ICON_USAGE)
            .skipTestModes(TestMode.WHITESPACE, TestMode.REORDER_ARGUMENTS)
            .run()
            .expect(
                """
                src/com/example/test/test.kt:26: Warning: Use composables from DaxTextFieldTrailingIconScope instead of arbitrary composables 
                for the trailingIcon parameter to maintain design system consistency.

                Example:
                DaxTextField(
                    state = state,
                    trailingIcon = {
                        DaxTextFieldTrailingIcon(
                            painter = painterResource(R.drawable.ic_copy_24),
                            contentDescription = stringResource(R.string.icon_description)
                        )
                    }
                )

                This ensures consistent styling, spacing, and behavior across all text field icons in the app. [InvalidDaxTextFieldTrailingIconUsage]
                        trailingIcon = {
                                       ^
                0 errors, 1 warnings
                """.trimIndent(),
            )
    }

    @Test
    fun whenDaxTextFieldUsedWithoutTrailingIconParameterThenNoWarning() {
        lint()
            .files(
                TestFiles.kt(
                    """
                    package com.example.test

                    import androidx.compose.foundation.text.input.TextFieldState
                    import androidx.compose.runtime.Composable
                    import com.duckduckgo.common.ui.compose.textfield.DaxTextField

                    @Composable
                    fun TestScreen() {
                        DaxTextField(state = TextFieldState())
                    }
                    """.trimIndent(),
                ).indented(),
                composeStubs,
                textFieldStateStub,
                daxTextFieldStub,
            )
            .allowCompilationErrors()
            .issues(INVALID_DAX_TEXT_FIELD_TRAILING_ICON_USAGE)
            .run()
            .expectClean()
    }

    @Test
    fun whenArbitraryComposableUsedInTrailingIconThenWarning() {
        lint()
            .files(
                TestFiles.kt(
                    """
                    package com.example.test

                    import androidx.compose.foundation.text.input.TextFieldState
                    import androidx.compose.runtime.Composable
                    import androidx.compose.ui.Modifier
                    import com.duckduckgo.common.ui.compose.textfield.DaxTextField

                    @Composable
                    fun Scaffold(content: @Composable () -> Unit) {}

                    @Composable
                    fun TestScreen() {
                        DaxTextField(
                            state = TextFieldState(),
                            label = "Enter text",
                            trailingIcon = {
                                Scaffold {}
                            }
                        )
                    }
                    """.trimIndent(),
                ).indented(),
                composeStubs,
                textFieldStateStub,
                daxTextFieldStub,
            )
            .allowCompilationErrors()
            .issues(INVALID_DAX_TEXT_FIELD_TRAILING_ICON_USAGE)
            .run()
            .expect(
                """
                src/com/example/test/test.kt:16: Warning: Use composables from DaxTextFieldTrailingIconScope instead of arbitrary composables 
                for the trailingIcon parameter to maintain design system consistency.

                Example:
                DaxTextField(
                    state = state,
                    trailingIcon = {
                        DaxTextFieldTrailingIcon(
                            painter = painterResource(R.drawable.ic_copy_24),
                            contentDescription = stringResource(R.string.icon_description)
                        )
                    }
                )

                This ensures consistent styling, spacing, and behavior across all text field icons in the app. [InvalidDaxTextFieldTrailingIconUsage]
                        trailingIcon = {
                                       ^
                0 errors, 1 warnings
                """.trimIndent(),
            )
    }
}
