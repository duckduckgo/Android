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
import com.duckduckgo.lint.ui.NoMaterial3DividerUsageDetector.Companion.NO_MATERIAL3_HORIZONTAL_DIVIDER_USAGE
import com.duckduckgo.lint.ui.NoMaterial3DividerUsageDetector.Companion.NO_MATERIAL3_VERTICAL_DIVIDER_USAGE
import org.junit.Test

class NoMaterial3DividerUsageDetectorTest {

    private val material3DividerStub = kotlin(
        """
        package androidx.compose.material3

        fun HorizontalDivider() {}

        fun VerticalDivider() {}
        """.trimIndent(),
    ).indented()

    private val daxDividerStub = kotlin(
        """
        package com.duckduckgo.common.ui.compose.divider

        fun DaxHorizontalDivider() {}

        fun DaxVerticalDivider() {}
        """.trimIndent(),
    ).indented()

    @Test
    fun whenMaterial3HorizontalDividerUsedThenError() {
        lint()
            .files(
                material3DividerStub,
                kotlin(
                    """
                    package com.example.test

                    import androidx.compose.material3.HorizontalDivider

                    fun MyScreen() {
                        HorizontalDivider()
                    }
                    """.trimIndent(),
                ).indented(),
            )
            .issues(NO_MATERIAL3_HORIZONTAL_DIVIDER_USAGE)
            .run()
            .expect(
                """
                src/com/example/test/test.kt:6: Error: Use DaxHorizontalDivider from the design system instead of the Material3 HorizontalDivider composable to ensure consistent styling across the app. [NoMaterial3HorizontalDividerUsage]
                    HorizontalDivider()
                    ~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """.trimIndent(),
            )
    }

    @Test
    fun whenMaterial3VerticalDividerUsedThenError() {
        lint()
            .files(
                material3DividerStub,
                kotlin(
                    """
                    package com.example.test

                    import androidx.compose.material3.VerticalDivider

                    fun MyScreen() {
                        VerticalDivider()
                    }
                    """.trimIndent(),
                ).indented(),
            )
            .issues(NO_MATERIAL3_VERTICAL_DIVIDER_USAGE)
            .run()
            .expect(
                """
                src/com/example/test/test.kt:6: Error: Use DaxVerticalDivider from the design system instead of the Material3 VerticalDivider composable to ensure consistent styling across the app. [NoMaterial3VerticalDividerUsage]
                    VerticalDivider()
                    ~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """.trimIndent(),
            )
    }

    @Test
    fun whenDaxHorizontalDividerUsedThenNoError() {
        lint()
            .files(
                daxDividerStub,
                kotlin(
                    """
                    package com.example.test

                    import com.duckduckgo.common.ui.compose.divider.DaxHorizontalDivider

                    fun MyScreen() {
                        DaxHorizontalDivider()
                    }
                    """.trimIndent(),
                ).indented(),
            )
            .issues(NO_MATERIAL3_HORIZONTAL_DIVIDER_USAGE, NO_MATERIAL3_VERTICAL_DIVIDER_USAGE)
            .run()
            .expectClean()
    }

    @Test
    fun whenDaxVerticalDividerUsedThenNoError() {
        lint()
            .files(
                daxDividerStub,
                kotlin(
                    """
                    package com.example.test

                    import com.duckduckgo.common.ui.compose.divider.DaxVerticalDivider

                    fun MyScreen() {
                        DaxVerticalDivider()
                    }
                    """.trimIndent(),
                ).indented(),
            )
            .issues(NO_MATERIAL3_HORIZONTAL_DIVIDER_USAGE, NO_MATERIAL3_VERTICAL_DIVIDER_USAGE)
            .run()
            .expectClean()
    }

    @Test
    fun whenNoDividerUsedThenNoError() {
        lint()
            .files(
                kotlin(
                    """
                    package com.example.test

                    fun MyScreen() {
                        val visible = true
                    }
                    """.trimIndent(),
                ).indented(),
            )
            .issues(NO_MATERIAL3_HORIZONTAL_DIVIDER_USAGE, NO_MATERIAL3_VERTICAL_DIVIDER_USAGE)
            .run()
            .expectClean()
    }
}
