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

import com.android.tools.lint.checks.infrastructure.TestFiles.xml
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.checks.infrastructure.TestMode
import com.duckduckgo.lint.strings.MissingSmartlingRequiredDirectivesDetector.Companion.MISSING_SMARTLING_REQUIRED_DIRECTIVES
import org.junit.Test

@Suppress("UnstableApiUsage")
class MissingSmartlingRequiredDirectivesDetectorTest {
    @Test
    fun whenStringFileHasRequiredStringThenPass() {
        TestLintTask.lint().files(
            xml(
                "res/values/strings.xml",
                """
<?xml version="1.0" encoding="UTF-8"?>
<!-- smartling.entity_escaping = false -->
<!-- smartling.instruction_attributes = instruction -->
<resources xmlns:tools="http://schemas.android.com/tools">
    <string name="appName" translatable="false">DuckDuckGo</string> 
    </resources>
                """.trimIndent()
            )
        )
            .skipTestModes(TestMode.CDATA)
            .issues(MISSING_SMARTLING_REQUIRED_DIRECTIVES)
            .run()
            .expectClean()
    }

    @Test
    fun whenStringFileHasNoRequiredStringThenFailWithErrors() {
        val expected = """
            res/values/strings.xml: Error: Missing directive: <!-- smartling.entity_escaping = false --> [MissingSmartlingRequiredDirectives]
            res/values/strings.xml: Error: Missing directive: <!-- smartling.instruction_attributes = instruction --> [MissingSmartlingRequiredDirectives]
            2 errors, 0 warnings
        """.trimIndent()

        TestLintTask.lint().files(
            xml(
                "res/values/strings.xml",
                """
<?xml version="1.0" encoding="UTF-8"?>
<resources xmlns:tools="http://schemas.android.com/tools">
    <string name="appName" translatable="false">DuckDuckGo</string> 
    </resources>
                """.trimIndent()
            )
        )
            .skipTestModes(TestMode.CDATA)
            .issues(MISSING_SMARTLING_REQUIRED_DIRECTIVES)
            .run()
            .expect(expected)
    }

    @Test
    fun whenStringFileHasNoEntityEscapingThenFailWithErrors() {
        val expected = """
            res/values/strings.xml: Error: Missing directive: <!-- smartling.entity_escaping = false --> [MissingSmartlingRequiredDirectives]
            1 errors, 0 warnings
        """.trimIndent()

        TestLintTask.lint().files(
            xml(
                "res/values/strings.xml",
                """
<?xml version="1.0" encoding="UTF-8"?>
<!-- smartling.instruction_attributes = instruction -->
<resources xmlns:tools="http://schemas.android.com/tools">
    <string name="appName" translatable="false">DuckDuckGo</string> 
    </resources>
                """.trimIndent()
            )
        )
            .skipTestModes(TestMode.CDATA)
            .issues(MISSING_SMARTLING_REQUIRED_DIRECTIVES)
            .run()
            .expect(expected)
    }

    @Test
    fun whenStringFileHasNoInstructionAttributesThenFailWithErrors() {
        val expected = """
            res/values/strings.xml: Error: Missing directive: <!-- smartling.instruction_attributes = instruction --> [MissingSmartlingRequiredDirectives]
            1 errors, 0 warnings
        """.trimIndent()

        TestLintTask.lint().files(
            xml(
                "res/values/strings.xml",
                """
<?xml version="1.0" encoding="UTF-8"?>
<!-- smartling.entity_escaping = false -->
<resources xmlns:tools="http://schemas.android.com/tools">
    <string name="appName" translatable="false">DuckDuckGo</string> 
    </resources>
                """.trimIndent()
            )
        )
            .skipTestModes(TestMode.CDATA)
            .issues(MISSING_SMARTLING_REQUIRED_DIRECTIVES)
            .run()
            .expect(expected)
    }

    @Test
    fun whenFileIsDoNotTranslateWithoutDirectivesThenPass() {
        TestLintTask.lint().files(
            xml(
                "res/values/donottranslate.xml",
                """
<?xml version="1.0" encoding="UTF-8"?>
<resources xmlns:tools="http://schemas.android.com/tools">
    <string name="appName" translatable="false">DuckDuckGo</string> 
    </resources>
                """.trimIndent()
            )
        )
            .skipTestModes(TestMode.CDATA)
            .issues(MISSING_SMARTLING_REQUIRED_DIRECTIVES)
            .run()
            .expectClean()
    }

