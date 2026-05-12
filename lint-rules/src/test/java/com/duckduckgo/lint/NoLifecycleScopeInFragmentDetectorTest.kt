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
import com.duckduckgo.lint.NoLifecycleScopeInFragmentDetector.Companion.ERROR_DESCRIPTION
import com.duckduckgo.lint.NoLifecycleScopeInFragmentDetector.Companion.ERROR_ID
import com.duckduckgo.lint.NoLifecycleScopeInFragmentDetector.Companion.NO_LIFECYCLE_SCOPE_IN_FRAGMENT
import org.junit.Test

@Suppress("UnstableApiUsage")
class NoLifecycleScopeInFragmentDetectorTest {

    @Test
    fun whenLifecycleScopeLaunchInFragmentThenError() {
        assertLintError(
            listOf(
                kt(lifecycleStubs),
                kt(fragmentStubs),
                kt(
                    """
                    package com.duckduckgo.lint

                    import androidx.fragment.app.Fragment

                    class MyFragment : Fragment() {
                        fun doWork() {
                            lifecycleScope.launch { }
                        }
                    }
                    """,
                ).indented(),
            ),
        )
    }

    @Test
    fun whenLifecycleScopeLaunchInInFragmentThenError() {
        assertLintError(
            listOf(
                kt(lifecycleStubs),
                kt(fragmentStubs),
                kt(
                    """
                    package com.duckduckgo.lint

                    import androidx.fragment.app.Fragment

                    class MyFragment : Fragment() {
                        fun observeFlows() {
                            flow.launchIn(lifecycleScope)
                        }
                    }
                    """,
                ).indented(),
            ),
        )
    }

    @Test
    fun whenLifecycleScopeInDialogFragmentThenError() {
        assertLintError(
            listOf(
                kt(lifecycleStubs),
                kt(fragmentStubs),
                kt(
                    """
                    package com.duckduckgo.lint

                    import androidx.fragment.app.DialogFragment

                    class MyDialog : DialogFragment() {
                        fun doWork() {
                            lifecycleScope.launch { }
                        }
                    }
                    """,
                ).indented(),
            ),
        )
    }

    @Test
    fun whenViewLifecycleOwnerLifecycleScopeInFragmentThenClean() {
        assertNoLintError(
            listOf(
                kt(lifecycleStubs),
                kt(fragmentStubs),
                kt(
                    """
                    package com.duckduckgo.lint

                    import androidx.fragment.app.Fragment

                    class MyFragment : Fragment() {
                        fun doWork() {
                            viewLifecycleOwner.lifecycleScope.launch { }
                        }
                    }
                    """,
                ).indented(),
            ),
        )
    }

    @Test
    fun whenViewLifecycleOwnerLifecycleScopeLaunchInInFragmentThenClean() {
        assertNoLintError(
            listOf(
                kt(lifecycleStubs),
                kt(fragmentStubs),
                kt(
                    """
                    package com.duckduckgo.lint

                    import androidx.fragment.app.Fragment

                    class MyFragment : Fragment() {
                        fun observeFlows() {
                            flow.launchIn(viewLifecycleOwner.lifecycleScope)
                        }
                    }
                    """,
                ).indented(),
            ),
        )
    }

    @Test
    fun whenLifecycleScopeInNonFragmentClassThenClean() {
        assertNoLintError(
            listOf(
                kt(lifecycleStubs),
                kt(fragmentStubs),
                kt(
                    """
                    package com.duckduckgo.lint

                    import androidx.lifecycle.LifecycleOwner

                    class MyActivity : LifecycleOwner() {
                        fun doWork() {
                            lifecycleScope.launch { }
                        }
                    }
                    """,
                ).indented(),
            ),
        )
    }

    private val lifecycleStubs = """
        package androidx.lifecycle

        open class LifecycleOwner {
            val lifecycleScope: Any = Any()
        }
    """

    private val fragmentStubs = """
        package androidx.fragment.app

        import androidx.lifecycle.LifecycleOwner

        open class Fragment : LifecycleOwner() {
            val viewLifecycleOwner: LifecycleOwner = LifecycleOwner()
        }

        open class DialogFragment : Fragment()
    """

    private fun assertLintError(files: List<TestFile>) {
        lint()
            .files(*files.toTypedArray())
            .issues(NO_LIFECYCLE_SCOPE_IN_FRAGMENT)
            .testModes(TestMode.DEFAULT)
            .run()
            .expectContains(expectedError)
    }

    private fun assertNoLintError(files: List<TestFile>) {
        lint()
            .files(*files.toTypedArray())
            .issues(NO_LIFECYCLE_SCOPE_IN_FRAGMENT)
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }

    companion object {
        private const val expectedError = "Warning: $ERROR_DESCRIPTION [$ERROR_ID]"
    }
}
