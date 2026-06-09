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
import com.duckduckgo.lint.ui.NoMaterial3CheckboxUsageDetector.Companion.NO_MATERIAL3_CHECKBOX_USAGE
import org.junit.Test

class NoMaterial3CheckboxUsageDetectorTest {

    private val checkboxStub = kotlin(
        """
        package androidx.compose.material3

        fun Checkbox(
            checked: Boolean,
            onCheckedChange: ((Boolean) -> Unit)?,
        ) {}
        """.trimIndent(),
    ).indented()

    private val daxCheckboxStub = kotlin(
        """
        package com.duckduckgo.common.ui.compose.checkbox

        fun DaxCheckbox(
            checked: Boolean,
            onCheckedChange: ((Boolean) -> Unit)?,
        ) {}
        """.trimIndent(),
    ).indented()

    @Test
    fun whenMaterial3CheckboxUsedThenError() {
        lint()
            .files(
                checkboxStub,
                kotlin(
                    """
                    package com.example.test

                    import androidx.compose.material3.Checkbox

                    fun MyScreen() {
                        Checkbox(checked = true, onCheckedChange = {})
                    }
                    """.trimIndent(),
                ).indented(),
            )
            .issues(NO_MATERIAL3_CHECKBOX_USAGE)
            .run()
            .expect(
                """
                src/com/example/test/test.kt:6: Error: Use DaxCheckbox from the design system instead of the Material3 Checkbox composable to ensure consistent styling across the app. [NoMaterial3CheckboxUsage]
                    Checkbox(checked = true, onCheckedChange = {})
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """.trimIndent(),
            )
    }

    @Test
    fun whenDaxCheckboxUsedThenNoError() {
        lint()
            .files(
                daxCheckboxStub,
                kotlin(
                    """
                    package com.example.test

                    import com.duckduckgo.common.ui.compose.checkbox.DaxCheckbox

                    fun MyScreen() {
                        DaxCheckbox(checked = true, onCheckedChange = {})
                    }
                    """.trimIndent(),
                ).indented(),
            )
            .issues(NO_MATERIAL3_CHECKBOX_USAGE)
            .run()
            .expectClean()
    }

    @Test
    fun whenNoCheckboxUsedThenNoError() {
        lint()
            .files(
                kotlin(
                    """
                    package com.example.test

                    fun MyScreen() {
                        val checked = true
                    }
                    """.trimIndent(),
                ).indented(),
            )
            .issues(NO_MATERIAL3_CHECKBOX_USAGE)
            .run()
            .expectClean()
    }
}
