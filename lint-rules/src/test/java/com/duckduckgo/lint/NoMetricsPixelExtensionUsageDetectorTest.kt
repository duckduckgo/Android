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
import com.duckduckgo.lint.NoMetricsPixelExtensionUsageDetector.Companion.NO_METRICS_PIXEL_EXTENSION_USAGE
import org.junit.Test

@Suppress("UnstableApiUsage")
class NoMetricsPixelExtensionUsageDetectorTest {

    @Test
    fun whenImportingMetricsPixelExtensionOutsideImplThenFailWithError() {
        lint()
            .files(kt("""
                package com.duckduckgo.somefeature.api

                import com.duckduckgo.feature.toggles.api.MetricsPixelExtension

                class SomeFeature {
                    fun doSomething() {}
                }
            """).indented())
            .allowCompilationErrors()
            .issues(NO_METRICS_PIXEL_EXTENSION_USAGE)
            .run()
            .expectContains("NoMetricsPixelExtensionUsage")
    }

    @Test
    fun whenImportingMetricsPixelExtensionProviderOutsideImplThenFailWithError() {
        lint()
            .files(kt("""
                package com.duckduckgo.somefeature.api

                import com.duckduckgo.feature.toggles.api.MetricsPixelExtensionProvider

                class SomeFeature {
                    fun doSomething() {}
                }
            """).indented())
            .allowCompilationErrors()
            .issues(NO_METRICS_PIXEL_EXTENSION_USAGE)
            .run()
            .expectContains("NoMetricsPixelExtensionUsage")
    }

    @Test
    fun whenImportingBothBannedTypesOutsideImplThenFailWithTwoErrors() {
        lint()
            .files(kt("""
                package com.duckduckgo.somefeature.impl

                import com.duckduckgo.feature.toggles.api.MetricsPixelExtension
                import com.duckduckgo.feature.toggles.api.MetricsPixelExtensionProvider

                class SomeImplementation {
                    fun doSomething() {}
                }
            """).indented())
            .allowCompilationErrors()
            .issues(NO_METRICS_PIXEL_EXTENSION_USAGE)
            .run()
            .expectContains("NoMetricsPixelExtensionUsage")
    }

    @Test
    fun whenImportingBannedTypesInsideFeatureTogglesImplThenSucceed() {
        lint()
            .files(kt("""
                package com.duckduckgo.feature.toggles.impl

                import com.duckduckgo.feature.toggles.api.MetricsPixelExtension
                import com.duckduckgo.feature.toggles.api.MetricsPixelExtensionProvider

                class RealMetricsPixelSender {
                    fun doSomething() {}
                }
            """).indented())
            .allowCompilationErrors()
            .issues(NO_METRICS_PIXEL_EXTENSION_USAGE)
            .run()
            .expectClean()
    }

    @Test
    fun whenImportingPublicMetricsPixelApiThenSucceed() {
        lint()
            .files(kt("""
                package com.duckduckgo.somefeature.api

                import com.duckduckgo.feature.toggles.api.MetricsPixel
                import com.duckduckgo.feature.toggles.api.ConversionWindow
                import com.duckduckgo.feature.toggles.api.MetricType

                class SomeFeature {
                    fun doSomething() {}
                }
            """).indented())
            .allowCompilationErrors()
            .issues(NO_METRICS_PIXEL_EXTENSION_USAGE)
            .run()
            .expectClean()
    }
}
