/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.lint.ui

import com.android.tools.lint.checks.infrastructure.LintDetectorTest.xml
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.Test

@Suppress("UnstableApiUsage")
class WrongStyleDetectorTest {

    @Test
    fun whenStyleCorrectThenSucces() {
        lint().files(
            xml(
                "res/xml/style.xml",
                """
                    <style name="Widget.DuckDuckGo.DaxButton.TextButton.Primary">
                        <item name="android:textColor">@color/button_primary_text_color_selector</item>
                        <item name="backgroundTint">@color/button_primary_container_selector</item>
                        <item name="rippleColor">@color/button_primary_ripple_selector</item>
                        <item name="iconTint">@color/button_primary_text_color_selector</item>
                    </style>
                """,
            ).indented(),
        )
            .issues(WrongStyleDetector.WRONG_STYLE_NAME)
            .run()
            .expectClean()
    }

    @Test
    fun whenStyleNameIsWrongThenFail() {
        lint().files(
            xml(
                "res/xml/style.xml",
                """
                    <style name="SomethingSomethingButton">
                        <item name="android:textColor">@color/button_primary_text_color_selector</item>
                        <item name="backgroundTint">@color/button_primary_container_selector</item>
                        <item name="rippleColor">@color/button_primary_ripple_selector</item>
                        <item name="iconTint">@color/button_primary_text_color_selector</item>
                    </style>
                """,
            ).indented(),
        )
            .issues(WrongStyleDetector.WRONG_STYLE_NAME)
            .run()
            .expect(
                """
                res/xml/style.xml:1: Error: Style names should follow the convention and start with Widget.DuckDuckGo. [WrongStyleName]
                <style name="SomethingSomethingButton">
                 ~~~~~
                1 errors, 0 warnings
            """,
            )
    }

    @Test
    fun whenStyleHasWidthThenFail() {
        lint().files(
            xml(
                "res/xml/style.xml",
                """
                    <style name="Widget.DuckDuckGo.DaxButton.TextButton.Primary">
                        <item name="android:layout_width">match_parent</item>
                        <item name="rippleColor">@color/button_primary_ripple_selector</item>
                        <item name="iconTint">@color/button_primary_text_color_selector</item>
                    </style>
                """,
            ).indented(),
        )
            .issues(WrongStyleDetector.WRONG_STYLE_PARAMETER)
            .run()
            .expect(
                """
                res/xml/style.xml:2: Error: Styles should not modify android:layout_height or android:layout_width [WrongStyleParameter]
                    <item name="android:layout_width">match_parent</item>
                     ~~~~
                1 errors, 0 warnings
            """,
            )
    }

    @Test
    fun whenStyleHasHeighthenFail() {
        lint().files(
            xml(
                "res/xml/style.xml",
                """
                    <style name="Widget.DuckDuckGo.DaxButton.TextButton.Primary">
                        <item name="android:layout_width">match_parent</item>
                        <item name="rippleColor">@color/button_primary_ripple_selector</item>
                        <item name="iconTint">@color/button_primary_text_color_selector</item>
                    </style>
                """,
            ).indented(),
        )
            .issues(WrongStyleDetector.WRONG_STYLE_PARAMETER)
            .run()
            .expect(
                """
                res/xml/style.xml:2: Error: Styles should not modify android:layout_height or android:layout_width [WrongStyleParameter]
                    <item name="android:layout_width">match_parent</item>
                     ~~~~
                1 errors, 0 warnings
            """,
            )
    }
}
