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

package com.duckduckgo.lint

import com.android.tools.lint.checks.infrastructure.TestFiles.kt
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import com.duckduckgo.lint.ui.DaxMessageButtonRowDetector
import org.junit.Test

@Suppress("UnstableApiUsage")
class DaxMessageButtonRowDetectorTest {

    private val daxMessageStub = kt(
        """
        package com.duckduckgo.common.ui.compose.message

        interface DaxMessageButtonRowScope
        data class DaxAction(val text: String, val onClick: () -> Unit)

        fun DaxMessageButtonRowScope.RightAlignButtons(primary: DaxAction, secondary: DaxAction) {}
        fun DaxMessageButtonRowScope.CenterAlignedButtons(primary: DaxAction, secondary: DaxAction) {}
        fun DaxMessageButtonRowScope.FullWidthSingleButton(primary: DaxAction) {}
        fun DaxMessageButtonRowScope.SmallSingleButton(primary: DaxAction) {}

        fun DaxMessage(
            title: String,
            body: String,
            buttonRow: (DaxMessageButtonRowScope.() -> Unit)? = null,
        ) {}
        """,
    ).indented()

    @Test
    fun whenButtonRowUsesApprovedHelperThenSucceed() {
        lint()
            .files(
                daxMessageStub,
                kt(
                    """
                    package com.duckduckgo.example
                    import com.duckduckgo.common.ui.compose.message.DaxAction
                    import com.duckduckgo.common.ui.compose.message.DaxMessage
                    import com.duckduckgo.common.ui.compose.message.RightAlignButtons

                    fun render() {
                        DaxMessage(
                            title = "title",
                            body = "body",
                            buttonRow = {
                                RightAlignButtons(
                                    primary = DaxAction(text = "p", onClick = {}),
                                    secondary = DaxAction(text = "s", onClick = {}),
                                )
                            },
                        )
                    }
                    """,
                ).indented(),
            )
            .issues(DaxMessageButtonRowDetector.INVALID_BUTTON_ROW_CONTENT)
            .run()
            .expectClean()
    }

    @Test
    fun whenButtonRowContainsTextThenFail() {
        lint()
            .files(
                daxMessageStub,
                kt(
                    """
                    package com.duckduckgo.example
                    import com.duckduckgo.common.ui.compose.message.DaxMessage

                    fun Text(value: String) {}

                    fun render() {
                        DaxMessage(
                            title = "title",
                            body = "body",
                            buttonRow = {
                                Text("error")
                            },
                        )
                    }
                    """,
                ).indented(),
            )
            .issues(DaxMessageButtonRowDetector.INVALID_BUTTON_ROW_CONTENT)
            .run()
            .expect(
                """
                src/com/duckduckgo/example/test.kt:11: Error: Only DaxMessageButtonRowScope helpers (RightAlignButtons, CenterAlignedButtons, FullWidthSingleButton, SmallSingleButton) are allowed inside the buttonRow slot. [DaxMessageButtonRowContent]
                            Text("error")
                            ~~~~~~~~~~~~~
                1 errors, 0 warnings
                """.trimIndent(),
            )
    }

    @Test
    fun whenButtonRowContainsRowThenFail() {
        lint()
            .files(
                daxMessageStub,
                kt(
                    """
                    package com.duckduckgo.example
                    import com.duckduckgo.common.ui.compose.message.DaxMessage

                    fun Row(content: () -> Unit) {}

                    fun render() {
                        DaxMessage(
                            title = "title",
                            body = "body",
                            buttonRow = {
                                Row {}
                            },
                        )
                    }
                    """,
                ).indented(),
            )
            .issues(DaxMessageButtonRowDetector.INVALID_BUTTON_ROW_CONTENT)
            .run()
            .expect(
                """
                src/com/duckduckgo/example/test.kt:11: Error: Only DaxMessageButtonRowScope helpers (RightAlignButtons, CenterAlignedButtons, FullWidthSingleButton, SmallSingleButton) are allowed inside the buttonRow slot. [DaxMessageButtonRowContent]
                            Row {}
                            ~~~~~~
                1 errors, 0 warnings
                """.trimIndent(),
            )
    }

    @Test
    fun whenWrapperButtonRowContainsTextThenFail() {
        lint()
            .files(
                daxMessageStub,
                kt(
                    """
                    package com.duckduckgo.example
                    import com.duckduckgo.common.ui.compose.message.DaxMessageButtonRowScope

                    fun MyWrapper(actions: DaxMessageButtonRowScope.() -> Unit) {}
                    fun Text(value: String) {}

                    fun render() {
                        MyWrapper {
                            Text("oops")
                        }
                    }
                    """,
                ).indented(),
            )
            .issues(DaxMessageButtonRowDetector.INVALID_BUTTON_ROW_CONTENT)
            .run()
            .expect(
                """
                src/com/duckduckgo/example/test.kt:9: Error: Only DaxMessageButtonRowScope helpers (RightAlignButtons, CenterAlignedButtons, FullWidthSingleButton, SmallSingleButton) are allowed inside the buttonRow slot. [DaxMessageButtonRowContent]
                        Text("oops")
                        ~~~~~~~~~~~~
                1 errors, 0 warnings
                """.trimIndent(),
            )
    }

    @Test
    fun whenButtonRowOmittedThenSucceed() {
        lint()
            .files(
                daxMessageStub,
                kt(
                    """
                    package com.duckduckgo.example
                    import com.duckduckgo.common.ui.compose.message.DaxMessage

                    fun render() {
                        DaxMessage(
                            title = "title",
                            body = "body",
                        )
                    }
                    """,
                ).indented(),
            )
            .issues(DaxMessageButtonRowDetector.INVALID_BUTTON_ROW_CONTENT)
            .run()
            .expectClean()
    }
}
