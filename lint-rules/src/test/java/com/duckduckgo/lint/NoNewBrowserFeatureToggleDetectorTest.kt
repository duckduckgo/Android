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
import com.duckduckgo.lint.NoNewBrowserFeatureToggleDetector.Companion.NO_NEW_BROWSER_FEATURE_TOGGLE
import org.junit.Test

@Suppress("UnstableApiUsage")
class NoNewBrowserFeatureToggleDetectorTest {

    @Test
    fun `grandfathered toggles - no errors`() {
        lint()
            .files(
                REMOTE_FEATURE_ANNOTATION_STUB,
                kt(
                    """
                package com.duckduckgo.browser.feature.toggles

                import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
                import com.duckduckgo.feature.toggles.api.Toggle

                @ContributesRemoteFeature(scope = Any::class, featureName = "androidBrowserConfig")
                interface AndroidBrowserConfigFeature {
                    fun self(): Toggle
                    fun screenLock(): Toggle
                    fun preserveCertificateOnSameOrigin(): Toggle
                }
                """,
                ).indented(),
            )
            .allowCompilationErrors()
            .issues(NO_NEW_BROWSER_FEATURE_TOGGLE)
            .run()
            .expectClean()
    }

    @Test
    fun `new toggle on the frozen interface - error reported`() {
        lint()
            .files(
                REMOTE_FEATURE_ANNOTATION_STUB,
                kt(
                    """
                package com.duckduckgo.browser.feature.toggles

                import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
                import com.duckduckgo.feature.toggles.api.Toggle

                @ContributesRemoteFeature(scope = Any::class, featureName = "androidBrowserConfig")
                interface AndroidBrowserConfigFeature {
                    fun self(): Toggle
                    fun myShinyNewToggle(): Toggle
                }
                """,
                ).indented(),
            )
            .allowCompilationErrors()
            .issues(NO_NEW_BROWSER_FEATURE_TOGGLE)
            .run()
            .expect(
                """
                src/com/duckduckgo/browser/feature/toggles/AndroidBrowserConfigFeature.kt:9: Error: Do not add new feature toggles to the browser-feature-toggles module [NoNewBrowserFeatureToggle]
                    fun myShinyNewToggle(): Toggle
                        ~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """.trimIndent(),
            )
    }

    @Test
    fun `new remote feature interface in the module - every toggle reported`() {
        lint()
            .files(
                REMOTE_FEATURE_ANNOTATION_STUB,
                kt(
                    """
                package com.duckduckgo.browser.feature.toggles

                import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
                import com.duckduckgo.feature.toggles.api.Toggle

                @ContributesRemoteFeature(scope = Any::class, featureName = "someOtherFeature")
                interface SomeOtherFeature {
                    fun self(): Toggle
                    fun anything(): Toggle
                }
                """,
                ).indented(),
            )
            .allowCompilationErrors()
            .issues(NO_NEW_BROWSER_FEATURE_TOGGLE)
            .run()
            .expect(
                """
                src/com/duckduckgo/browser/feature/toggles/SomeOtherFeature.kt:8: Error: Do not add new feature toggles to the browser-feature-toggles module [NoNewBrowserFeatureToggle]
                    fun self(): Toggle
                        ~~~~
                src/com/duckduckgo/browser/feature/toggles/SomeOtherFeature.kt:9: Error: Do not add new feature toggles to the browser-feature-toggles module [NoNewBrowserFeatureToggle]
                    fun anything(): Toggle
                        ~~~~~~~~
                2 errors, 0 warnings
                """.trimIndent(),
            )
    }

    @Test
    fun `new interface reusing a grandfathered toggle name - error reported`() {
        lint()
            .files(
                REMOTE_FEATURE_ANNOTATION_STUB,
                kt(
                    """
                package com.duckduckgo.browser.feature.toggles

                import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
                import com.duckduckgo.feature.toggles.api.Toggle

                @ContributesRemoteFeature(scope = Any::class, featureName = "sneakyFeature")
                interface SneakyFeature {
                    fun screenLock(): Toggle
                }
                """,
                ).indented(),
            )
            .allowCompilationErrors()
            .issues(NO_NEW_BROWSER_FEATURE_TOGGLE)
            .run()
            .expect(
                """
                src/com/duckduckgo/browser/feature/toggles/SneakyFeature.kt:8: Error: Do not add new feature toggles to the browser-feature-toggles module [NoNewBrowserFeatureToggle]
                    fun screenLock(): Toggle
                        ~~~~~~~~~~
                1 errors, 0 warnings
                """.trimIndent(),
            )
    }

    @Test
    fun `new toggle in another module - no errors`() {
        lint()
            .files(
                REMOTE_FEATURE_ANNOTATION_STUB,
                kt(
                    """
                package com.duckduckgo.myfeature.impl

                import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
                import com.duckduckgo.feature.toggles.api.Toggle

                @ContributesRemoteFeature(scope = Any::class, featureName = "myFeature")
                interface MyFeature {
                    fun self(): Toggle
                    fun myShinyNewToggle(): Toggle
                }
                """,
                ).indented(),
            )
            .allowCompilationErrors()
            .issues(NO_NEW_BROWSER_FEATURE_TOGGLE)
            .run()
            .expectClean()
    }

    @Test
    fun `non remote feature class in the module - no errors`() {
        lint()
            .files(
                REMOTE_FEATURE_ANNOTATION_STUB,
                kt(
                    """
                package com.duckduckgo.browser.feature.toggles

                interface SomeHelper {
                    fun doSomething(): String
                }
                """,
                ).indented(),
            )
            .allowCompilationErrors()
            .issues(NO_NEW_BROWSER_FEATURE_TOGGLE)
            .run()
            .expectClean()
    }

    companion object {
        private val REMOTE_FEATURE_ANNOTATION_STUB = kt(
            """
            package com.duckduckgo.anvil.annotations

            import kotlin.reflect.KClass

            annotation class ContributesRemoteFeature(
                val scope: KClass<*>,
                val featureName: String,
            )
            """,
        ).indented()
    }
}
