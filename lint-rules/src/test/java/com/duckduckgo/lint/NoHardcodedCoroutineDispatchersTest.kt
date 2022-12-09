/*
 * Copyright (c) 2022 DuckDuckGo
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

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestFiles.kt
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import com.android.tools.lint.checks.infrastructure.TestMode
import com.duckduckgo.lint.NoHardcodedCoroutineDispatcherDetector.Companion.ERROR_DESCRIPTION
import com.duckduckgo.lint.NoHardcodedCoroutineDispatcherDetector.Companion.ERROR_ID
import com.duckduckgo.lint.NoHardcodedCoroutineDispatcherDetector.Companion.NO_HARCODED_COROUTINE_DISPATCHER
import org.junit.Test

@Suppress("UnstableApiUsage")
class NoHardcodedCoroutineDispatchersTest {

    @Test
    fun whenUsedInsideWithContextThenDetectedAsAViolation() {
        val callSite = """
              package com.duckduckgo.lint

                import kotlinx.coroutines.Dispatchers

                class Duck {
                    fun quack() {
                        withContext(Dispatchers.IO) { }                 
                    }
                }
            """

        assertLintError(listOf(kotlin(callSite).indented(), kotlin(classDefinitions).indented()))
    }

    @Test
    fun whenDispatchersIoThenDetectedAsAViolation() {
        val callSite = """
              package com.duckduckgo.lint

                import kotlinx.coroutines.Dispatchers

                class Duck {
                    fun quack() {
                        Dispatchers.IO  
                    }
                }
            """

        assertLintError(listOf(kt(callSite), kt(classDefinitions)))
    }

    @Test
    fun whenDispatchersMainThenDetectedAsAViolation() {
        val callSite = """
              package com.duckduckgo.lint

                import kotlinx.coroutines.Dispatchers

                class Duck {
                    fun quack() {
                        Dispatchers.Main  
                    }
                }
            """

        assertLintError(listOf(kt(callSite), kt(classDefinitions)))
    }

    @Test
    fun whenDispatchersDefaultThenDetectedAsAViolation() {
        val callSite = """
              package com.duckduckgo.lint

                import kotlinx.coroutines.Dispatchers

                class Duck {
                    fun quack() {
                        Dispatchers.Default  
                    }
                }
            """

        assertLintError(listOf(kt(callSite), kt(classDefinitions)))
    }


    @Test
    fun whenDispatchersUnconfinedThenDetectedAsAViolation() {
        val callSite = """
              package com.duckduckgo.lint

                import kotlinx.coroutines.Dispatchers

                class Duck {
                    fun quack() {
                        Dispatchers.Unconfined  
                    }
                }
            """

        assertLintError(listOf(kt(callSite), kt(classDefinitions)))
    }


    @Test
    fun whenRecommendedDispatcherProviderUsedThenNotDetectedAsAViolation() {
        val callSite = """
              package com.duckduckgo.lint

                import kotlinx.coroutines.Dispatchers

                class Duck(private val dispatcherProvider: DispatcherProvider) {
                    fun quack() {
                        withContext(dispatcherProvider.io()) { }                 
                    }
                }
            """

        assertNotLintError(listOf(kt(callSite), kt(classDefinitions)))
    }


    private val classDefinitions = """
            package kotlinx.coroutines

            object Dispatchers {

                @JvmStatic
                val IO: CoroutineScope = object: CoroutineScope(){}
                val Main: CoroutineScope = object: CoroutineScope(){}
                val Default: CoroutineScope = object: CoroutineScope(){}
                val Unconfined: CoroutineScope = object: CoroutineScope(){}
                val Test: CoroutineScope = object: CoroutineScope(){}
            }

           class CoroutineScope       

        """

    private fun assertLintError(files: List<TestFile>) {
        lint()
            .files(*files.toTypedArray())
            .issues(NO_HARCODED_COROUTINE_DISPATCHER)
            .testModes(TestMode.DEFAULT)
            .run()
            .expectContains(expectedError)
    }

    private fun assertNotLintError(files: List<TestFile>) {
        lint()
            .files(*files.toTypedArray())
            .issues(NO_HARCODED_COROUTINE_DISPATCHER)
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }

    companion object {
        private const val expectedError = "Error: $ERROR_DESCRIPTION [$ERROR_ID]"
    }
}
