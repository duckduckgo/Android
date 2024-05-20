/*
 * Copyright (c) 2024 DuckDuckGo
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
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.checks.infrastructure.TestMode.Companion
import com.android.tools.lint.detector.api.Scope
import org.junit.Test
import java.util.EnumSet

class TestFunctionNameDetectorTest {

    @Test
    fun `name has no backticks - reports error`() {
        lint()
            .allowMissingSdk()
            .customScope(EnumSet.of(Scope.JAVA_FILE))
            .issues(TestFunctionNameDetector.TEST_FUNCTION_NAME)
            .files(
                JUNIT_STUB,
                kt(
                    """
                package com.example        

                import org.junit.Test
    
                @Test
                fun foo() {
                    println("hello")
                }
            """,
                ),
            )

            .run()
            .expectWarningCount(1)
            .expectContains("Test name should be in backticks")
    }

    @Test
    fun `name no parts - reports error`() {
        lint()
            .allowMissingSdk()
            .customScope(EnumSet.of(Scope.JAVA_FILE))
            .issues(TestFunctionNameDetector.TEST_FUNCTION_NAME)
            .files(
                JUNIT_STUB,
                kt(
                    """
                package com.example        

                import org.junit.Test
    
                @Test
                fun `foo bar`() {
                    println("hello")
                }
            """,
                ),
            )

            .run()
            .expectWarningCount(1)
            .expectContains("Test name should have two or three parts")
    }

    @Test
    fun `name not enough parts - reports error`() {
        lint()
            .allowMissingSdk()
            .customScope(EnumSet.of(Scope.JAVA_FILE))
            .issues(TestFunctionNameDetector.TEST_FUNCTION_NAME)
            .files(
                JUNIT_STUB,
                kt(
                    """
                package com.example        

                import org.junit.Test
    
                @Test
                fun `foo`() {
                    println("hello")
                }
            """,
                ),
            )

            .run()
            .expectWarningCount(1)
            .expectContains("Test name should have two or three parts")
    }

    @Test
    fun `name capitalization - reports error`() {
        lint()
            .allowMissingSdk()
            .customScope(EnumSet.of(Scope.JAVA_FILE))
            .issues(TestFunctionNameDetector.TEST_FUNCTION_NAME)
            .files(
                JUNIT_STUB,
                kt(
                    """
                package com.example        

                import org.junit.Test
    
                @Test
                fun `Foo - Bar`() {
                    println("hello")
                }
            """,
                ),
            )

            .run()
            .expectWarningCount(1)
            .expectContains("Test name parts should not be capitalized")
    }

    @Test
    fun `name parts - clean`() {
        lint()
            .allowMissingSdk()
            .customScope(EnumSet.of(Scope.JAVA_FILE))
            .issues(TestFunctionNameDetector.TEST_FUNCTION_NAME)
            .files(
                JUNIT_STUB,
                kt(
                    """
                package com.example        

                import org.junit.Test
    
                @Test
                fun `println - prints hello`() {
                    println("hello")
                }
            """,
                ),
            )

            .run()
            .expectClean()
    }

    @Test
    fun `parameterized test - clean`() {
        lint()
            .allowMissingSdk()
            .customScope(EnumSet.of(Scope.JAVA_FILE))
            .skipTestModes(TestMode.IMPORT_ALIAS) // We won't worry about import aliases here as it adds a lot of complexity
            .issues(TestFunctionNameDetector.TEST_FUNCTION_NAME)
            .files(
                JUNIT_STUB,
                RUNNER_STUB,
                kt(
                    """
                package com.example        

                import org.junit.Test
                import org.junit.runner.Parameterized
                import org.junit.runner.RunWith
                
                @RunWith(Parameterized::class)
                class Test {
                    @Test
                    fun `Foo - Bar`() {
                        println("hello")
                    }
                }
            """,
                ),
            )

            .run()
            .expectClean()
    }



    companion object {

        val JUNIT_STUB = kt(
            """
                package org.junit

                annotation class Test
            """,
        )

        val RUNNER_STUB = kt(
            """
                package org.junit.runner

                open class Runner

                class Parameterized: Runner

                annotation class RunWith(val value: KClass<out Runner>)
            """,
        )
    }
}