    @Test
    fun whenFileIsNotStringWithoutDirectivesThenPass() {
        TestLintTask.lint().files(
            xml(
                "res/values/animation-settings.xml",
                """
<?xml version="1.0" encoding="UTF-8"?>
<resources>
<integer name="tab_switcher_animation_duration_ms">300</integer>
</resources>
                """.trimIndent()
            )
        )
            .skipTestModes(TestMode.CDATA)
            .issues(MISSING_SMARTLING_REQUIRED_DIRECTIVES)
            .run()
            .expectClean()
    }

    @Test
    fun whenFileIsNotInValuesWithoutDirectivesThenPass() {
        TestLintTask.lint().files(
            xml(
                "res/strings.xml",
                """
<?xml version="1.0" encoding="UTF-8"?>
<resources xmlns:tools="http://schemas.android.com/tools">
    <string name="appName" translatable="false">DuckDuckGo</string> 
    </resources>
                """.trimIndent()
            )
        )
            .skipTestModes(TestMode.CDATA)
            .issues(MISSING_SMARTLING_REQUIRED_DIRECTIVES)
            .run()
            .expectClean()
    }

    @Test
    fun whenFileIsModuleStringWithoutDirectivesThenFailWithErrors() {
        val expected = """
            res/values/strings-vpn.xml: Error: Missing directive: <!-- smartling.entity_escaping = false --> [MissingSmartlingRequiredDirectives]
            res/values/strings-vpn.xml: Error: Missing directive: <!-- smartling.instruction_attributes = instruction --> [MissingSmartlingRequiredDirectives]
            2 errors, 0 warnings
        """.trimIndent()

        TestLintTask.lint().files(
            xml(
                "res/values/strings-vpn.xml",
                """
<?xml version="1.0" encoding="UTF-8"?>
<resources xmlns:tools="http://schemas.android.com/tools">
    <string name="appName" translatable="false">DuckDuckGo</string> 
    </resources>
                """.trimIndent()
            )
        )
            .skipTestModes(TestMode.CDATA)
            .issues(MISSING_SMARTLING_REQUIRED_DIRECTIVES)
            .run()
            .expect(expected)
    }

    @Test
    fun whenFileIsInLanguageValuesFolderWithoutDirectivesThenFailWithErrors() {
        val expected = """
            res/values-bg/strings.xml: Error: Missing directive: <!-- smartling.entity_escaping = false --> [MissingSmartlingRequiredDirectives]
            res/values-bg/strings.xml: Error: Missing directive: <!-- smartling.instruction_attributes = instruction --> [MissingSmartlingRequiredDirectives]
            2 errors, 0 warnings
        """.trimIndent()

        TestLintTask.lint().files(
            xml(
                "res/values-bg/strings.xml",
                """
<?xml version="1.0" encoding="UTF-8"?>
<resources xmlns:tools="http://schemas.android.com/tools">
    <string name="appName" translatable="false">DuckDuckGo</string> 
    </resources>
                """.trimIndent()
            )
        )
            .skipTestModes(TestMode.CDATA)
            .issues(MISSING_SMARTLING_REQUIRED_DIRECTIVES)
            .run()
            .expect(expected)
    }

    @Test
    fun whenEmptyStringFileThenPass() {
        TestLintTask.lint().files(
            xml(
                "res/values/strings.xml",
                """"""
            )
        )
            .skipTestModes(TestMode.CDATA)
            .issues(MISSING_SMARTLING_REQUIRED_DIRECTIVES)
            .run()
            .expectClean()
    }

    @Test
    fun wheDirectiveIsMisplacedThenFailWithErrors() {
        val expected = """
            res/values/strings.xml: Error: Missing directive: <!-- smartling.entity_escaping = false --> [MissingSmartlingRequiredDirectives]
            1 errors, 0 warnings
        """.trimIndent()

        TestLintTask.lint().files(
            xml(
                "res/values/strings.xml",
                """
<?xml version="1.0" encoding="UTF-8"?>
<!-- smartling.instruction_attributes = instruction -->
<resources xmlns:tools="http://schemas.android.com/tools">
<!-- smartling.entity_escaping = false -->
    <string name="appName" translatable="false">DuckDuckGo</string> 
    </resources>
                """.trimIndent()
            )
        )
            .skipTestModes(TestMode.CDATA)
            .issues(MISSING_SMARTLING_REQUIRED_DIRECTIVES)
            .run()
            .expect(expected)
    }
}
