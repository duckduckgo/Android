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
import com.duckduckgo.lint.ui.NoMaterial3RadioButtonUsageDetector.Companion.NO_MATERIAL3_RADIO_BUTTON_USAGE
import org.junit.Test

class NoMaterial3RadioButtonUsageDetectorTest {

    private val radioButtonStub = kotlin(
        """
        package androidx.compose.material3

        fun RadioButton(
            selected: Boolean,
            onClick: (() -> Unit)?,
        ) {}
        """.trimIndent(),
    ).indented()

    private val daxRadioButtonStub = kotlin(
        """
        package com.duckduckgo.common.ui.compose.radiobutton

        fun DaxRadioButton(
            selected: Boolean,
            onClick: (() -> Unit)?,
        ) {}
        """.trimIndent(),
    ).indented()

    @Test
    fun whenMaterial3RadioButtonUsedThenError() {
        lint()
            .files(
                radioButtonStub,
                kotlin(
                    """
                    package com.example.test

                    import androidx.compose.material3.RadioButton

                    fun MyScreen() {
                        RadioButton(selected = true, onClick = {})
                    }
                    """.trimIndent(),
                ).indented(),
            )
            .issues(NO_MATERIAL3_RADIO_BUTTON_USAGE)
            .run()
            .expect(
                """
                src/com/example/test/test.kt:6: Error: Use DaxRadioButton from the design system instead of the Material3 RadioButton composable to ensure consistent styling across the app. [NoMaterial3RadioButtonUsage]
                    RadioButton(selected = true, onClick = {})
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """.trimIndent(),
            )
    }

    @Test
    fun whenDaxRadioButtonUsedThenNoError() {
        lint()
            .files(
                daxRadioButtonStub,
                kotlin(
                    """
                    package com.example.test

                    import com.duckduckgo.common.ui.compose.radiobutton.DaxRadioButton

                    fun MyScreen() {
                        DaxRadioButton(selected = true, onClick = {})
                    }
                    """.trimIndent(),
                ).indented(),
            )
            .issues(NO_MATERIAL3_RADIO_BUTTON_USAGE)
            .run()
            .expectClean()
    }

    @Test
    fun whenNoRadioButtonUsedThenNoError() {
        lint()
            .files(
                kotlin(
                    """
                    package com.example.test

                    fun MyScreen() {
                        val selected = true
                    }
                    """.trimIndent(),
                ).indented(),
            )
            .issues(NO_MATERIAL3_RADIO_BUTTON_USAGE)
            .run()
            .expectClean()
    }
}
