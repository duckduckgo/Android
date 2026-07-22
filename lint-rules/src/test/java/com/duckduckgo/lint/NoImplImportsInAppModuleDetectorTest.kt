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
import com.duckduckgo.lint.NoImplImportsInAppModuleDetector.Companion.NO_IMPL_IMPORTS_IN_APP_MODULE_ISSUE
import org.junit.Test

@Suppress("UnstableApiUsage")
class NoImplImportsInAppModuleDetectorTest {

    @Test
    fun whenNonImplModuleImportsFromImplPackageThenFailWithError() {
        lint()
            .files(kt("""
                package com.duckduckgo.myfeature.api

                import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName
                import com.duckduckgo.voice.impl.VoiceSearchPixelNames

                class MyFeature {
                    fun doSomething() {
                    }
                }
            """).indented())
            .allowCompilationErrors()
            .issues(NO_IMPL_IMPORTS_IN_APP_MODULE_ISSUE)
            .run()
            .expectContains("NoImplImportsInAppModule")
            .expectContains("com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName")
    }

    @Test
    fun whenNonImplModuleImportsFromApiPackageThenSucceed() {
        lint()
            .files(kt("""
                package com.duckduckgo.myfeature.api

                import com.duckduckgo.duckchat.api.DuckChat
                import com.duckduckgo.voice.api.VoiceSearch

                class MyFeature {
                    fun doSomething() {
                    }
                }
            """).indented())
            .allowCompilationErrors()
            .issues(NO_IMPL_IMPORTS_IN_APP_MODULE_ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun whenImplModuleImportsFromImplPackageThenSucceed() {
        lint()
            .files(kt("""
                package com.duckduckgo.duckchat.impl.feature

                import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName
                import com.duckduckgo.voice.impl.VoiceSearchPixelNames

                class MyImplementation {
                    fun doSomething() {
                    }
                }
            """).indented())
            .allowCompilationErrors()
            .issues(NO_IMPL_IMPORTS_IN_APP_MODULE_ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun whenInternalModuleImportsFromImplPackageThenSucceed() {
        lint()
            .files(kt("""
                package com.duckduckgo.duckchat.internal.feature

                import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName
                import com.duckduckgo.voice.impl.VoiceSearchPixelNames

                class MyInternalImplementation {
                    fun doSomething() {
                    }
                }
            """).indented())
            .allowCompilationErrors()
            .issues(NO_IMPL_IMPORTS_IN_APP_MODULE_ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun whenImportingFromStandardLibraryThenSucceed() {
        lint()
            .files(kt("""
                package com.duckduckgo.myfeature.api

                import java.util.ArrayList
                import kotlin.collections.List

                class MyFeature {
                    fun doSomething() {
                    }
                }
            """).indented())
            .issues(NO_IMPL_IMPORTS_IN_APP_MODULE_ISSUE)
            .run()
            .expectClean()
    }
}
