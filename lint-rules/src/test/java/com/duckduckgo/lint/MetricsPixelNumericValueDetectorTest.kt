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
import com.android.tools.lint.checks.infrastructure.TestMode
import com.duckduckgo.lint.MetricsPixelNumericValueDetector.Companion.NUMERIC_VALUE_REQUIRED
import org.junit.Test

@Suppress("UnstableApiUsage")
class MetricsPixelNumericValueDetectorTest {

    @Test
    fun `COUNT_WHEN_IN_WINDOW with valid integer value - no error`() {
        lint()
            .files(
                metricsPixelStub(),
                kt("""
                    package com.duckduckgo.somefeature.impl

                    import com.duckduckgo.feature.toggles.api.MetricsPixel
                    import com.duckduckgo.feature.toggles.api.MetricType
                    import com.duckduckgo.feature.toggles.api.ConversionWindow

                    class SomeFeature {
                        fun doSomething(toggle: Any) {
                            MetricsPixel(
                                metric = "some_metric",
                                value = "3",
                                toggle = toggle as com.duckduckgo.feature.toggles.api.Toggle,
                                conversionWindow = listOf(ConversionWindow(0, 1)),
                                type = MetricType.COUNT_WHEN_IN_WINDOW,
                            )
                        }
                    }
                """).indented(),
            )
            .allowCompilationErrors()
            .issues(NUMERIC_VALUE_REQUIRED)
            .skipTestModes(TestMode.REORDER_ARGUMENTS)
            .run()
            .expectClean()
    }

    @Test
    fun `COUNT_ALWAYS with valid integer value - no error`() {
        lint()
            .files(
                metricsPixelStub(),
                kt("""
                    package com.duckduckgo.somefeature.impl

                    import com.duckduckgo.feature.toggles.api.MetricsPixel
                    import com.duckduckgo.feature.toggles.api.MetricType
                    import com.duckduckgo.feature.toggles.api.ConversionWindow

                    class SomeFeature {
                        fun doSomething(toggle: Any) {
                            MetricsPixel(
                                metric = "some_metric",
                                value = "5",
                                toggle = toggle as com.duckduckgo.feature.toggles.api.Toggle,
                                conversionWindow = listOf(ConversionWindow(0, 1)),
                                type = MetricType.COUNT_ALWAYS,
                            )
                        }
                    }
                """).indented(),
            )
            .allowCompilationErrors()
            .issues(NUMERIC_VALUE_REQUIRED)
            .skipTestModes(TestMode.REORDER_ARGUMENTS)
            .run()
            .expectClean()
    }

    @Test
    fun `NORMAL with non-integer value - no error`() {
        lint()
            .files(
                metricsPixelStub(),
                kt("""
                    package com.duckduckgo.somefeature.impl

                    import com.duckduckgo.feature.toggles.api.MetricsPixel
                    import com.duckduckgo.feature.toggles.api.MetricType
                    import com.duckduckgo.feature.toggles.api.ConversionWindow

                    class SomeFeature {
                        fun doSomething(toggle: Any) {
                            MetricsPixel(
                                metric = "some_metric",
                                value = "not_a_number",
                                toggle = toggle as com.duckduckgo.feature.toggles.api.Toggle,
                                conversionWindow = listOf(ConversionWindow(0, 1)),
                                type = MetricType.NORMAL,
                            )
                        }
                    }
                """).indented(),
            )
            .allowCompilationErrors()
            .issues(NUMERIC_VALUE_REQUIRED)
            .skipTestModes(TestMode.REORDER_ARGUMENTS)
            .run()
            .expectClean()
    }

