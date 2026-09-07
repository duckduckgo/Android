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
import com.duckduckgo.lint.NoHardcodedPetalPixelParamDetector.Companion.ERROR_ID
import com.duckduckgo.lint.NoHardcodedPetalPixelParamDetector.Companion.KEY_MESSAGE
import com.duckduckgo.lint.NoHardcodedPetalPixelParamDetector.Companion.NO_HARDCODED_PETAL_PIXEL_PARAM
import com.duckduckgo.lint.NoHardcodedPetalPixelParamDetector.Companion.VALUE_MESSAGE
import org.junit.Test

@Suppress("UnstableApiUsage")
class NoHardcodedPetalPixelParamDetectorTest {

    @Test
    fun whenPetalKeyHardcodedInPixelParametersThenDetectedAsAViolation() {
        val callSite = """
            package com.duckduckgo.feature.impl

            import com.duckduckgo.app.statistics.pixels.Pixel

            class Duck(private val pixel: Pixel) {
                fun quack() {
                    pixel.fire("m_duck_quack", mapOf("petal" to "randomize"))
                }
            }
        """

        assertLintError(listOf(kt(callSite).indented(), pixelStub), KEY_MESSAGE)
    }

    @Test
    fun whenPetalKeyHardcodedInPutCallThenDetectedAsAViolation() {
        val callSite = """
            package com.duckduckgo.feature.impl

            class Duck {
                fun quack(): Map<String, String> = buildMap {
                    put("petal", "randomize")
                }
            }
        """

        assertLintError(listOf(kt(callSite).indented(), pixelStub), KEY_MESSAGE)
    }

    @Test
    fun whenPetalKeyRedeclaredInFeatureModuleThenDetectedAsAViolation() {
        val callSite = """
            package com.duckduckgo.feature.impl

            object DuckPixelParameters {
                const val PETAL = "petal"
            }
        """

        assertLintError(listOf(kt(callSite).indented(), pixelStub), KEY_MESSAGE)
    }

    @Test
    fun whenPetalValueHardcodedAlongsideSharedKeyThenDetectedAsAViolation() {
        val callSite = """
            package com.duckduckgo.feature.impl

            import com.duckduckgo.app.statistics.pixels.Pixel

            class Duck(private val pixel: Pixel) {
                fun quack() {
                    pixel.fire("m_duck_quack", mapOf(Pixel.PixelParameter.PETAL to "randomize"))
                }
            }
        """

        assertLintError(listOf(kt(callSite).indented(), pixelStub), VALUE_MESSAGE)
    }

    @Test
    fun whenDeprecatedPetalValueHardcodedThenDetectedAsAViolation() {
        val callSite = """
            package com.duckduckgo.feature.impl

            import com.duckduckgo.app.statistics.pixels.Pixel

            class Duck(private val pixel: Pixel) {
                fun quack() {
                    pixel.fire("m_duck_quack", mapOf(Pixel.PixelParameter.PETAL to "true"))
                }
            }
        """

        assertLintError(listOf(kt(callSite).indented(), pixelStub), VALUE_MESSAGE)
    }

    @Test
    fun whenPetalValuesRedeclaredInFeatureModuleThenDetectedAsAViolation() {
        val callSite = """
            package com.duckduckgo.feature.impl

            object PetalValues {
                const val RANDOMIZE = "randomize"
            }
        """

        assertLintError(listOf(kt(callSite).indented(), pixelStub), VALUE_MESSAGE)
    }

    @Test
    fun whenSharedConstantsUsedThenNotDetectedAsAViolation() {
        val callSite = """
            package com.duckduckgo.feature.impl

            import com.duckduckgo.app.statistics.pixels.Pixel

            class Duck(private val pixel: Pixel) {
                fun quack() {
                    pixel.fire(
                        "m_duck_quack",
                        mapOf(Pixel.PixelParameter.PETAL to Pixel.PixelValues.PETAL_RANDOMIZE),
                    )
                }
            }
        """

        assertNoLintError(listOf(kt(callSite).indented(), pixelStub))
    }

    @Test
    fun whenPixelApiDeclaresPetalConstantsThenNotDetectedAsAViolation() {
        assertNoLintError(listOf(pixelStub))
    }

    @Test
    fun whenUnrelatedParameterUsesTheSameValueThenNotDetectedAsAViolation() {
        val callSite = """
            package com.duckduckgo.feature.impl

            import com.duckduckgo.app.statistics.pixels.Pixel

            class Duck(private val pixel: Pixel) {
                fun quack() {
                    pixel.fire("m_duck_quack", mapOf(Pixel.PixelParameter.SOURCE to "randomize"))
                }
            }
        """

        assertNoLintError(listOf(kt(callSite).indented(), pixelStub))
    }

    private fun assertLintError(files: List<TestFile>, message: String) {
        lint()
            .files(*files.toTypedArray())
            .issues(NO_HARDCODED_PETAL_PIXEL_PARAM)
            .testModes(TestMode.DEFAULT)
            .allowCompilationErrors()
            .run()
            .expectContains("$message [$ERROR_ID]")
    }

    private fun assertNoLintError(files: List<TestFile>) {
        lint()
            .files(*files.toTypedArray())
            .issues(NO_HARDCODED_PETAL_PIXEL_PARAM)
            .testModes(TestMode.DEFAULT)
            .allowCompilationErrors()
            .run()
            .expectClean()
    }

    private val pixelStub = kt(
        """
        package com.duckduckgo.app.statistics.pixels

        interface Pixel {
            object PixelParameter {
                const val SOURCE = "source"
                const val PETAL = "petal"
            }

            object PixelValues {
                const val PETAL_RANDOMIZE = "randomize"
                const val PETAL_KANON = "kanon"
            }

            fun fire(pixelName: String, parameters: Map<String, String> = emptyMap())
        }
        """,
    ).indented()
}
