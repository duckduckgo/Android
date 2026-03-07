/*
 * Copyright (c) 2025 DuckDuckGo
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
import com.duckduckgo.lint.RemoteFeatureNameDetector.Companion.UNDERSCORE_IN_FEATURE_NAME
import org.junit.Test

@Suppress("UnstableApiUsage")
class RemoteFeatureNameDetectorTest {

    @Test
    fun `clean interface - no errors`() {
        lint()
            .files(REMOTE_FEATURE_ANNOTATION_STUB, kt("""
                package com.test

                import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
                import com.duckduckgo.feature.toggles.api.Toggle

                @ContributesRemoteFeature(scope = Any::class, featureName = "myFeature")
                interface MyFeature {
                    fun self(): Toggle
                    fun subFeature(): Toggle
                }
            """).indented())
            .allowCompilationErrors()
            .issues(UNDERSCORE_IN_FEATURE_NAME)
            .run()
            .expectClean()
    }

    @Test
    fun `underscore in featureName - error reported`() {
        lint()
            .files(REMOTE_FEATURE_ANNOTATION_STUB, kt("""
                package com.test

                import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
                import com.duckduckgo.feature.toggles.api.Toggle

                @ContributesRemoteFeature(scope = Any::class, featureName = "my_feature")
                interface MyFeature {
                    fun self(): Toggle
                }
            """).indented())
            .allowCompilationErrors()
            .issues(UNDERSCORE_IN_FEATURE_NAME)
            .run()
            .expect("""
                src/com/test/MyFeature.kt:6: Error: RemoteFeature names must not contain underscores [RemoteFeatureNameWithUnderscore]
                @ContributesRemoteFeature(scope = Any::class, featureName = "my_feature")
                                                                            ~~~~~~~~~~~~
                1 errors, 0 warnings
            """.trimIndent())
    }

    @Test
    fun `underscore in method name - error reported`() {
        lint()
            .files(REMOTE_FEATURE_ANNOTATION_STUB, kt("""
                package com.test

                import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
                import com.duckduckgo.feature.toggles.api.Toggle

                @ContributesRemoteFeature(scope = Any::class, featureName = "myFeature")
                interface MyFeature {
                    fun self(): Toggle
                    fun sub_feature(): Toggle
                }
            """).indented())
            .allowCompilationErrors()
            .issues(UNDERSCORE_IN_FEATURE_NAME)
            .run()
            .expect("""
                src/com/test/MyFeature.kt:9: Error: RemoteFeature names must not contain underscores [RemoteFeatureNameWithUnderscore]
                    fun sub_feature(): Toggle
                        ~~~~~~~~~~~
                1 errors, 0 warnings
            """.trimIndent())
    }

    @Test
    fun `underscore in both featureName and method name - both errors reported`() {
        lint()
            .files(REMOTE_FEATURE_ANNOTATION_STUB, kt("""
                package com.test

                import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
                import com.duckduckgo.feature.toggles.api.Toggle

                @ContributesRemoteFeature(scope = Any::class, featureName = "my_feature")
                interface MyFeature {
                    fun self(): Toggle
                    fun sub_feature(): Toggle
                }
            """).indented())
            .allowCompilationErrors()
            .issues(UNDERSCORE_IN_FEATURE_NAME)
            .run()
            .expect("""
                src/com/test/MyFeature.kt:6: Error: RemoteFeature names must not contain underscores [RemoteFeatureNameWithUnderscore]
                @ContributesRemoteFeature(scope = Any::class, featureName = "my_feature")
                                                                            ~~~~~~~~~~~~
                src/com/test/MyFeature.kt:9: Error: RemoteFeature names must not contain underscores [RemoteFeatureNameWithUnderscore]
                    fun sub_feature(): Toggle
                        ~~~~~~~~~~~
                2 errors, 0 warnings
            """.trimIndent())
    }

    @Test
    fun `interface without ContributesRemoteFeature is not checked`() {
        lint()
            .files(REMOTE_FEATURE_ANNOTATION_STUB, kt("""
                package com.test

                import com.duckduckgo.feature.toggles.api.Toggle

                interface MyFeature {
                    fun sub_feature(): Toggle
                }
            """).indented())
            .allowCompilationErrors()
            .issues(UNDERSCORE_IN_FEATURE_NAME)
            .run()
            .expectClean()
    }

    @Test
    fun `multiple methods with underscores - all reported`() {
        lint()
            .files(REMOTE_FEATURE_ANNOTATION_STUB, kt("""
                package com.test

                import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
                import com.duckduckgo.feature.toggles.api.Toggle

                @ContributesRemoteFeature(scope = Any::class, featureName = "myFeature")
                interface MyFeature {
                    fun self(): Toggle
                    fun foo_one(): Toggle
                    fun foo_two(): Toggle
                }
            """).indented())
            .allowCompilationErrors()
            .issues(UNDERSCORE_IN_FEATURE_NAME)
            .run()
            .expect("""
                src/com/test/MyFeature.kt:9: Error: RemoteFeature names must not contain underscores [RemoteFeatureNameWithUnderscore]
                    fun foo_one(): Toggle
                        ~~~~~~~
                src/com/test/MyFeature.kt:10: Error: RemoteFeature names must not contain underscores [RemoteFeatureNameWithUnderscore]
                    fun foo_two(): Toggle
                        ~~~~~~~
                2 errors, 0 warnings
            """.trimIndent())
    }

    companion object {
        private val REMOTE_FEATURE_ANNOTATION_STUB = kt("""
            package com.duckduckgo.anvil.annotations

            import kotlin.reflect.KClass

            annotation class ContributesRemoteFeature(
                val scope: KClass<*>,
                val featureName: String,
            )
        """).indented()
    }
}
