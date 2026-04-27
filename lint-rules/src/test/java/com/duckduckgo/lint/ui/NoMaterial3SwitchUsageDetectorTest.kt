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
import com.duckduckgo.lint.ui.NoMaterial3SwitchUsageDetector.Companion.NO_MATERIAL3_SWITCH_USAGE
import org.junit.Test

class NoMaterial3SwitchUsageDetectorTest {

    private val switchStub = kotlin(
        """
        package androidx.compose.material3

        fun Switch(
            checked: Boolean,
            onCheckedChange: ((Boolean) -> Unit)?,
        ) {}
        """.trimIndent(),
    ).indented()

    private val daxSwitchStub = kotlin(
        """
        package com.duckduckgo.common.ui.compose.switch

        fun DaxSwitch(
            checked: Boolean,
            onCheckedChange: ((Boolean) -> Unit)?,
        ) {}
        """.trimIndent(),
    ).indented()

    @Test
    fun whenMaterial3SwitchUsedThenError() {
        lint()
            .files(
                switchStub,
                kotlin(
                    """
                    package com.example.test

                    import androidx.compose.material3.Switch

                    fun MyScreen() {
                        Switch(checked = true, onCheckedChange = {})
                    }
                    """.trimIndent(),
                ).indented(),
            )
            .issues(NO_MATERIAL3_SWITCH_USAGE)
            .run()
            .expect(
                """
                src/com/example/test/test.kt:6: Error: Use DaxSwitch from the design system instead of the Material3 Switch composable to ensure consistent styling across the app. [NoMaterial3SwitchUsage]
                    Switch(checked = true, onCheckedChange = {})
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """.trimIndent(),
            )
    }

    @Test
    fun whenDaxSwitchUsedThenNoError() {
        lint()
            .files(
                daxSwitchStub,
                kotlin(
                    """
                    package com.example.test

                    import com.duckduckgo.common.ui.compose.switch.DaxSwitch

                    fun MyScreen() {
                        DaxSwitch(checked = true, onCheckedChange = {})
                    }
                    """.trimIndent(),
                ).indented(),
            )
            .issues(NO_MATERIAL3_SWITCH_USAGE)
            .run()
            .expectClean()
    }

    @Test
    fun whenNoSwitchUsedThenNoError() {
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
            .issues(NO_MATERIAL3_SWITCH_USAGE)
            .run()
            .expectClean()
    }
}