/*
 * Copyright (c) 2023 DuckDuckGo
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
import org.junit.Test

@Suppress("UnstableApiUsage")
class NoRobolectricTestRunnerDetectorTest {
    @Test
    fun whenRobolectricTestRunnerFoundThenFailWithError() {
        lint()
            .files(kt(
                """
                package com.duckduckgo.lint
                import java.lang.Class

                class Runner
    
                annotation class RunWith(val value: KClass<out Runner?>)
        
                class RobolectricTestRunner
    
                @RunWith(RobolectricTestRunner::class)
                class Duck {
                    fun quack() {                    
                    }
                }
            """
            ).indented())
            .issues(NoRobolectricTestRunnerDetector.NO_ROBOLECTRIC_TEST_RUNNER_ISSUE)
            .run()
            .expect("""
                src/com/duckduckgo/lint/Runner.kt:10: Error: The RobolectricTestRunner parameter must not be used in the RunWith annotation. [NoRobolectricTestRunnerAnnotation]
                @RunWith(RobolectricTestRunner::class)
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
            """.trimMargin())
    }

    @Test
    fun whenRobolectricTestRunnerNotFoundThenSucceed() {
        lint()
            .files(kt("""
                package com.duckduckgo.lint
                import java.lang.Class

                class Runner
    
                annotation class RunWith(val value: KClass<out Runner?>)
        
                class AndroidJUnit4
    
                @RunWith(AndroidJUnit4::class)
                class Duck {
                    fun quack() {                    
                    }
                }
            """).indented())
            .allowCompilationErrors()
            .issues(NoRobolectricTestRunnerDetector.NO_ROBOLECTRIC_TEST_RUNNER_ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun whenParameterizedRobolectricTestRunnerNotFoundThenSucceed() {
        lint()
            .files(kt("""
                package com.duckduckgo.lint
                import java.lang.Class

                class Runner
    
                annotation class RunWith(val value: KClass<out Runner?>)
        
                class ParameterizedRobolectricTestRunner
    
                @RunWith(ParameterizedRobolectricTestRunner::class)
                class Duck {
                    fun quack() {                    
                    }
                }
            """).indented())
            .allowCompilationErrors()
            .issues(NoRobolectricTestRunnerDetector.NO_ROBOLECTRIC_TEST_RUNNER_ISSUE)
            .run()
            .expectClean()
    }
}
