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
class MissingContributesToOnModuleDetectorTest {

    private val daggerStubs = kt(
        """
        package dagger
        annotation class Module
        annotation class Provides
        annotation class Binds
        """,
    ).indented()

    private val anvilStubs = kt(
        """
        package com.squareup.anvil.annotations
        import kotlin.reflect.KClass
        annotation class ContributesTo(val scope: KClass<*>)
        """,
    ).indented()

    private val scopeStubs = kt(
        """
        package com.duckduckgo.di.scopes
        abstract class AppScope private constructor()
        abstract class ActivityScope private constructor()
        """,
    ).indented()

    @Test
    fun whenObjectModuleHasNoContributesToThenFailWithError() {
        lint()
            .files(
                daggerStubs,
                anvilStubs,
                kt(
                    """
                    package com.duckduckgo.example
                    import dagger.Module

                    @Module
                    object FooModule
                    """,
                ).indented(),
            )
            .issues(MissingContributesToOnModuleDetector.MISSING_CONTRIBUTES_TO_ON_MODULE)
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun whenAbstractClassModuleHasNoContributesToThenFailWithError() {
        lint()
            .files(
                daggerStubs,
                anvilStubs,
                kt(
                    """
                    package com.duckduckgo.example
                    import dagger.Module

                    @Module
                    abstract class FooModule
                    """,
                ).indented(),
            )
            .issues(MissingContributesToOnModuleDetector.MISSING_CONTRIBUTES_TO_ON_MODULE)
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun whenInterfaceModuleHasNoContributesToThenFailWithError() {
        lint()
            .files(
                daggerStubs,
                anvilStubs,
                kt(
                    """
                    package com.duckduckgo.example
                    import dagger.Module

                    @Module
                    interface FooModule
                    """,
                ).indented(),
            )
            .issues(MissingContributesToOnModuleDetector.MISSING_CONTRIBUTES_TO_ON_MODULE)
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun whenModuleHasContributesToAppScopeThenSucceed() {
        lint()
            .files(
                daggerStubs,
                anvilStubs,
                scopeStubs,
                kt(
                    """
                    package com.duckduckgo.example
                    import com.duckduckgo.di.scopes.AppScope
                    import com.squareup.anvil.annotations.ContributesTo
                    import dagger.Module

                    @Module
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
    fun whenModuleHasContributesToActivityScopeThenSucceed() {
        lint()
            .files(
                daggerStubs,
                anvilStubs,
                scopeStubs,
                kt(
                    """
                    package com.duckduckgo.example
                    import com.duckduckgo.di.scopes.ActivityScope
                    import com.squareup.anvil.annotations.ContributesTo
                    import dagger.Module

                    @Module
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
    fun whenClassIsNotAModuleThenSucceed() {
        lint()
            .files(
                daggerStubs,
                anvilStubs,
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
