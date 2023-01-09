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

package com.duckduckgo.lint.ui

import com.android.tools.lint.checks.infrastructure.TestFiles
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.Test

@Suppress("UnstableApiUsage")
class MissingDividerDetectorTest {
    @Test
    fun when1dpHeightThenFail() {
        lint()
            .files(
                TestFiles.xml(
                    "res/layout/view.xml",
                    """
                <android.support.design.widget.CoordinatorLayout
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:app="http://schemas.android.com/apk/res-auto"
                    xmlns:tools="http://schemas.android.com/tools"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:background="#eeeeee">

                  <View
                      android:id="@+id/divider"
                      android:layout_width="match_parent"
                      android:layout_height="1dp"/>
                      
                </android.support.design.widget.CoordinatorLayout>
            """
                ).indented()
            )
            .issues(MissingDividerDetector.MISSING_HORIZONTAL_DIVIDER)
            .run()
            .expect(
                """
                res/layout/view.xml:9: Error: 1dp height used in a View. Please, use the [HorizontalDivider] Component from the Design System [MissingHorizontalDivider]
                  <View
                   ~~~~
                1 errors, 0 warnings
            """.trimMargin()
            )
    }

    @Test
    fun when1dpWidthThenFail() {
        lint()
            .files(
                TestFiles.xml(
                    "res/layout/view.xml",
                    """
                <android.support.design.widget.CoordinatorLayout
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:app="http://schemas.android.com/apk/res-auto"
                    xmlns:tools="http://schemas.android.com/tools"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:background="#eeeeee">

                  <View
                      android:id="@+id/divider"
                      android:layout_width="1dp"
                      android:layout_height="match_parent"/>
                      
                </android.support.design.widget.CoordinatorLayout>
            """
                ).indented()
            )
            .issues(MissingDividerDetector.MISSING_VERTICAL_DIVIDER)
            .run()
            .expect(
                """
                res/layout/view.xml:9: Error: 1dp width used in a View. Please, use the [VerticalDivider] Component from the Design System [MissingVerticalDivider]
                  <View
                   ~~~~
                1 errors, 0 warnings
            """.trimMargin()
            )
    }
}
