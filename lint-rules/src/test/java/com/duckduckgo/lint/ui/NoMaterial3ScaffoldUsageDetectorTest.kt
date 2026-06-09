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
import com.duckduckgo.lint.ui.NoMaterial3ScaffoldUsageDetector.Companion.NO_MATERIAL3_SCAFFOLD_USAGE
import org.junit.Test

class NoMaterial3ScaffoldUsageDetectorTest {

    private val scaffoldStub = kotlin(
        """
        package androidx.compose.material3

        fun Scaffold(
            content: () -> Unit,
        ) {}
        """.trimIndent(),
    ).indented()

    private val daxScaffoldStub = kotlin(
        """
        package com.duckduckgo.common.ui.compose.layout

        fun DaxScaffold(
            content: () -> Unit,
        ) {}
        """.trimIndent(),
    ).indented()

    @Test
    fun whenMaterial3ScaffoldUsedThenError() {
        lint()
            .files(
                scaffoldStub,
                kotlin(
                    """
                    package com.example.test

                    import androidx.compose.material3.Scaffold

                    fun MyScreen() {
                        Scaffold(content = {})
                    }
                    """.trimIndent(),
                ).indented(),
            )
            .issues(NO_MATERIAL3_SCAFFOLD_USAGE)
            .run()
            .expect(
                """
                src/com/example/test/test.kt:6: Error: Use DaxScaffold from the design system instead of the Material3 Scaffold composable to ensure consistent styling across the app. [NoMaterial3ScaffoldUsage]
                    Scaffold(content = {})
                    ~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """.trimIndent(),
            )
    }

    @Test
    fun whenDaxScaffoldUsedThenNoError() {
        lint()
            .files(
                daxScaffoldStub,
                kotlin(
                    """
                    package com.example.test

                    import com.duckduckgo.common.ui.compose.layout.DaxScaffold

                    fun MyScreen() {
                        DaxScaffold(content = {})
                    }
                    """.trimIndent(),
                ).indented(),
            )
            .issues(NO_MATERIAL3_SCAFFOLD_USAGE)
            .run()
            .expectClean()
    }

    @Test
    fun whenNoScaffoldUsedThenNoError() {
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
            .issues(NO_MATERIAL3_SCAFFOLD_USAGE)
            .run()
            .expectClean()
    }
}
