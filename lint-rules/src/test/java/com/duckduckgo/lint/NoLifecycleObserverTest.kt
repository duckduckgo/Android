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
class NoLifecycleObserverTest {
    @Test
    fun whenLifecycleObserverExtendedThenFailWithError() {
        lint()
            .files(kt("""
              package com.duckduckgo.lint
    
                class LifecycleObserver

                class Duck : LifecycleObserver() {
                    fun quack() {                    
                    }
                }
            """).indented())
            .issues(NoLifecycleObserverDetector.NO_LIFECYCLE_OBSERVER_ISSUE)
            .run()
            .expect("""
                src/com/duckduckgo/lint/LifecycleObserver.kt:5: Error: LifecycleObserver should not be directly extended [NoLifecycleObserver]
                  class Duck : LifecycleObserver() {
                        ~~~~
                1 errors, 0 warnings
            """.trimMargin())
    }

    @Test
    fun whenDefaultLifecycleObserverExtendedThenFail() {
        lint()
            .files(kt("""
              package com.duckduckgo.lint
    
                class DefaultLifecycleObserver

                class Duck : DefaultLifecycleObserver() {
                    fun quack() {                    
                    }
                }
            """).indented())
            .allowCompilationErrors()
            .issues(NoLifecycleObserverDetector.NO_LIFECYCLE_OBSERVER_ISSUE)
            .run()
            .expect("""
                src/com/duckduckgo/lint/DefaultLifecycleObserver.kt:5: Error: LifecycleObserver should not be directly extended [NoLifecycleObserver]
                  class Duck : DefaultLifecycleObserver() {
                        ~~~~
                1 errors, 0 warnings
            """.trimMargin())
    }

    @Test
    fun whenLifecycleObserverNotFoundThenSucceed() {
        lint()
            .files(kt("""
              package com.duckduckgo.lint
    
                class Duck {
                    fun quack() {                    
                    }
                }
            """).indented())
            .allowCompilationErrors()
            .issues(NoLifecycleObserverDetector.NO_LIFECYCLE_OBSERVER_ISSUE)
            .run()
            .expectClean()
    }
}
