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

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFiles.kt
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import com.android.tools.lint.checks.infrastructure.TestMode
import org.junit.Test

class NoFlowTerminalOperatorWithoutValueDetectorTest {
    @Test
    fun `first() on Flow is flagged`() {
        lint()
            .files(
                *flowStubs(),
                kt(
                    """
                    import kotlinx.coroutines.flow.Flow
                    import kotlinx.coroutines.flow.first

                    suspend fun read(flow: Flow<Boolean>): Boolean {
                        return flow.first()
                    }
                    """,
                ).indented(),
            )
            .issues(DenyListedApiDetector.ISSUE)
            .testModes(TestMode.DEFAULT)
            .run()
            .expectContains("first() will throw if flow is empty")
    }

    @Test
    fun `last() on Flow is flagged`() {
        lint()
            .files(
                *flowStubs(),
                kt(
                    """
                    import kotlinx.coroutines.flow.Flow
                    import kotlinx.coroutines.flow.last

                    suspend fun read(flow: Flow<Boolean>): Boolean {
                        return flow.last()
                    }
                    """,
                ).indented(),
            )
            .issues(DenyListedApiDetector.ISSUE)
            .testModes(TestMode.DEFAULT)
            .run()
            .expectContains("last() will throw if flow is empty")
    }

    @Test
    fun `first() on StateFlow is allowed`() {
        lint()
            .files(
                *flowStubs(),
                kt(
                    """
                    import kotlinx.coroutines.flow.StateFlow
                    import kotlinx.coroutines.flow.first

                    suspend fun read(state: StateFlow<Boolean>): Boolean {
                        return state.first()
                    }
                    """,
                ).indented(),
            )
            .issues(DenyListedApiDetector.ISSUE)
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }

    @Test
    fun `first() on MutableStateFlow is allowed`() {
        lint()
            .files(
                *flowStubs(),
                kt(
                    """
                    import kotlinx.coroutines.flow.MutableStateFlow
                    import kotlinx.coroutines.flow.first

                    suspend fun read(state: MutableStateFlow<Boolean>): Boolean {
                        return state.first()
                    }
                    """,
                ).indented(),
            )
            .issues(DenyListedApiDetector.ISSUE)
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }

    @Test
    fun `last() on StateFlow is allowed`() {
        lint()
            .files(
                *flowStubs(),
                kt(
                    """
                    import kotlinx.coroutines.flow.StateFlow
                    import kotlinx.coroutines.flow.last

                    suspend fun read(state: StateFlow<Boolean>): Boolean {
                        return state.last()
                    }
                    """,
                ).indented(),
            )
            .issues(DenyListedApiDetector.ISSUE)
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }

    @Test
    fun `last() on MutableStateFlow is allowed`() {
        lint()
            .files(
                *flowStubs(),
                kt(
                    """
                    import kotlinx.coroutines.flow.MutableStateFlow
                    import kotlinx.coroutines.flow.last

                    suspend fun read(state: MutableStateFlow<Boolean>): Boolean {
                        return state.last()
                    }
                    """,
                ).indented(),
            )
            .issues(DenyListedApiDetector.ISSUE)
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }

    private fun flowStubs(): Array<TestFile> = arrayOf(
        kt("""
            package kotlinx.coroutines.flow
    
            interface Flow<out T>
            interface StateFlow<out T> : Flow<T>
            interface MutableStateFlow<T> : StateFlow<T>
        """).indented(),
        // At runtime `Flow.first()` / `Flow.last()` live in the compiler-generated multifile facade
        // class `kotlinx.coroutines.flow.FlowKt__ReduceKt`. This is what DenyListedApiDetector
        // matches against, so we force the facade name with `@file:JvmName("FlowKt__ReduceKt")`.
        kt("""
            @file:JvmName("FlowKt__ReduceKt")
            package kotlinx.coroutines.flow
    
            suspend fun <T> Flow<T>.first(): T = TODO()
            suspend fun <T> Flow<T>.last(): T = TODO()
        """).indented(),
    )
}
