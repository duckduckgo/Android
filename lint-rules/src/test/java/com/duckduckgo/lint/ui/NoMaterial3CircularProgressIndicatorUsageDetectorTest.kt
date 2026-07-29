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
import com.duckduckgo.lint.ui.NoMaterial3CircularProgressIndicatorUsageDetector.Companion.NO_MATERIAL3_CIRCULAR_PROGRESS_INDICATOR_USAGE
import org.junit.Test

class NoMaterial3CircularProgressIndicatorUsageDetectorTest {

    private val circularProgressIndicatorStub = kotlin(
        """
        package androidx.compose.material3

        fun CircularProgressIndicator() {}
        """.trimIndent(),
    ).indented()

    private val daxProgressSpinnerStub = kotlin(
        """
        package com.duckduckgo.common.ui.compose.progress

        fun DaxProgressSpinner() {}
        """.trimIndent(),
    ).indented()

    @Test
    fun whenMaterial3CircularProgressIndicatorUsedThenError() {
        lint()
            .files(
                circularProgressIndicatorStub,
                kotlin(
                    """
                    package com.example.test

                    import androidx.compose.material3.CircularProgressIndicator

                    fun MyScreen() {
                        CircularProgressIndicator()
                    }
                    """.trimIndent(),
                ).indented(),
            )
            .issues(NO_MATERIAL3_CIRCULAR_PROGRESS_INDICATOR_USAGE)
            .run()
            .expect(
                """
                src/com/example/test/test.kt:6: Error: Use DaxProgressSpinner from the design system instead of the Material3 CircularProgressIndicator composable to ensure consistent styling across the app. [NoMaterial3CircularProgressIndicatorUsage]
                    CircularProgressIndicator()
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """.trimIndent(),
            )
    }

    @Test
    fun whenDaxProgressSpinnerUsedThenNoError() {
        lint()
            .files(
                daxProgressSpinnerStub,
                kotlin(
                    """
                    package com.example.test

                    import com.duckduckgo.common.ui.compose.progress.DaxProgressSpinner

                    fun MyScreen() {
                        DaxProgressSpinner()
                    }
                    """.trimIndent(),
                ).indented(),
            )
            .issues(NO_MATERIAL3_CIRCULAR_PROGRESS_INDICATOR_USAGE)
            .run()
            .expectClean()
    }

    @Test
    fun whenNoCircularProgressIndicatorUsedThenNoError() {
        lint()
            .files(
                kotlin(
                    """
                    package com.example.test

                    fun MyScreen() {
                        val loading = true
                    }
                    """.trimIndent(),
                ).indented(),
            )
            .issues(NO_MATERIAL3_CIRCULAR_PROGRESS_INDICATOR_USAGE)
            .run()
            .expectClean()
    }
}
