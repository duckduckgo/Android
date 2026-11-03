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
import com.duckduckgo.lint.ui.NoRawM3SliderUsageDetector.Companion.NO_RAW_M3_SLIDER_USAGE
import org.junit.Test

class NoRawM3SliderUsageDetectorTest {

    private val m3SliderStub = TestFiles.kotlin(
        """
        package androidx.compose.material3

        import androidx.compose.runtime.Composable

        @Composable
        fun Slider(value: Float, onValueChange: (Float) -> Unit) {}
        """.trimIndent()
    ).indented()

    private val composableStub = TestFiles.kotlin(
        """
        package androidx.compose.runtime

        annotation class Composable
        """.trimIndent()
    ).indented()

    @Test
    fun whenRawM3SliderUsedThenError() {
        lint()
            .files(
                TestFiles.kt(
                    """
                    package com.example.feature

                    import androidx.compose.material3.Slider
                    import androidx.compose.runtime.Composable

                    @Composable
                    fun MyScreen() {
                        Slider(value = 0f, onValueChange = {})
                    }
                    """.trimIndent()
                ).indented(),
                m3SliderStub,
                composableStub,
            )
            .allowCompilationErrors()
            .issues(NO_RAW_M3_SLIDER_USAGE)
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun whenDaxSliderUsedThenNoError() {
        lint()
            .files(
                TestFiles.kt(
                    """
                    package com.example.feature

                    import androidx.compose.runtime.Composable

                    @Composable
                    fun DaxSlider(value: Float, onValueChange: (Float) -> Unit) {}

                    @Composable
                    fun MyScreen() {
                        DaxSlider(value = 0f, onValueChange = {})
                    }
                    """.trimIndent()
                ).indented(),
                composableStub,
            )
            .issues(NO_RAW_M3_SLIDER_USAGE)
            .run()
            .expectClean()
    }

    @Test
    fun whenNonM3FunctionNamedSliderThenNoError() {
        lint()
            .files(
                TestFiles.kt(
                    """
                    package com.example.feature

                    import androidx.compose.runtime.Composable

                    @Composable
                    fun Slider(value: Float, onValueChange: (Float) -> Unit) {}

                    @Composable
                    fun MyScreen() {
                        Slider(value = 0f, onValueChange = {})
                    }
                    """.trimIndent()
                ).indented(),
                composableStub,
            )
            .issues(NO_RAW_M3_SLIDER_USAGE)
            .run()
            .expectClean()
    }
}
