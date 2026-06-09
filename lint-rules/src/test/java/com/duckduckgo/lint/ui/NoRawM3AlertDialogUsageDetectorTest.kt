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
import com.android.tools.lint.detector.api.TextFormat
import com.duckduckgo.lint.ui.NoRawM3AlertDialogUsageDetector.Companion.NO_RAW_M3_ALERT_DIALOG_USAGE
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoRawM3AlertDialogUsageDetectorTest {

    private val m3AlertDialogStub = TestFiles.kotlin(
        """
        package androidx.compose.material3

        import androidx.compose.runtime.Composable

        @Composable
        fun AlertDialog(
            onDismissRequest: () -> Unit,
            confirmButton: @Composable () -> Unit,
            text: @Composable () -> Unit,
        ) {}

        @Composable
        fun BasicAlertDialog(
            onDismissRequest: () -> Unit,
            content: @Composable () -> Unit,
        ) {}
        """.trimIndent()
    ).indented()

    private val composableStub = TestFiles.kotlin(
        """
        package androidx.compose.runtime

        annotation class Composable
        """.trimIndent()
    ).indented()

    @Test
    fun whenRawM3AlertDialogUsedThenError() {
        lint()
            .allowMissingSdk()
            .files(
                TestFiles.kt(
                    """
                    package com.example.feature

                    import androidx.compose.material3.AlertDialog
                    import androidx.compose.runtime.Composable

                    @Composable
                    fun MyScreen() {
                        AlertDialog(onDismissRequest = {}, confirmButton = {}, text = {})
                    }
                    """.trimIndent()
                ).indented(),
                m3AlertDialogStub,
                composableStub,
            )
            .allowCompilationErrors()
            .issues(NO_RAW_M3_ALERT_DIALOG_USAGE)
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun whenRawM3BasicAlertDialogUsedThenError() {
        lint()
            .allowMissingSdk()
            .files(
                TestFiles.kt(
                    """
                    package com.example.feature

                    import androidx.compose.material3.BasicAlertDialog
                    import androidx.compose.runtime.Composable

                    @Composable
                    fun MyScreen() {
                        BasicAlertDialog(onDismissRequest = {}, content = {})
                    }
                    """.trimIndent()
                ).indented(),
                m3AlertDialogStub,
                composableStub,
            )
            .allowCompilationErrors()
            .issues(NO_RAW_M3_ALERT_DIALOG_USAGE)
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun whenDaxAlertDialogUsedThenNoError() {
        lint()
            .allowMissingSdk()
            .files(
                TestFiles.kt(
                    """
                    package com.example.feature

                    import androidx.compose.runtime.Composable

                    @Composable
                    fun DaxAlertDialog(onDismissRequest: () -> Unit, title: String) {}

                    @Composable
                    fun MyScreen() {
                        DaxAlertDialog(onDismissRequest = {}, title = "Hi")
                    }
                    """.trimIndent()
                ).indented(),
                composableStub,
            )
            .issues(NO_RAW_M3_ALERT_DIALOG_USAGE)
            .run()
            .expectClean()
    }

    @Test
    fun whenNonM3FunctionNamedAlertDialogThenNoError() {
        lint()
            .allowMissingSdk()
            .files(
                TestFiles.kt(
                    """
                    package com.example.feature

                    import androidx.compose.runtime.Composable

                    @Composable
                    fun AlertDialog(title: String) {}

                    @Composable
                    fun MyScreen() {
                        AlertDialog(title = "Hi")
                    }
                    """.trimIndent()
                ).indented(),
                composableStub,
            )
            .issues(NO_RAW_M3_ALERT_DIALOG_USAGE)
            .run()
            .expectClean()
    }

    @Test
    fun whenIssueTextReadThenItReferencesPublicDesignSystemDialogApis() {
        val briefDescription = NO_RAW_M3_ALERT_DIALOG_USAGE.getBriefDescription(TextFormat.RAW)
        val explanation = NO_RAW_M3_ALERT_DIALOG_USAGE.getExplanation(TextFormat.RAW)
        val standaloneTextAlertDialogApi = Regex("\\bTextAlertDialog\\b")

        assertTrue(briefDescription.contains("DaxTextAlertDialog"))
        assertTrue(explanation.contains("DaxTextAlertDialog"))
        assertFalse(standaloneTextAlertDialogApi.containsMatchIn(briefDescription))
        assertFalse(standaloneTextAlertDialogApi.containsMatchIn(explanation))
    }
}
