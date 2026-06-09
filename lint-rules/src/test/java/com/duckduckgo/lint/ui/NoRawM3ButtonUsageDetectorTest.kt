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
import com.duckduckgo.lint.ui.NoRawM3ButtonUsageDetector.Companion.NO_RAW_M3_BUTTON_USAGE
import org.junit.Test

class NoRawM3ButtonUsageDetectorTest {

    private val m3ButtonStub = TestFiles.kotlin(
        """
        package androidx.compose.material3

        import androidx.compose.runtime.Composable

        @Composable
        fun Button(onClick: () -> Unit, content: @Composable () -> Unit) {}

        @Composable
        fun OutlinedButton(onClick: () -> Unit, content: @Composable () -> Unit) {}

        @Composable
        fun TextButton(onClick: () -> Unit, content: @Composable () -> Unit) {}

        @Composable
        fun IconButton(onClick: () -> Unit, content: @Composable () -> Unit) {}
        """.trimIndent()
    ).indented()

    private val composableStub = TestFiles.kotlin(
        """
        package androidx.compose.runtime

        annotation class Composable
        """.trimIndent()
    ).indented()

    @Test
    fun whenRawM3ButtonUsedThenError() {
        lint()
            .files(
                TestFiles.kt(
                    """
                    package com.example.feature

                    import androidx.compose.material3.Button
                    import androidx.compose.runtime.Composable

                    @Composable
                    fun MyScreen() {
                        Button(onClick = {}) {}
                    }
                    """.trimIndent()
                ).indented(),
                m3ButtonStub,
                composableStub,
            )
            .allowCompilationErrors()
            .issues(NO_RAW_M3_BUTTON_USAGE)
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun whenRawM3OutlinedButtonUsedThenError() {
        lint()
            .files(
                TestFiles.kt(
                    """
                    package com.example.feature

                    import androidx.compose.material3.OutlinedButton
                    import androidx.compose.runtime.Composable

                    @Composable
                    fun MyScreen() {
                        OutlinedButton(onClick = {}) {}
                    }
                    """.trimIndent()
                ).indented(),
                m3ButtonStub,
                composableStub,
            )
            .allowCompilationErrors()
            .issues(NO_RAW_M3_BUTTON_USAGE)
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun whenRawM3IconButtonUsedThenError() {
        lint()
            .files(
                TestFiles.kt(
                    """
                    package com.example.feature

                    import androidx.compose.material3.IconButton
                    import androidx.compose.runtime.Composable

                    @Composable
                    fun MyScreen() {
                        IconButton(onClick = {}) {}
                    }
                    """.trimIndent()
                ).indented(),
                m3ButtonStub,
                composableStub,
            )
            .allowCompilationErrors()
            .issues(NO_RAW_M3_BUTTON_USAGE)
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun whenDaxButtonUsedThenNoError() {
        lint()
            .files(
                TestFiles.kt(
                    """
                    package com.example.feature

                    import androidx.compose.runtime.Composable

                    @Composable
                    fun DaxPrimaryButton(text: String, onClick: () -> Unit) {}

                    @Composable
                    fun MyScreen() {
                        DaxPrimaryButton(text = "OK", onClick = {})
                    }
                    """.trimIndent()
                ).indented(),
                composableStub,
            )
            .issues(NO_RAW_M3_BUTTON_USAGE)
            .run()
            .expectClean()
    }

    @Test
    fun whenNonM3ButtonFunctionNamedButtonThenNoError() {
        lint()
            .files(
                TestFiles.kt(
                    """
                    package com.example.feature

                    import androidx.compose.runtime.Composable

                    @Composable
                    fun Button(text: String) {}

                    @Composable
                    fun MyScreen() {
                        Button(text = "OK")
                    }
                    """.trimIndent()
                ).indented(),
                composableStub,
            )
            .issues(NO_RAW_M3_BUTTON_USAGE)
            .run()
            .expectClean()
    }
}
