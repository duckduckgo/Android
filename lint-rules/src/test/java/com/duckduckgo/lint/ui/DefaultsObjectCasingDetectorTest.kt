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

package com.duckduckgo.lint.ui

import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import com.duckduckgo.lint.ui.DefaultsObjectCasingDetector.Companion.DEFAULTS_PROPERTY_CASING
import org.junit.Test

class DefaultsObjectCasingDetectorTest {

    private val composableStub = kotlin(
        """
        package androidx.compose.runtime

        annotation class Composable
        """.trimIndent(),
    ).indented()

    @Test
    fun whenCamelCaseConstantInDefaultsObjectThenError() {
        lint()
            .files(
                kotlin(
                    """
                    package com.duckduckgo.common.ui.compose.foo

                    object DaxFooDefaults {
                        val iconSize = 8
                    }
                    """.trimIndent(),
                ).indented(),
            )
            .issues(DEFAULTS_PROPERTY_CASING)
            .run()
            .expect(
                """
                src/com/duckduckgo/common/ui/compose/foo/DaxFooDefaults.kt:4: Error: Constant iconSize in DaxFooDefaults should be PascalCase: IconSize [DefaultsPropertyCasing]
                    val iconSize = 8
                        ~~~~~~~~
                1 errors, 0 warnings
                """.trimIndent(),
            )
    }

    @Test
    fun whenCamelCaseConstValInDefaultsObjectThenError() {
        lint()
            .files(
                kotlin(
                    """
                    package com.duckduckgo.common.ui.compose.foo

                    private object DaxFooDefaults {
                        const val disabledAlpha = 0.4f
                    }
                    """.trimIndent(),
                ).indented(),
            )
            .issues(DEFAULTS_PROPERTY_CASING)
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun whenPascalCaseConstantInDefaultsObjectThenNoError() {
        lint()
            .files(
                kotlin(
                    """
                    package com.duckduckgo.common.ui.compose.foo

                    object DaxFooDefaults {
                        val IconSize = 8
                        const val DisabledAlpha = 0.4f
                    }
                    """.trimIndent(),
                ).indented(),
            )
            .issues(DEFAULTS_PROPERTY_CASING)
            .run()
            .expectClean()
    }

    @Test
    fun whenScreamingSnakeCaseConstValInDefaultsObjectThenNoError() {
        lint()
            .files(
                kotlin(
                    """
                    package com.duckduckgo.common.ui.compose.foo

                    object DaxFooDefaults {
                        internal const val ALPHA_DISABLED = 0.4f
                    }
                    """.trimIndent(),
                ).indented(),
            )
            .issues(DEFAULTS_PROPERTY_CASING)
            .run()
            .expectClean()
    }

    @Test
    fun whenCamelCasePropertyWithComposableGetterInDefaultsObjectThenNoError() {
        lint()
            .files(
                composableStub,
                kotlin(
                    """
                    package com.duckduckgo.common.ui.compose.foo

                    import androidx.compose.runtime.Composable

                    object DaxFooDefaults {
                        val containerColor: Int
                            @Composable get() = 0
                    }
                    """.trimIndent(),
                ).indented(),
            )
            .issues(DEFAULTS_PROPERTY_CASING)
            .run()
            .expectClean()
    }

    @Test
    fun whenCamelCaseFunctionInDefaultsObjectThenNoError() {
        lint()
            .files(
                kotlin(
                    """
                    package com.duckduckgo.common.ui.compose.foo

                    object DaxFooDefaults {
                        fun colors(): Int = 0
                    }
                    """.trimIndent(),
                ).indented(),
            )
            .issues(DEFAULTS_PROPERTY_CASING)
            .run()
            .expectClean()
    }

    @Test
    fun whenCamelCaseConstantInNonDefaultsObjectThenNoError() {
        lint()
            .files(
                kotlin(
                    """
                    package com.duckduckgo.common.ui.compose.foo

                    object DaxFooHelper {
                        val iconSize = 8
                    }
                    """.trimIndent(),
                ).indented(),
            )
            .issues(DEFAULTS_PROPERTY_CASING)
            .run()
            .expectClean()
    }
}
