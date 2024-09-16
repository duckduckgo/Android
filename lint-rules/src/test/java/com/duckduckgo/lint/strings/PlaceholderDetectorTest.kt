/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.lint.strings

import com.android.tools.lint.checks.infrastructure.LintDetectorTest.xml
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.checks.infrastructure.TestMode
import com.duckduckgo.lint.strings.PlaceholderDetector.Companion.PLACEHOLDER_MISSING_POSITION
import org.junit.Test

@Suppress("UnstableApiUsage")
class PlaceholderDetectorTest {

    @Test
    fun whenStringHasSPlaceholderThenFailWithError() {
        val expected =
            """
            res/values/strings.xml:3: Error: Placeholder is missing the position in a string [PlaceholderMissingPosition]
                <string name="macos_windows">Windows coming soon! %s</string>
                                             ~~~~~
            1 errors, 0 warnings
            """

        TestLintTask.lint().files(
            xml(
                "res/values/strings.xml",
                """
                <?xml version="1.0" encoding="utf-8"?>
                <resources>
                    <string name="macos_windows">Windows coming soon! %s</string>
                </resources>    
                """
            ).indented())
            .skipTestModes(TestMode.CDATA)
            .issues(PLACEHOLDER_MISSING_POSITION)
            .run().expect(expected)
    }

    @Test
    fun whenStringHasDPlaceholderAndNoInstructionThenFailWithError() {
        val expected =
            """
            res/values/strings.xml:3: Error: Placeholder is missing the position in a string [PlaceholderMissingPosition]
                <string name="macos_windows">Windows coming soon! %d</string>
                                             ~~~~~
            1 errors, 0 warnings
            """

        TestLintTask.lint().files(
            xml(
                "res/values/strings.xml",
                """
                <?xml version="1.0" encoding="utf-8"?>
                <resources>
                    <string name="macos_windows">Windows coming soon! %d</string>
                </resources>    
                """
            ).indented())
            .skipTestModes(TestMode.CDATA)
            .issues(PLACEHOLDER_MISSING_POSITION)
            .run().expect(expected)
    }

    @Test
    fun whenStringHasSeveralPlaceholdersAndNoInstructionThenFailWithError() {
        val expected =
            """
            res/values/strings.xml:3: Error: Placeholder is missing the position in a string [PlaceholderMissingPosition]
                <string name="macos_windows">Windows %d coming soon! %s</string>
                                             ~~~~~
            1 errors, 0 warnings
            """

        TestLintTask.lint().files(
            xml(
                "res/values/strings.xml",
                """
                <?xml version="1.0" encoding="utf-8"?>
                <resources>
                    <string name="macos_windows">Windows %d coming soon! %s</string>
                </resources>    
                """
            ).indented())
            .skipTestModes(TestMode.CDATA)
            .issues(PLACEHOLDER_MISSING_POSITION)
            .run().expect(expected)
    }

    @Test
    fun whenStringHasSPlaceholderWithOrderThenSuccess() {
        TestLintTask.lint().files(
            xml(
                "res/values/strings.xml",
                """
                <?xml version="1.0" encoding="utf-8"?>
                <resources>
                    <string name="macos_windows">Windows coming soon! %1$\s</string>
                </resources>    
                """
            ).indented())
            .skipTestModes(TestMode.CDATA)
            .issues(PLACEHOLDER_MISSING_POSITION)
            .run().expectClean()
    }

    @Test
    fun whenStringHasDPlaceholderWithOrderThenSuccess() {
        TestLintTask.lint().files(
            xml(
                "res/values/strings.xml",
                """
                <?xml version="1.0" encoding="utf-8"?>
                <resources>
                    <string name="macos_windows" instruction="">Windows coming soon! %1$\d</string>
                </resources>    
                """
            ).indented())
            .skipTestModes(TestMode.CDATA)
            .issues(PLACEHOLDER_MISSING_POSITION)
            .run().expectClean()
    }

    @Test
    fun whenStringHasSeveralPlaceholdersWithOrderThenSuccess() {
        TestLintTask.lint().files(
            xml(
                "res/values/strings.xml",
                """
                <?xml version="1.0" encoding="utf-8"?>
                <resources>
                    <string name="macos_windows" instruction="test">Windows %1$\d coming soon! %2$\s</string>
                </resources>    
                """
            ).indented())
            .skipTestModes(TestMode.CDATA)
            .issues(PLACEHOLDER_MISSING_POSITION)
            .run().expectClean()
    }


    @Test
    fun whenStringHasPlaceholdersIsInDoNotTranslateFileAndNoOrderThenFailWithError() {
        val expected =
            """
            res/values/donottranslate.xml:3: Error: Placeholder is missing the position in a string [PlaceholderMissingPosition]
                <string name="macos_windows">Windows %d coming soon!</string>
                                             ~~~~~
            1 errors, 0 warnings
            """

        TestLintTask.lint().files(
            xml(
                "res/values/donottranslate.xml",
                """
                <?xml version="1.0" encoding="utf-8"?>
                <resources>
                    <string name="macos_windows">Windows %d coming soon!</string>
                </resources>    
                """
            ).indented())
            .skipTestModes(TestMode.CDATA)
            .issues(PLACEHOLDER_MISSING_POSITION)
            .run().expect(expected)
    }
}
