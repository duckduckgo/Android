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
class MissingExplicitReturnTypeOnProvidesBindsDetectorTest {

    private val daggerStubs = kt(
        """
        package dagger
        annotation class Module
        annotation class Provides
        annotation class Binds
        """,
    ).indented()

    private val depStubs = kt(
        """
        package com.duckduckgo.example
        interface Foo
        class RealFoo : Foo
        class Bar
        """,
    ).indented()

    @Test
    fun whenProvidesMethodOmitsReturnTypeThenFailWithError() {
        lint()
            .files(
                daggerStubs,
                depStubs,
                kt(
                    """
                    package com.duckduckgo.example
                    import dagger.Module
                    import dagger.Provides

                    @Module
                    object FooModule {
                        @Provides
                        fun providesFoo() = RealFoo()
                    }
                    """,
                ).indented(),
            )
            .issues(MissingExplicitReturnTypeOnProvidesBindsDetector.MISSING_EXPLICIT_RETURN_TYPE)
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun whenProvidesMethodHasExplicitReturnTypeWithExpressionBodyThenSucceed() {
        lint()
            .files(
                daggerStubs,
                depStubs,
                kt(
                    """
                    package com.duckduckgo.example
                    import dagger.Module
                    import dagger.Provides

                    @Module
                    object FooModule {
                        @Provides
                        fun providesFoo(): Foo = RealFoo()
                    }
                    """,
                ).indented(),
            )
            .issues(MissingExplicitReturnTypeOnProvidesBindsDetector.MISSING_EXPLICIT_RETURN_TYPE)
            .run()
            .expectClean()
    }

    @Test
    fun whenProvidesMethodHasExplicitReturnTypeWithBlockBodyThenSucceed() {
        lint()
            .files(
                daggerStubs,
                depStubs,
                kt(
                    """
                    package com.duckduckgo.example
                    import dagger.Module
                    import dagger.Provides

                    @Module
                    object FooModule {
                        @Provides
                        fun providesFoo(): Foo {
                            return RealFoo()
                        }
                    }
                    """,
                ).indented(),
            )
            .issues(MissingExplicitReturnTypeOnProvidesBindsDetector.MISSING_EXPLICIT_RETURN_TYPE)
            .run()
            .expectClean()
    }

    @Test
    fun whenBindsMethodHasExplicitReturnTypeThenSucceed() {
        lint()
            .files(
                daggerStubs,
                depStubs,
                kt(
                    """
                    package com.duckduckgo.example
                    import dagger.Binds
                    import dagger.Module

                    @Module
                    abstract class FooModule {
                        @Binds
                        abstract fun bindFoo(impl: RealFoo): Foo
                    }
                    """,
                ).indented(),
            )
            .issues(MissingExplicitReturnTypeOnProvidesBindsDetector.MISSING_EXPLICIT_RETURN_TYPE)
            .run()
            .expectClean()
    }

    @Test
    fun whenRegularFunctionWithoutAnnotationOmitsReturnTypeThenSucceed() {
        lint()
            .files(
                daggerStubs,
                depStubs,
                kt(
                    """
                    package com.duckduckgo.example

                    object Helpers {
                        fun makeFoo() = RealFoo()
                    }
                    """,
                ).indented(),
            )
            .issues(MissingExplicitReturnTypeOnProvidesBindsDetector.MISSING_EXPLICIT_RETURN_TYPE)
            .run()
            .expectClean()
    }

    @Test
    fun whenMultipleProvidesMethodsMissingReturnTypeThenFailForEach() {
        lint()
            .files(
                daggerStubs,
                depStubs,
                kt(
                    """
                    package com.duckduckgo.example
                    import dagger.Module
                    import dagger.Provides

                    @Module
                    object FooModule {
                        @Provides
                        fun providesFoo() = RealFoo()

                        @Provides
                        fun providesBar() = Bar()
                    }
                    """,
                ).indented(),
            )
            .issues(MissingExplicitReturnTypeOnProvidesBindsDetector.MISSING_EXPLICIT_RETURN_TYPE)
            .run()
            .expectErrorCount(2)
    }
}
