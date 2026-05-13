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
class MissingHasMemberInjectionsDetectorTest {

    private val injectStub = kt(
        """
        package javax.inject
        annotation class Inject
        annotation class Qualifier
        """,
    ).indented()

    private val metroStub = kt(
        """
        package dev.zacsweers.metro
        annotation class HasMemberInjections
        """,
    ).indented()

    private val depStub = kt(
        """
        package com.duckduckgo.example
        class Dep
        class OtherDep
        """,
    ).indented()

    @Test
    fun whenOpenClassHasInjectMemberWithoutMarkerThenFailWithError() {
        lint()
            .files(
                injectStub,
                metroStub,
                depStub,
                kt(
                    """
                    package com.duckduckgo.example
                    import javax.inject.Inject

                    open class Foo {
                        @Inject lateinit var dep: Dep
                    }
                    """,
                ).indented(),
            )
            .issues(MissingHasMemberInjectionsDetector.MISSING_HAS_MEMBER_INJECTIONS)
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun whenOpenClassHasQualifiedInjectMemberWithoutMarkerThenFailWithError() {
        lint()
            .files(
                injectStub,
                metroStub,
                depStub,
                kt(
                    """
                    package com.duckduckgo.example
                    import javax.inject.Inject
                    import javax.inject.Qualifier

                    @Qualifier annotation class Named

                    open class Foo {
                        @Inject @field:Named lateinit var dep: Dep
                    }
                    """,
                ).indented(),
            )
            .issues(MissingHasMemberInjectionsDetector.MISSING_HAS_MEMBER_INJECTIONS)
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun whenOpenClassHasInjectMemberAndMarkerThenSucceed() {
        lint()
            .files(
                injectStub,
                metroStub,
                depStub,
                kt(
                    """
                    package com.duckduckgo.example
                    import dev.zacsweers.metro.HasMemberInjections
                    import javax.inject.Inject

                    @HasMemberInjections
                    open class Foo {
                        @Inject lateinit var dep: Dep
                    }
                    """,
                ).indented(),
            )
            .issues(MissingHasMemberInjectionsDetector.MISSING_HAS_MEMBER_INJECTIONS)
            .run()
            .expectClean()
    }

    @Test
    fun whenFinalClassHasInjectMemberWithoutMarkerThenSucceed() {
        // Kotlin classes are final by default. Final classes cannot be subclassed, so Metro does
        // not require @HasMemberInjections on them.
        lint()
            .files(
                injectStub,
                metroStub,
                depStub,
                kt(
                    """
                    package com.duckduckgo.example
                    import javax.inject.Inject

                    class Foo {
                        @Inject lateinit var dep: Dep
                    }
                    """,
                ).indented(),
            )
            .issues(MissingHasMemberInjectionsDetector.MISSING_HAS_MEMBER_INJECTIONS)
            .run()
            .expectClean()
    }

    @Test
    fun whenClassUsesOnlyConstructorInjectionThenSucceed() {
        lint()
            .files(
                injectStub,
                metroStub,
                depStub,
                kt(
                    """
                    package com.duckduckgo.example
                    import javax.inject.Inject

                    class Foo @Inject constructor(val dep: Dep)
                    """,
                ).indented(),
            )
            .issues(MissingHasMemberInjectionsDetector.MISSING_HAS_MEMBER_INJECTIONS)
            .run()
            .expectClean()
    }

    @Test
    fun whenClassHasNoInjectAtAllThenSucceed() {
        lint()
            .files(
                injectStub,
                metroStub,
                depStub,
                kt(
                    """
                    package com.duckduckgo.example

                    class Foo {
                        var dep: Dep? = null
                    }
                    """,
                ).indented(),
            )
            .issues(MissingHasMemberInjectionsDetector.MISSING_HAS_MEMBER_INJECTIONS)
            .run()
            .expectClean()
    }

    @Test
    fun whenAbstractClassHasInjectMemberWithoutMarkerThenFailWithError() {
        // Abstract bases also require @HasMemberInjections; the Metro migration declares it on
        // every class with member injection, regardless of whether it is abstract.
        lint()
            .files(
                injectStub,
                metroStub,
                depStub,
                kt(
                    """
                    package com.duckduckgo.example
                    import javax.inject.Inject

                    abstract class Base {
                        @Inject lateinit var dep: Dep
                    }
                    """,
                ).indented(),
            )
            .issues(MissingHasMemberInjectionsDetector.MISSING_HAS_MEMBER_INJECTIONS)
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun whenOpenClassHasBothConstructorInjectionAndMemberInjectionWithoutMarkerThenFailWithError() {
        lint()
            .files(
                injectStub,
                metroStub,
                depStub,
                kt(
                    """
                    package com.duckduckgo.example
                    import javax.inject.Inject

                    open class Foo @Inject constructor(val a: Dep) {
                        @Inject lateinit var b: OtherDep
                    }
                    """,
                ).indented(),
            )
            .issues(MissingHasMemberInjectionsDetector.MISSING_HAS_MEMBER_INJECTIONS)
            .run()
            .expectErrorCount(1)
    }
}
