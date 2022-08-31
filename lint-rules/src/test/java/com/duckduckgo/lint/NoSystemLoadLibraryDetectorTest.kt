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

import com.android.tools.lint.checks.infrastructure.TestFiles.kt
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.Test

@Suppress("UnstableApiUsage")
class NoSystemLoadLibraryDetectorTest {
    @Test
    fun whenSystemDotLibraryFoundThenFailWithError() {
        lint()
            .files(kt("""
              package com.duckduckgo.lint
    
                class Duck {
                    fun quack() {
                        System.loadLibrary()
                    }
                }
            """).indented())
            .issues(NoSystemLoadLibraryDetector.NO_SYSTEM_LOAD_LIBRARY)
            .run()
            .expect("""
               src/com/duckduckgo/lint/Duck.kt:5: Error: System.loadLibrary() should not be used. [NoSystemLoadLibrary]
                         System.loadLibrary()
                                ~~~~~~~~~~~
               1 errors, 0 warnings
            """.trimMargin())
    }

    @Test
    fun whenSystemDotLibraryNotFoundThenFailWithError() {
        lint()
            .files(kt("""
              package com.duckduckgo.lint
    
                fun loadLibrary() {}

                class Duck {
                    fun quack() {
                        loadLibrary()
                    }
                }
            """).indented())
            .allowCompilationErrors()
            .issues(NoSystemLoadLibraryDetector.NO_SYSTEM_LOAD_LIBRARY)
            .run()
            .expectClean()
    }
}
