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
import com.duckduckgo.lint.ui.NoMaterial3TopAppBarUsageDetector.Companion.NO_MATERIAL3_TOP_APP_BAR_USAGE
import org.junit.Test

class NoMaterial3TopAppBarUsageDetectorTest {

    private val topAppBarStub = kotlin(
        """
        package androidx.compose.material3

        fun TopAppBar(title: () -> Unit) {}

        fun CenterAlignedTopAppBar(title: () -> Unit) {}

        fun MediumTopAppBar(title: () -> Unit) {}

        fun LargeTopAppBar(title: () -> Unit) {}
        """.trimIndent(),
    ).indented()

    private val daxTopAppBarStub = kotlin(
        """
        package com.duckduckgo.common.ui.compose.appbars

        fun DaxTopAppBar(title: String) {}

        fun DaxSearchTopAppBar(title: String) {}
        """.trimIndent(),
    ).indented()

    @Test
    fun whenMaterial3TopAppBarUsedThenError() {
        lint()
            .files(
                topAppBarStub,
                kotlin(
                    """
                    package com.example.test

                    import androidx.compose.material3.TopAppBar

                    fun MyScreen() {
                        TopAppBar(title = {})
                    }
                    """.trimIndent(),
                ).indented(),
            )
            .issues(NO_MATERIAL3_TOP_APP_BAR_USAGE)
            .run()
            .expect(
                """
                src/com/example/test/test.kt:6: Error: Use DaxTopAppBar or DaxSearchTopAppBar from the design system instead of the Material3 TopAppBar composable to ensure consistent styling across the app. [NoMaterial3TopAppBarUsage]
                    TopAppBar(title = {})
                    ~~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """.trimIndent(),
            )
    }

    @Test
    fun whenMaterial3CenterAlignedTopAppBarUsedThenError() {
        lint()
            .files(
                topAppBarStub,
                kotlin(
                    """
                    package com.example.test

                    import androidx.compose.material3.CenterAlignedTopAppBar

                    fun MyScreen() {
                        CenterAlignedTopAppBar(title = {})
                    }
                    """.trimIndent(),
                ).indented(),
            )
            .issues(NO_MATERIAL3_TOP_APP_BAR_USAGE)
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun whenMaterial3MediumTopAppBarUsedThenError() {
        lint()
            .files(
                topAppBarStub,
                kotlin(
                    """
                    package com.example.test

                    import androidx.compose.material3.MediumTopAppBar

                    fun MyScreen() {
                        MediumTopAppBar(title = {})
                    }
                    """.trimIndent(),
                ).indented(),
            )
            .issues(NO_MATERIAL3_TOP_APP_BAR_USAGE)
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun whenMaterial3LargeTopAppBarUsedThenError() {
        lint()
            .files(
                topAppBarStub,
                kotlin(
                    """
                    package com.example.test

                    import androidx.compose.material3.LargeTopAppBar

                    fun MyScreen() {
                        LargeTopAppBar(title = {})
                    }
                    """.trimIndent(),
                ).indented(),
            )
            .issues(NO_MATERIAL3_TOP_APP_BAR_USAGE)
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun whenNonMaterial3TopAppBarUsedThenNoError() {
        lint()
            .files(
                kotlin(
                    """
                    package com.example.widgets

                    fun TopAppBar(title: () -> Unit) {}
                    """.trimIndent(),
                ).indented(),
                kotlin(
                    """
                    package com.example.test

                    import com.example.widgets.TopAppBar

                    fun MyScreen() {
                        TopAppBar(title = {})
                    }
                    """.trimIndent(),
                ).indented(),
            )
            .issues(NO_MATERIAL3_TOP_APP_BAR_USAGE)
            .run()
            .expectClean()
    }

    @Test
    fun whenDaxTopAppBarUsedThenNoError() {
        lint()
            .files(
                daxTopAppBarStub,
                kotlin(
                    """
                    package com.example.test

                    import com.duckduckgo.common.ui.compose.appbars.DaxTopAppBar

                    fun MyScreen() {
                        DaxTopAppBar(title = "Title")
                    }
                    """.trimIndent(),
                ).indented(),
            )
            .issues(NO_MATERIAL3_TOP_APP_BAR_USAGE)
            .run()
            .expectClean()
    }

    @Test
    fun whenDaxSearchTopAppBarUsedThenNoError() {
        lint()
            .files(
                daxTopAppBarStub,
                kotlin(
                    """
                    package com.example.test

                    import com.duckduckgo.common.ui.compose.appbars.DaxSearchTopAppBar

                    fun MyScreen() {
                        DaxSearchTopAppBar(title = "Title")
                    }
                    """.trimIndent(),
                ).indented(),
            )
            .issues(NO_MATERIAL3_TOP_APP_BAR_USAGE)
            .run()
            .expectClean()
    }

    @Test
    fun whenNoTopAppBarUsedThenNoError() {
        lint()
            .files(
                kotlin(
                    """
                    package com.example.test

                    fun MyScreen() {
                        val title = "Title"
                    }
                    """.trimIndent(),
                ).indented(),
            )
            .issues(NO_MATERIAL3_TOP_APP_BAR_USAGE)
            .run()
            .expectClean()
    }
}
