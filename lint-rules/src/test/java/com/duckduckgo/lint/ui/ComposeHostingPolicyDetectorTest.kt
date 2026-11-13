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

import com.android.tools.lint.checks.infrastructure.ProjectDescription
import com.android.tools.lint.checks.infrastructure.TestFiles
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import com.duckduckgo.lint.ui.NoComposeViewUsageDetector.Companion.NO_COMPOSE_VIEW_USAGE
import com.duckduckgo.lint.ui.NoSetContentDetector.Companion.NO_SET_CONTENT_USAGE
import org.junit.Test

class ComposeHostingPolicyDetectorTest {

    private val composeViewStub = TestFiles.kotlin(
        """
        package androidx.compose.ui.platform
        import android.content.Context
        import android.util.AttributeSet
        import android.view.View
        class ComposeView @JvmOverloads constructor(
            context: Context,
            attrs: AttributeSet? = null,
            defStyleAttr: Int = 0
        ) : View(context, attrs, defStyleAttr)
        """.trimIndent()
    ).indented()

    private val setContentStub = TestFiles.kotlin(
        """
        package androidx.activity.compose
        import android.app.Activity
        import androidx.compose.runtime.Composable
        fun Activity.setContent(content: @Composable () -> Unit) {}
        """.trimIndent()
    ).indented()

    private val composableStub = TestFiles.kotlin(
        """
        package androidx.compose.runtime
        annotation class Composable
        """.trimIndent()
    ).indented()

    private fun composeViewUsage(path: String) = TestFiles.kotlin(
        path,
        """
        package com.duckduckgo.sample
        import android.content.Context
        import androidx.compose.ui.platform.ComposeView
        fun create(context: Context) = ComposeView(context)
        """.trimIndent()
    ).indented()

    private fun setContentUsage(path: String) = TestFiles.kotlin(
        path,
        """
        package com.duckduckgo.sample
        import android.app.Activity
        import androidx.activity.compose.setContent
        class SampleActivity : Activity() {
            override fun onCreate(savedInstanceState: android.os.Bundle?) {
                super.onCreate(savedInstanceState)
                setContent { }
            }
        }
        """.trimIndent()
    ).indented()

    private fun project(name: String, vararg files: com.android.tools.lint.checks.infrastructure.TestFile): ProjectDescription =
        ProjectDescription().name(name).files(*files)

    @Test
    fun whenComposeViewUsedInInternalModuleThenNoError() {
        lint()
            .projects(project("sync-internal", composeViewUsage("src/main/java/com/duckduckgo/sample/Host.kt"), composeViewStub))
            .allowCompilationErrors()
            .issues(NO_COMPOSE_VIEW_USAGE)
            .run()
            .expectClean()
    }

    @Test
    fun whenSetContentUsedInInternalModuleThenNoError() {
        lint()
            .projects(project("sync-internal", setContentUsage("src/main/java/com/duckduckgo/sample/SampleActivity.kt"), setContentStub, composableStub))
            .allowCompilationErrors()
            .issues(NO_SET_CONTENT_USAGE)
            .run()
            .expectClean()
    }

    @Test
    fun whenComposeViewUsedInAppInternalSourceSetThenNoError() {
        lint()
            .projects(project("app", composeViewUsage("src/internal/java/com/duckduckgo/sample/Host.kt"), composeViewStub))
            .allowCompilationErrors()
            .issues(NO_COMPOSE_VIEW_USAGE)
            .run()
            .expectClean()
    }

    @Test
    fun whenSetContentUsedInAppInternalSourceSetThenNoError() {
        lint()
            .projects(project("app", setContentUsage("src/internal/java/com/duckduckgo/sample/SampleActivity.kt"), setContentStub, composableStub))
            .allowCompilationErrors()
            .issues(NO_SET_CONTENT_USAGE)
            .run()
            .expectClean()
    }

    @Test
    fun whenComposeViewUsedInAppMainSourceSetThenError() {
        lint()
            .projects(project("app", composeViewUsage("src/main/java/com/duckduckgo/sample/Host.kt"), composeViewStub))
            .allowCompilationErrors()
            .issues(NO_COMPOSE_VIEW_USAGE)
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun whenComposeViewUsedInFeatureImplModuleThenError() {
        lint()
            .projects(project("subscriptions-impl", composeViewUsage("src/main/java/com/duckduckgo/sample/Host.kt"), composeViewStub))
            .allowCompilationErrors()
            .issues(NO_COMPOSE_VIEW_USAGE)
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun whenSetContentUsedInFeatureImplModuleThenError() {
        lint()
            .projects(project("subscriptions-impl", setContentUsage("src/main/java/com/duckduckgo/sample/SampleActivity.kt"), setContentStub, composableStub))
            .allowCompilationErrors()
            .issues(NO_SET_CONTENT_USAGE)
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun whenComposeViewUsedInDesignSystemModuleThenNoError() {
        lint()
            .projects(project("design-system", composeViewUsage("src/main/java/com/duckduckgo/sample/Host.kt"), composeViewStub))
            .allowCompilationErrors()
            .issues(NO_COMPOSE_VIEW_USAGE)
            .run()
            .expectClean()
    }

    @Test
    fun whenProductionReferenceIsListedInLintXmlThenNoError() {
        val lintXml = TestFiles.xml(
            "lint.xml",
            """
            <lint>
                <issue id="NoComposeViewUsage">
                    <ignore path="src/main/java/com/duckduckgo/sample/Host.kt" />
                </issue>
                <issue id="NoSetContentUsage">
                    <ignore path="src/main/java/com/duckduckgo/sample/SampleActivity.kt" />
                </issue>
            </lint>
            """.trimIndent()
        ).indented()
        lint()
            .projects(
                project(
                    "subscriptions-impl",
                    composeViewUsage("src/main/java/com/duckduckgo/sample/Host.kt"),
                    setContentUsage("src/main/java/com/duckduckgo/sample/SampleActivity.kt"),
                    composeViewStub,
                    setContentStub,
                    composableStub,
                    lintXml,
                ),
            )
            .allowCompilationErrors()
            .issues(NO_COMPOSE_VIEW_USAGE, NO_SET_CONTENT_USAGE)
            .run()
            .expectClean()
    }

    @Test
    fun whenLintXmlIgnoresOnlyOneFileThenOtherFileStillErrors() {
        val lintXml = TestFiles.xml(
            "lint.xml",
            """
            <lint>
                <issue id="NoComposeViewUsage">
                    <ignore path="src/main/java/com/duckduckgo/sample/Host.kt" />
                </issue>
            </lint>
            """.trimIndent()
        ).indented()
        lint()
            .projects(
                project(
                    "subscriptions-impl",
                    composeViewUsage("src/main/java/com/duckduckgo/sample/Host.kt"),
                    composeViewUsage("src/main/java/com/duckduckgo/sample/Other.kt"),
                    composeViewStub,
                    lintXml,
                ),
            )
            .allowCompilationErrors()
            .issues(NO_COMPOSE_VIEW_USAGE)
            .run()
            .expectErrorCount(1)
    }
}
