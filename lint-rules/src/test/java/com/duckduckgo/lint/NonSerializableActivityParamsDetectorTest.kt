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

class NonSerializableActivityParamsDetectorTest {
    @Test
    fun `primitives, strings, enums and serializable types are allowed`() {
        lint()
            .files(
                *activityParamsStubs(),
                kt(
                    """
                    package com.duckduckgo.example
                    import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams

                    data class Params(
                        val id: Int,
                        val enabled: Boolean?,
                        val name: String?,
                        val source: Source,
                        val data: SerializableData,
                        val sealed: SerializableSealed,
                        val ids: IntArray,
                    ) : ActivityParams
                    """,
                ).indented(),
            )
            .issues(NonSerializableActivityParamsDetector.NON_SERIALIZABLE_ACTIVITY_PARAMS)
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }

    @Test
    fun `collections of serializable elements are allowed`() {
        lint()
            .files(
                *activityParamsStubs(),
                kt(
                    """
                    package com.duckduckgo.example
                    import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams

                    data class Params(
                        val sources: List<Source>,
                        val names: Set<String>,
                        val data: Map<String, SerializableData>,
                        val nested: List<List<Int>>,
                    ) : ActivityParams
                    """,
                ).indented(),
            )
            .issues(NonSerializableActivityParamsDetector.NON_SERIALIZABLE_ACTIVITY_PARAMS)
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }

    @Test
    fun `non-serializable property is flagged`() {
        lint()
            .files(
                *activityParamsStubs(),
                kt(
                    """
                    package com.duckduckgo.example
                    import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams

                    data class Params(val data: PlainData) : ActivityParams
                    """,
                ).indented(),
            )
            .issues(NonSerializableActivityParamsDetector.NON_SERIALIZABLE_ACTIVITY_PARAMS)
            .testModes(TestMode.DEFAULT)
            .run()
            .expectContains("data of type PlainData is not Serializable")
    }

    @Test
    fun `collections of non-serializable elements are flagged`() {
        lint()
            .files(
                *activityParamsStubs(),
                kt(
                    """
                    package com.duckduckgo.example
                    import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams

                    data class Params(
                        val list: List<PlainData>,
                        val map: Map<String, PlainData>,
                    ) : ActivityParams
                    """,
                ).indented(),
            )
            .issues(NonSerializableActivityParamsDetector.NON_SERIALIZABLE_ACTIVITY_PARAMS)
            .testModes(TestMode.DEFAULT)
            .run()
            .expectErrorCount(2)
    }

    @Test
    fun `lambda property is flagged`() {
        lint()
            .files(
                *activityParamsStubs(),
                kt(
                    """
                    package com.duckduckgo.example
                    import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams

                    data class Params(val onDone: () -> Unit) : ActivityParams
                    """,
                ).indented(),
            )
            .issues(NonSerializableActivityParamsDetector.NON_SERIALIZABLE_ACTIVITY_PARAMS)
            .testModes(TestMode.DEFAULT)
            .run()
            .expectContains("onDone of type Function0<Unit> is not Serializable")
    }

    @Test
    fun `params implementing ActivityParams through a sealed interface are checked`() {
        lint()
            .files(
                *activityParamsStubs(),
                kt(
                    """
                    package com.duckduckgo.example
                    import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams

                    sealed interface Screens : ActivityParams {
                        data object Main : Screens
                        data class Detail(val data: PlainData) : Screens
                    }
                    """,
                ).indented(),
            )
            .issues(NonSerializableActivityParamsDetector.NON_SERIALIZABLE_ACTIVITY_PARAMS)
            .testModes(TestMode.DEFAULT)
            .run()
            .expectContains("data of type PlainData is not Serializable")
    }

    @Test
    fun `transient non-serializable property is allowed`() {
        lint()
            .files(
                *activityParamsStubs(),
                kt(
                    """
                    package com.duckduckgo.example
                    import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams

                    data class Params(val id: String) : ActivityParams {
                        @Transient val cache: PlainData? = null
                    }
                    """,
                ).indented(),
            )
            .issues(NonSerializableActivityParamsDetector.NON_SERIALIZABLE_ACTIVITY_PARAMS)
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }

    @Test
    fun `class that is not ActivityParams is allowed`() {
        lint()
            .files(
                *activityParamsStubs(),
                kt(
                    """
                    package com.duckduckgo.example

                    data class NotParams(val data: PlainData)
                    """,
                ).indented(),
            )
            .issues(NonSerializableActivityParamsDetector.NON_SERIALIZABLE_ACTIVITY_PARAMS)
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }

    private fun activityParamsStubs(): Array<TestFile> = arrayOf(
        kt("""
            package com.duckduckgo.navigation.api

            import java.io.Serializable

            interface GlobalActivityStarter {
                interface ActivityParams : Serializable
            }
        """).indented(),
        kt("""
            package com.duckduckgo.example

            import java.io.Serializable

            enum class Source { A, B }
            data class SerializableData(val value: String) : Serializable
            data class PlainData(val value: String)
            sealed interface SerializableSealed : Serializable
        """).indented(),
    )
}
