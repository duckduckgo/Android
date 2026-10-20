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
import com.duckduckgo.lint.utils.METRO_DI_API
import org.junit.Test

@Suppress("UnstableApiUsage")
class MissingContributesToOnModuleDetectorTest {

    private val scopeStubs = kt(
        """
        package com.duckduckgo.di.scopes
        abstract class AppScope private constructor()
        abstract class ActivityScope private constructor()
        """,
    ).indented()

    @Test
    fun whenAbstractClassBindingContainerHasNoContributesToThenFailWithError() {
        lint()
            .files(
                METRO_DI_API,
                kt(
                    """
                    package com.duckduckgo.example
                    import dev.zacsweers.metro.BindingContainer

                    @BindingContainer
                    abstract class FooModule
                    """,
                ).indented(),
            )
            .issues(MissingContributesToOnModuleDetector.MISSING_CONTRIBUTES_TO_ON_MODULE)
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun whenInterfaceBindingContainerHasNoContributesToThenFailWithError() {
        lint()
            .files(
                METRO_DI_API,
                kt(
                    """
                    package com.duckduckgo.example
                    import dev.zacsweers.metro.BindingContainer

                    @BindingContainer
                    interface FooModule
                    """,
                ).indented(),
            )
            .issues(MissingContributesToOnModuleDetector.MISSING_CONTRIBUTES_TO_ON_MODULE)
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun whenBindingContainerHasContributesToActivityScopeThenSucceed() {
        lint()
            .files(
                METRO_DI_API,
                scopeStubs,
                kt(
                    """
                    package com.duckduckgo.example
                    import com.duckduckgo.di.scopes.ActivityScope
                    import dev.zacsweers.metro.BindingContainer
                    import dev.zacsweers.metro.ContributesTo

                    @BindingContainer
                    @ContributesTo(ActivityScope::class)
                    object FooModule
                    """,
                ).indented(),
            )
            .issues(MissingContributesToOnModuleDetector.MISSING_CONTRIBUTES_TO_ON_MODULE)
            .run()
            .expectClean()
    }

    @Test
    fun whenBindingContainerHasNoContributesToThenFailWithError() {
        lint()
            .files(
                METRO_DI_API,
                kt(
                    """
                    package com.duckduckgo.example
                    import dev.zacsweers.metro.BindingContainer

                    @BindingContainer
                    object FooModule
                    """,
                ).indented(),
            )
            .issues(MissingContributesToOnModuleDetector.MISSING_CONTRIBUTES_TO_ON_MODULE)
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun whenBindingContainerHasContributesToThenSucceed() {
        lint()
            .files(
                METRO_DI_API,
                scopeStubs,
                kt(
                    """
                    package com.duckduckgo.example
                    import com.duckduckgo.di.scopes.AppScope
                    import dev.zacsweers.metro.BindingContainer
                    import dev.zacsweers.metro.ContributesTo

                    @BindingContainer
                    @ContributesTo(AppScope::class)
                    object FooModule
                    """,
                ).indented(),
            )
            .issues(MissingContributesToOnModuleDetector.MISSING_CONTRIBUTES_TO_ON_MODULE)
            .run()
            .expectClean()
    }

    @Test
    fun whenClassIsNotAModuleThenSucceed() {
        lint()
            .files(
                METRO_DI_API,
                kt(
                    """
                    package com.duckduckgo.example

                    class PlainClass {
                        fun work() {}
                    }
                    """,
                ).indented(),
            )
            .issues(MissingContributesToOnModuleDetector.MISSING_CONTRIBUTES_TO_ON_MODULE)
            .run()
            .expectClean()
    }
}
