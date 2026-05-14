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
import com.duckduckgo.lint.ui.NoRawM3SurfaceUsageDetector.Companion.NO_RAW_M3_SURFACE_USAGE
import org.junit.Test

class NoRawM3SurfaceUsageDetectorTest {

    private val m3SurfaceStub = TestFiles.kotlin(
        """
        package androidx.compose.material3

        import androidx.compose.runtime.Composable

        @Composable
        fun Surface(content: @Composable () -> Unit) {}
        """.trimIndent()
    ).indented()

    private val composableStub = TestFiles.kotlin(
        """
        package androidx.compose.runtime

        annotation class Composable
        """.trimIndent()
    ).indented()

    @Test
    fun whenRawM3SurfaceUsedThenError() {
        lint()
            .files(
                TestFiles.kt(
                    """
                    package com.example.feature

                    import androidx.compose.material3.Surface
                    import androidx.compose.runtime.Composable

                    @Composable
                    fun MyScreen() {
                        Surface {}
                    }
                    """.trimIndent()
                ).indented(),
                m3SurfaceStub,
                composableStub,
            )
            .allowCompilationErrors()
            .issues(NO_RAW_M3_SURFACE_USAGE)
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun whenDaxSurfaceUsedThenNoError() {
        lint()
            .files(
                TestFiles.kt(
                    """
                    package com.example.feature

                    import androidx.compose.runtime.Composable

                    @Composable
                    fun DaxSurface(content: @Composable () -> Unit) {}

                    @Composable
                    fun MyScreen() {
                        DaxSurface {}
                    }
                    """.trimIndent()
                ).indented(),
                composableStub,
            )
            .issues(NO_RAW_M3_SURFACE_USAGE)
            .run()
            .expectClean()
    }

    @Test
    fun whenNonM3SurfaceFunctionNamedSurfaceThenNoError() {
        lint()
            .files(
                TestFiles.kt(
                    """
                    package com.example.feature

                    import androidx.compose.runtime.Composable

                    @Composable
                    fun Surface(content: @Composable () -> Unit) {}

                    @Composable
                    fun MyScreen() {
                        Surface {}
                    }
                    """.trimIndent()
                ).indented(),
                composableStub,
            )
            .issues(NO_RAW_M3_SURFACE_USAGE)
            .run()
            .expectClean()
    }
}