    @Test
    fun `COUNT_WHEN_IN_WINDOW with non-integer value - error reported`() {
        lint()
            .files(
                metricsPixelStub(),
                kt("""
                    package com.duckduckgo.somefeature.impl

                    import com.duckduckgo.feature.toggles.api.MetricsPixel
                    import com.duckduckgo.feature.toggles.api.MetricType
                    import com.duckduckgo.feature.toggles.api.ConversionWindow

                    class SomeFeature {
                        fun doSomething(toggle: Any) {
                            MetricsPixel(
                                metric = "some_metric",
                                value = "not_a_number",
                                toggle = toggle as com.duckduckgo.feature.toggles.api.Toggle,
                                conversionWindow = listOf(ConversionWindow(0, 1)),
                                type = MetricType.COUNT_WHEN_IN_WINDOW,
                            )
                        }
                    }
                """).indented(),
            )
            .allowCompilationErrors()
            .issues(NUMERIC_VALUE_REQUIRED)
            .skipTestModes(TestMode.REORDER_ARGUMENTS)
            .run()
            .expectContains("MetricsPixelNumericValue")
    }

    @Test
    fun `COUNT_ALWAYS with non-integer value - error reported`() {
        lint()
            .files(
                metricsPixelStub(),
                kt("""
                    package com.duckduckgo.somefeature.impl

                    import com.duckduckgo.feature.toggles.api.MetricsPixel
                    import com.duckduckgo.feature.toggles.api.MetricType
                    import com.duckduckgo.feature.toggles.api.ConversionWindow

                    class SomeFeature {
                        fun doSomething(toggle: Any) {
                            MetricsPixel(
                                metric = "some_metric",
                                value = "not_a_number",
                                toggle = toggle as com.duckduckgo.feature.toggles.api.Toggle,
                                conversionWindow = listOf(ConversionWindow(0, 1)),
                                type = MetricType.COUNT_ALWAYS,
                            )
                        }
                    }
                """).indented(),
            )
            .allowCompilationErrors()
            .issues(NUMERIC_VALUE_REQUIRED)
            .skipTestModes(TestMode.REORDER_ARGUMENTS)
            .run()
            .expectContains("MetricsPixelNumericValue")
    }

    @Test
    fun `COUNT_ALWAYS with non-integer value and out-of-order named args - error reported`() {
        lint()
            .files(
                metricsPixelStub(),
                kt("""
                    package com.duckduckgo.somefeature.impl

                    import com.duckduckgo.feature.toggles.api.MetricsPixel
                    import com.duckduckgo.feature.toggles.api.MetricType
                    import com.duckduckgo.feature.toggles.api.ConversionWindow

                    class SomeFeature {
                        fun doSomething(toggle: Any) {
                            MetricsPixel(
                                type = MetricType.COUNT_ALWAYS,
                                metric = "some_metric",
                                conversionWindow = listOf(ConversionWindow(0, 1)),
                                value = "not_a_number",
                                toggle = toggle as com.duckduckgo.feature.toggles.api.Toggle,
                            )
                        }
                    }
                """).indented(),
            )
            .allowCompilationErrors()
            .issues(NUMERIC_VALUE_REQUIRED)
            .skipTestModes(TestMode.REORDER_ARGUMENTS)
            .run()
            .expectContains("MetricsPixelNumericValue")
    }

    // Stub matches the real MetricsPixel signature: declaration order is
    // metric, value, conversionWindow, toggle, type (type has a default value).
    // Tests pass toggle before conversionWindow (source order ≠ declaration order)
    // to exercise named-argument lookup rather than positional index mapping.
    private fun metricsPixelStub() = kt("""
        package com.duckduckgo.feature.toggles.api

        interface Toggle
        enum class MetricType { NORMAL, COUNT_WHEN_IN_WINDOW, COUNT_ALWAYS }
        data class ConversionWindow(val lowerWindow: Int, val upperWindow: Int)
        data class MetricsPixel(
            val metric: String,
            val value: String,
            val conversionWindow: List<ConversionWindow>,
            val toggle: Toggle,
            val type: MetricType = MetricType.NORMAL,
        )
    """).indented()
}
