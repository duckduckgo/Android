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
import org.junit.Test

class MissingInstructionDetectorTest {

    @Test
    fun whenStringHasSPlaceholderAndNoInstructionThenFailWithError() {
        val expected =
            """
            res/values/strings.xml:3: Error: Missing instructions attribute [MissingInstruction]
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
            .issues(MissingInstructionDetector.MISSING_INSTRUCTION)
            .run().expect(expected)
    }

    @Test
    fun whenStringHasDPlaceholderAndNoInstructionThenFailWithError() {
        val expected =
            """
            res/values/strings.xml:3: Error: Missing instructions attribute [MissingInstruction]
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
            .issues(MissingInstructionDetector.MISSING_INSTRUCTION)
            .run().expect(expected)
    }

    @Test
    fun whenStringHasSeveralPlaceholdersAndNoInstructionThenFailWithError() {
        val expected =
            """
            res/values/strings.xml:3: Error: Missing instructions attribute [MissingInstruction]
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
            .issues(MissingInstructionDetector.MISSING_INSTRUCTION)
            .run().expect(expected)
    }

    @Test
    fun whenStringHasOrderedPlaceholdersAndNoInstructionThenFailWithError() {
        val expected =
            """
            res/values/strings.xml:3: Error: Missing instructions attribute [MissingInstruction]
                <string name="macos_windows">Windows %1$ s coming soon! %2$ s</string>
                                             ~~~~~
            1 errors, 0 warnings
            """

        TestLintTask.lint().files(
            xml(
                "res/values/strings.xml",
                """
                <?xml version="1.0" encoding="utf-8"?>
                <resources>
                    <string name="macos_windows">Windows %1$ s coming soon! %2$ s</string>
                </resources>    
                """
            ).indented())
            .skipTestModes(TestMode.CDATA)
            .issues(MissingInstructionDetector.MISSING_INSTRUCTION)
            .run().expect(expected)
    }

    @Test
    fun whenStringHasSPlaceholderAndInstructionThenSuccess() {
        TestLintTask.lint().files(
            xml(
                "res/values/strings.xml",
                """
                <?xml version="1.0" encoding="utf-8"?>
                <resources>
                    <string name="macos_windows" instruction="test">Windows coming soon! %s</string>
                </resources>    
                """
            ).indented())
            .skipTestModes(TestMode.CDATA)
            .issues(MissingInstructionDetector.MISSING_INSTRUCTION)
            .run().expectClean()
    }

    @Test
    fun whenStringHasDPlaceholderAndInstructionThenSuccess() {
        TestLintTask.lint().files(
            xml(
                "res/values/strings.xml",
                """
                <?xml version="1.0" encoding="utf-8"?>
                <resources>
                    <string name="macos_windows" instruction="">Windows coming soon! %d</string>
                </resources>    
                """
            ).indented())
            .skipTestModes(TestMode.CDATA)
            .issues(MissingInstructionDetector.MISSING_INSTRUCTION)
            .run().expectClean()
    }

    @Test
    fun whenStringHasSeveralPlaceholdersAndInstructionThenSuccess() {
        TestLintTask.lint().files(
            xml(
                "res/values/strings.xml",
                """
                <?xml version="1.0" encoding="utf-8"?>
                <resources>
                    <string name="macos_windows" instruction="test">Windows %d coming soon! %s</string>
                </resources>    
                """
            ).indented())
            .skipTestModes(TestMode.CDATA)
            .issues(MissingInstructionDetector.MISSING_INSTRUCTION)
            .run().expectClean()
    }

    @Test
    fun whenStringHasOrderedPlaceholdersAndInstructionThenSuccess() {
        TestLintTask.lint().files(
            xml(
                "res/values/strings.xml",
                """
                <?xml version="1.0" encoding="utf-8"?>
                <resources>
                    <string name="macos_windows" instruction="test">Windows %1$ s coming soon! %2$ s</string>
                </resources>    
                """
            ).indented())
            .skipTestModes(TestMode.CDATA)
            .issues(MissingInstructionDetector.MISSING_INSTRUCTION)
            .run().expectClean()
    }
    @Test
    fun whenStringHasPlaceholdersIsNotTranslatableAndNoInstructionThenSuccess() {
        TestLintTask.lint().files(
            xml(
                "res/values/strings.xml",
                """
                <?xml version="1.0" encoding="utf-8"?>
                <resources>
                    <string name="macos_windows" translatable="false">Windows %1$ s coming soon! %2$ s</string>
                </resources>    
                """
            ).indented())
            .skipTestModes(TestMode.CDATA)
            .issues(MissingInstructionDetector.MISSING_INSTRUCTION)
            .run().expectClean()
    }
}
