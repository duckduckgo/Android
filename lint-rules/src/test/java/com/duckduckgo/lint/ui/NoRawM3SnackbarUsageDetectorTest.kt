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
import com.duckduckgo.lint.ui.NoRawM3SnackbarUsageDetector.Companion.NO_RAW_M3_SNACKBAR_USAGE
import org.junit.Test

class NoRawM3SnackbarUsageDetectorTest {

    private val m3SnackbarStub = TestFiles.kotlin(
        """
        package androidx.compose.material3

        import androidx.compose.runtime.Composable

        @Composable
        fun Snackbar(content: @Composable () -> Unit) {}
        """.trimIndent()
    ).indented()

    private val composableStub = TestFiles.kotlin(
        """
        package androidx.compose.runtime

        annotation class Composable
        """.trimIndent()
    ).indented()

    @Test
    fun whenRawM3SnackbarUsedThenError() {
        lint()
            .files(
                TestFiles.kt(
                    """
                    package com.example.feature

                    import androidx.compose.material3.Snackbar
                    import androidx.compose.runtime.Composable

                    @Composable
                    fun MyScreen() {
                        Snackbar {}
                    }
                    """.trimIndent()
                ).indented(),
                m3SnackbarStub,
                composableStub,
            )
            .allowCompilationErrors()
            .issues(NO_RAW_M3_SNACKBAR_USAGE)
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun whenDaxSnackbarUsedThenNoError() {
        lint()
            .files(
                TestFiles.kt(
                    """
                    package com.example.feature

                    import androidx.compose.runtime.Composable

                    @Composable
                    fun DaxSnackbar(message: String) {}

                    @Composable
                    fun MyScreen() {
                        DaxSnackbar("Hello")
                    }
                    """.trimIndent()
                ).indented(),
                composableStub,
            )
            .issues(NO_RAW_M3_SNACKBAR_USAGE)
            .run()
            .expectClean()
    }

    @Test
    fun whenNonM3FunctionNamedSnackbarThenNoError() {
        lint()
            .files(
                TestFiles.kt(
                    """
                    package com.example.feature

                    import androidx.compose.runtime.Composable

                    @Composable
                    fun Snackbar(content: @Composable () -> Unit) {}

                    @Composable
                    fun MyScreen() {
                        Snackbar {}
                    }
                    """.trimIndent()
                ).indented(),
                composableStub,
            )
            .issues(NO_RAW_M3_SNACKBAR_USAGE)
            .run()
            .expectClean()
    }
}
