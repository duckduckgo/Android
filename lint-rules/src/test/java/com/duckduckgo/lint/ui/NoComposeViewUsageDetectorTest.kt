/*
 * Copyright (c) 2025 DuckDuckGo
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
import com.duckduckgo.lint.ui.NoComposeViewUsageDetector.Companion.NO_COMPOSE_VIEW_USAGE
import org.junit.Test

class NoComposeViewUsageDetectorTest {

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

    @Test
    fun whenComposeViewUsedInXmlThenError() {
        lint()
            .files(
                TestFiles.xml(
                    "res/layout/activity_main.xml",
                    """
                    <LinearLayout
                        xmlns:android="http://schemas.android.com/apk/res/android"
                        android:layout_width="match_parent"
                        android:layout_height="match_parent"
                        android:orientation="vertical">

                        <androidx.compose.ui.platform.ComposeView
                            android:layout_width="match_parent"
                            android:layout_height="match_parent"/>

                    </LinearLayout>
                    """.trimIndent()
                ).indented(),
                composeViewStub
            )
            .allowCompilationErrors()
            .issues(NO_COMPOSE_VIEW_USAGE)
            .run()
            .expect(
                """
                res/layout/activity_main.xml:7: Error: Compose is not yet approved to be used in production. ComposeView should not be used in XML layouts or custom views until Compose usage is officially approved for the codebase. [NoComposeViewUsage]
                    <androidx.compose.ui.platform.ComposeView
                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """.trimIndent()
            )
    }

    @Test
    fun whenComposeViewNotUsedInXmlThenNoError() {
        lint()
            .files(
                TestFiles.xml(
                    "res/layout/activity_main.xml",
                    """
                    <LinearLayout
                        xmlns:android="http://schemas.android.com/apk/res/android"
                        android:layout_width="match_parent"
                        android:layout_height="match_parent"
                        android:orientation="vertical">

                        <TextView
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:text="Hello World!" />

                    </LinearLayout>
                    """.trimIndent()
                ).indented()
            )
            .issues(NO_COMPOSE_VIEW_USAGE)
            .run()
            .expectClean()
    }

    @Test
    fun whenComposeViewConstructorUsedInKotlinThenError() {
        lint()
            .files(
                TestFiles.kt(
                    """
                    package com.example.test
                    
                    import android.content.Context
                    import androidx.compose.ui.platform.ComposeView
                    
                    class MyActivity {
                        fun createComposeView(context: Context) {
                            val composeView = ComposeView(context)
                            // Do something with composeView
                        }
                    }
                    """.trimIndent()
                ).indented(),
                composeViewStub
            )
            .allowCompilationErrors()
            .issues(NO_COMPOSE_VIEW_USAGE)
            .run()
            .expect(
                """
                src/com/example/test/MyActivity.kt:8: Error: Compose is not yet approved to be used in production. ComposeView should not be used in XML layouts or custom views until Compose usage is officially approved for the codebase. [NoComposeViewUsage]
                        val composeView = ComposeView(context)
                                          ~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """.trimIndent()
            )
    }

    @Test
    fun whenComposeViewWithAttributesConstructorUsedInKotlinThenError() {
        lint()
            .files(
                TestFiles.kt(
                    """
                    package com.example.test
                    
                    import android.content.Context
                    import android.util.AttributeSet
                    import androidx.compose.ui.platform.ComposeView
                    
                    class CustomView(context: Context, attrs: AttributeSet?) : ComposeView(context, attrs) {
                        // Custom implementation
                    }
                    """.trimIndent()
                ).indented(),
                composeViewStub
            )
            .allowCompilationErrors()
            .issues(NO_COMPOSE_VIEW_USAGE)
            .run()
            .expect(
                """
                src/com/example/test/CustomView.kt:7: Error: Compose is not yet approved to be used in production. ComposeView should not be used in XML layouts or custom views until Compose usage is officially approved for the codebase. [NoComposeViewUsage]
                class CustomView(context: Context, attrs: AttributeSet?) : ComposeView(context, attrs) {
                                                                           ~~~~~~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """.trimIndent()
            )
    }

    @Test
    fun whenRegularViewUsedInKotlinThenNoError() {
        lint()
            .files(
                TestFiles.kt(
                    """
                    package com.example.test
                    
                    import android.content.Context
                    import android.widget.LinearLayout
                    import android.widget.TextView
                    
                    class MyActivity {
                        fun createViews(context: Context) {
                            val layout = LinearLayout(context)
                            val textView = TextView(context)
                            // Regular Android Views are fine
                        }
                    }
                    """.trimIndent()
                ).indented()
            )
            .issues(NO_COMPOSE_VIEW_USAGE)
            .run()
            .expectClean()
    }

    @Test
    fun whenMultipleComposeViewsUsedThenMultipleErrors() {
        lint()
            .files(
                TestFiles.xml(
                    "res/layout/multiple_compose.xml",
                    """
                    <LinearLayout
                        xmlns:android="http://schemas.android.com/apk/res/android"
                        android:layout_width="match_parent"
                        android:layout_height="match_parent"
                        android:orientation="vertical">

                        <androidx.compose.ui.platform.ComposeView
                            android:id="@+id/composeView1"
                            android:layout_width="match_parent"
                            android:layout_height="0dp"
                            android:layout_weight="1"/>

                        <androidx.compose.ui.platform.ComposeView
                            android:id="@+id/composeView2"
                            android:layout_width="match_parent"
                            android:layout_height="0dp"
                            android:layout_weight="1"/>

                    </LinearLayout>
                    """.trimIndent()
                ).indented(),
                composeViewStub
            )
            .allowCompilationErrors()
            .issues(NO_COMPOSE_VIEW_USAGE)
            .run()
            .expect(
                """
                res/layout/multiple_compose.xml:7: Error: Compose is not yet approved to be used in production. ComposeView should not be used in XML layouts or custom views until Compose usage is officially approved for the codebase. [NoComposeViewUsage]
                    <androidx.compose.ui.platform.ComposeView
                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                res/layout/multiple_compose.xml:13: Error: Compose is not yet approved to be used in production. ComposeView should not be used in XML layouts or custom views until Compose usage is officially approved for the codebase. [NoComposeViewUsage]
                    <androidx.compose.ui.platform.ComposeView
                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                2 errors, 0 warnings
                """.trimIndent()
            )
    }
}
