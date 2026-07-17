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
import org.junit.Test

@Suppress("UnstableApiUsage")
class DenyListedApiDetectorTest {

    private val browserModeStubs = arrayOf(
        kt(
            """
            package kotlinx.coroutines.flow
            interface StateFlow<out T> { val value: T }
            """,
        ).indented(),
        kt(
            """
            package com.duckduckgo.browsermode.api
            import kotlinx.coroutines.flow.StateFlow
            enum class BrowserMode { REGULAR, FIRE }
            interface BrowserModeStateHolder {
                val currentMode: StateFlow<BrowserMode>
                fun switchTo(mode: BrowserMode)
            }
            """,
        ).indented(),
    )

    @Test
    fun whenCurrentModeIsReadThenFailWithError() {
        lint()
            .files(
                *browserModeStubs,
                kt(
                    """
                    package com.duckduckgo.test
                    import com.duckduckgo.browsermode.api.BrowserModeStateHolder
                    class Consumer(private val holder: BrowserModeStateHolder) {
                        fun mode() = holder.currentMode.value
                    }
                    """,
                ).indented(),
            )
            .issues(DenyListedApiDetector.ISSUE)
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun whenSwitchToIsCalledThenSucceed() {
        lint()
            .files(
                *browserModeStubs,
                kt(
                    """
                    package com.duckduckgo.test
                    import com.duckduckgo.browsermode.api.BrowserMode
                    import com.duckduckgo.browsermode.api.BrowserModeStateHolder
                    class Switcher(private val holder: BrowserModeStateHolder) {
                        fun switch() = holder.switchTo(BrowserMode.FIRE)
                    }
                    """,
                ).indented(),
            )
            .issues(DenyListedApiDetector.ISSUE)
            .run()
            .expectClean()
    }
}
