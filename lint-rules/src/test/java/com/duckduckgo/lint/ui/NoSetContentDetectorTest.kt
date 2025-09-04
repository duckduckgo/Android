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
import com.duckduckgo.lint.ui.NoSetContentDetector.Companion.NO_SET_CONTENT_USAGE
import org.junit.Test

class NoSetContentDetectorTest {

    private val activityStub = TestFiles.kotlin(
        """
        package android.app
        
        open class Activity
        """.trimIndent()
    ).indented()

    private val composeStub = TestFiles.kotlin(
        """
        package androidx.activity.compose
        
        import androidx.compose.runtime.Composable
        import android.app.Activity
        
        fun Activity.setContent(content: @Composable () -> Unit) {
            // Compose Activity extension function
        }
        """.trimIndent()
    ).indented()

    @Test
    fun whenSetContentUsedInActivityThenError() {
        lint()
            .files(
                TestFiles.kt(
                    """
                    package com.example.test
                    
                    import android.app.Activity
                    import androidx.activity.compose.setContent
                    
                    class MainActivity : Activity() {
                        override fun onCreate(savedInstanceState: android.os.Bundle?) {
                            super.onCreate(savedInstanceState)
                            setContent {
                                // Compose content
                            }
                        }
                    }
                    """.trimIndent()
                ).indented(),
                activityStub,
                composeStub
            )
            .allowCompilationErrors()
            .issues(NO_SET_CONTENT_USAGE)
            .run()
            .expect(
                """
                src/com/example/test/MainActivity.kt:9: Error: Compose is not yet approved to be used in production. The setContent function should not be used in Activities until Compose usage is officially approved for the codebase. [NoSetContentUsage]
                        setContent {
                        ^
                1 errors, 0 warnings
                """.trimIndent()
            )
    }

    @Test
    fun whenSetContentUsedInAppCompatActivityThenError() {
        lint()
            .files(
                TestFiles.kt(
                    """
                    package com.example.test
                    
                    import androidx.appcompat.app.AppCompatActivity
                    import androidx.activity.compose.setContent
                    
                    class BrowserActivity : AppCompatActivity() {
                        override fun onCreate(savedInstanceState: android.os.Bundle?) {
                            super.onCreate(savedInstanceState)
                            
                            setContent {
                                // This should trigger the lint error
                            }
                        }
                    }
                    """.trimIndent()
                ).indented(),
                TestFiles.kotlin(
                    """
                    package androidx.appcompat.app
                    
                    import android.app.Activity
                    
                    open class AppCompatActivity : Activity()
                    """.trimIndent()
                ).indented(),
                activityStub,
                composeStub
            )
            .allowCompilationErrors()
            .issues(NO_SET_CONTENT_USAGE)
            .run()
            .expect(
                """
                src/com/example/test/BrowserActivity.kt:10: Error: Compose is not yet approved to be used in production. The setContent function should not be used in Activities until Compose usage is officially approved for the codebase. [NoSetContentUsage]
                        setContent {
                        ^
                1 errors, 0 warnings
                """.trimIndent()
            )
    }

    @Test
    fun whenSetContentUsedInNonActivityClassThenNoError() {
        lint()
            .files(
                TestFiles.kt(
                    """
                    package com.example.test
                    
                    import androidx.compose.runtime.Composable
                    
                    class RegularClass {
                        fun setContent(content: @Composable () -> Unit) {
                            // This is not Activity.setContent, so it's allowed
                        }
                        
                        fun someMethod() {
                            setContent {
                                // This should not trigger lint error
                            }
                        }
                    }
                    """.trimIndent()
                ).indented(),
                activityStub,
                composeStub
            )
            .allowCompilationErrors()
            .issues(NO_SET_CONTENT_USAGE)
            .run()
            .expectClean()
    }

    @Test
    fun whenActivityDoesNotUseSetContentThenNoError() {
        lint()
            .files(
                TestFiles.kt(
                    """
                    package com.example.test
                    
                    import android.app.Activity
                    
                    class MainActivity : Activity() {
                        override fun onCreate(savedInstanceState: android.os.Bundle?) {
                            super.onCreate(savedInstanceState)
                            setContentView(R.layout.activity_main)
                        }
                        
                        private fun setContentView(layoutId: Int) {
                            // Traditional setContentView is fine
                        }
                    }
                    
                    object R {
                        object layout {
                            const val activity_main = 1
                        }
                    }
                    """.trimIndent()
                ).indented(),
                activityStub
            )
            .allowCompilationErrors()
            .issues(NO_SET_CONTENT_USAGE)
            .run()
            .expectClean()
    }

    @Test
    fun whenMultipleSetContentCallsInActivityThenMultipleErrors() {
        lint()
            .files(
                TestFiles.kt(
                    """
                    package com.example.test
                    
                    import android.app.Activity
                    import androidx.activity.compose.setContent
                    
                    class MainActivity : Activity() {
                        override fun onCreate(savedInstanceState: android.os.Bundle?) {
                            super.onCreate(savedInstanceState)
                            
                            if (someCondition()) {
                                setContent {
                                    // First violation
                                }
                            } else {
                                setContent {
                                    // Second violation
                                }
                            }
                        }
                        
                        private fun someCondition() = true
                    }
                    """.trimIndent()
                ).indented(),
                activityStub,
                composeStub
            )
            .allowCompilationErrors()
            .issues(NO_SET_CONTENT_USAGE)
            .run()
            .expect(
                """
                src/com/example/test/MainActivity.kt:11: Error: Compose is not yet approved to be used in production. The setContent function should not be used in Activities until Compose usage is officially approved for the codebase. [NoSetContentUsage]
                            setContent {
                            ^
                src/com/example/test/MainActivity.kt:15: Error: Compose is not yet approved to be used in production. The setContent function should not be used in Activities until Compose usage is officially approved for the codebase. [NoSetContentUsage]
                            setContent {
                            ^
                2 errors, 0 warnings
                """.trimIndent()
            )
    }

    @Test
    fun whenSetContentUsedInCustomActivitySubclassThenError() {
        lint()
            .files(
                TestFiles.kt(
                    """
                    package com.example.test
                    
                    import android.app.Activity
                    import androidx.activity.compose.setContent
                    
                    open class BaseActivity : Activity()
                    
                    class CustomActivity : BaseActivity() {
                        fun setupUI() {
                            setContent {
                                // Should detect this in custom Activity subclass
                            }
                        }
                    }
                    """.trimIndent()
                ).indented(),
                activityStub,
                composeStub
            )
            .allowCompilationErrors()
            .issues(NO_SET_CONTENT_USAGE)
            .run()
            .expect(
                """
                src/com/example/test/BaseActivity.kt:10: Error: Compose is not yet approved to be used in production. The setContent function should not be used in Activities until Compose usage is officially approved for the codebase. [NoSetContentUsage]
                        setContent {
                        ^
                1 errors, 0 warnings
                """.trimIndent()
            )
    }
}