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
import com.android.tools.lint.checks.infrastructure.TestFiles
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import com.android.tools.lint.checks.infrastructure.TestMode
import com.duckduckgo.lint.NoPostValueOnSingleLiveEventDetector.Companion.NO_POST_VALUE_ON_SINGLE_LIVE_EVENT
import org.junit.Test

class NoPostValueOnSingleLiveEventDetectorTest {

    @Test
    fun whenPostValueOnSingleLiveEventThenError() {
        val callSite = """
            package com.duckduckgo.app
            import com.duckduckgo.common.utils.SingleLiveEvent

            class MyViewModel {
                val command = SingleLiveEvent<String>()

                fun doSomething() {
                    command.postValue("Navigate")
                }
            }
        """

        assertLintError(
            listOf(TestFiles.kotlin(callSite).indented(), TestFiles.kt(mutableLiveDataStub), TestFiles.kt(singleLiveEventStub)),
            """
                src/com/duckduckgo/app/MyViewModel.kt:8: Error: Do not use postValue() on SingleLiveEvent. Use setValue() (.value = ...) on the main thread instead. postValue() coalesces pending values, which silently drops commands. [NoPostValueOnSingleLiveEvent]
                        command.postValue("Navigate")
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
            """,
        )
    }

    @Test
    fun whenSetValueOnSingleLiveEventThenClean() {
        val callSite = """
            package com.duckduckgo.app
            import com.duckduckgo.common.utils.SingleLiveEvent

            class MyViewModel {
                val command = SingleLiveEvent<String>()

                fun doSomething() {
                    command.value = "Navigate"
                }
            }
        """

        assertNoLintError(listOf(TestFiles.kotlin(callSite).indented(), TestFiles.kt(mutableLiveDataStub), TestFiles.kt(singleLiveEventStub)))
    }

    @Test
    fun whenPostValueOnSubclassOfSingleLiveEventThenError() {
        val callSite = """
            package com.duckduckgo.app
            import com.duckduckgo.common.utils.SingleLiveEvent

            class MySingleLiveEvent<T> : SingleLiveEvent<T>()

            class MyViewModel {
                val command = MySingleLiveEvent<String>()

                fun doSomething() {
                    command.postValue("Navigate")
                }
            }
        """

        assertLintError(
            listOf(TestFiles.kotlin(callSite).indented(), TestFiles.kt(mutableLiveDataStub), TestFiles.kt(singleLiveEventStub)),
            """
                src/com/duckduckgo/app/MySingleLiveEvent.kt:10: Error: Do not use postValue() on SingleLiveEvent. Use setValue() (.value = ...) on the main thread instead. postValue() coalesces pending values, which silently drops commands. [NoPostValueOnSingleLiveEvent]
                        command.postValue("Navigate")
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
            """,
        )
    }

    @Test
    fun whenPostValueOnRegularMutableLiveDataThenClean() {
        val callSite = """
            package com.duckduckgo.app
            import androidx.lifecycle.MutableLiveData

            class MyViewModel {
                val state = MutableLiveData<String>()

                fun doSomething() {
                    state.postValue("Update")
                }
            }
        """

        assertNoLintError(listOf(TestFiles.kotlin(callSite).indented(), TestFiles.kt(mutableLiveDataStub)))
    }

    private val singleLiveEventStub = """
        package com.duckduckgo.common.utils

        import androidx.lifecycle.MutableLiveData

        class SingleLiveEvent<T> : MutableLiveData<T>() {
            override fun setValue(t: T?) {
                super.setValue(t)
            }

            fun call() {
                value = null
            }
        }
    """

    private val mutableLiveDataStub = """
        package androidx.lifecycle

        open class MutableLiveData<T> {
            open fun postValue(value: T?) {}
            open fun setValue(value: T?) {}
        }
    """

    private fun assertLintError(files: List<TestFile>, expectedError: String) {
        lint()
            .files(*files.toTypedArray())
            .issues(NO_POST_VALUE_ON_SINGLE_LIVE_EVENT)
            .testModes(TestMode.DEFAULT)
            .run()
            .expectContains(expectedError)
    }

    private fun assertNoLintError(files: List<TestFile>) {
        lint()
            .files(*files.toTypedArray())
            .issues(NO_POST_VALUE_ON_SINGLE_LIVE_EVENT)
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }
}