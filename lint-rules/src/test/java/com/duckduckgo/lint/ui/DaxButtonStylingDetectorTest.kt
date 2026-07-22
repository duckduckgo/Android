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
import com.duckduckgo.lint.ui.DaxButtonStylingDetector.Companion.INVALID_DAX_BUTTON_PROPERTY
import com.duckduckgo.lint.ui.NoStyleAppliedToDesignSystemComponentDetector.Companion.STYLE_IN_DESIGN_SYSTEM_COMPONENT
import org.junit.Test

@Suppress("UnstableApiUsage")
class DaxButtonStylingDetectorTest {

    @Test
    fun whenButtonHeightChangedThenFail() {
        lint()
            .files(
                TestFiles.xml(
                    "res/layout/buttons.xml",
                    """
                <android.support.design.widget.CoordinatorLayout
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:app="http://schemas.android.com/apk/res-auto"
                    xmlns:tools="http://schemas.android.com/tools"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:background="#eeeeee">
                    
                  <com.duckduckgo.common.ui.view.button.DaxButtonPrimary
                      android:id="@+id/button"
                      android:layout_width="wrap_content"
                      android:layout_height="44dp"
                      tools:ignore="RtlHardcoded"/>
                      
                </android.support.design.widget.CoordinatorLayout>
            """
                ).indented()
            )
            .issues(INVALID_DAX_BUTTON_PROPERTY)
            .run()
            .expect(
                """
                res/layout/buttons.xml:9: Error: layout_height is defined by the DaxButton Component, you shouldn't change it [InvalidDaxButtonProperty]
                  <com.duckduckgo.common.ui.view.button.DaxButtonPrimary
                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
            """.trimMargin()
            )
    }

    @Test
    fun whenButtonStyleChangedThenFail() {
        lint()
            .files(
                TestFiles.xml(
                    "res/layout/buttons.xml",
                    """
                <android.support.design.widget.CoordinatorLayout
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:app="http://schemas.android.com/apk/res-auto"
                    xmlns:tools="http://schemas.android.com/tools"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:background="#eeeeee">
                    
                  <com.duckduckgo.common.ui.view.button.DaxButtonPrimary
                      android:id="@+id/button"
                      android:layout_width="wrap_content"
                      android:layout_height="wrap_content"
                      style="@style/Widget.DuckDuckGo.Button.Primary"
                      tools:ignore="RtlHardcoded"/>
                      
                </android.support.design.widget.CoordinatorLayout>
            """
                ).indented()
            )
            .issues(INVALID_DAX_BUTTON_PROPERTY)
            .run()
            .expect(
                """
                res/layout/buttons.xml:9: Error: style is defined by the DaxButton Component, you shouldn't change it [InvalidDaxButtonProperty]
                  <com.duckduckgo.common.ui.view.button.DaxButtonPrimary
                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
            """.trimMargin()
            )
    }

    @Test
    fun whenTextStyleChangedThenFail() {
        lint()
            .files(
                TestFiles.xml(
                    "res/layout/buttons.xml",
                    """
                <android.support.design.widget.CoordinatorLayout
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:app="http://schemas.android.com/apk/res-auto"
                    xmlns:tools="http://schemas.android.com/tools"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:background="#eeeeee">
                    
                  <com.duckduckgo.common.ui.view.button.DaxButtonPrimary
                      android:id="@+id/button"
                      android:layout_width="wrap_content"
                      android:layout_height="wrap_content"
                      android:textStyle="@style/Widget.DuckDuckGo.Button.Primary"
                      tools:ignore="RtlHardcoded"/>
                      
                </android.support.design.widget.CoordinatorLayout>
            """
                ).indented()
            )
            .issues(INVALID_DAX_BUTTON_PROPERTY)
            .run()
            .expect(
                """
                res/layout/buttons.xml:9: Error: textStyle is defined by the DaxButton Component, you shouldn't change it [InvalidDaxButtonProperty]
                  <com.duckduckgo.common.ui.view.button.DaxButtonPrimary
                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
            """.trimMargin()
            )
    }

    @Test
    fun whenTextColorChangedThenFail() {
        lint()
            .files(
                TestFiles.xml(
                    "res/layout/buttons.xml",
                    """
                <android.support.design.widget.CoordinatorLayout
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:app="http://schemas.android.com/apk/res-auto"
                    xmlns:tools="http://schemas.android.com/tools"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:background="#eeeeee">
                    
                  <com.duckduckgo.common.ui.view.button.DaxButtonPrimary
                      android:id="@+id/button"
                      android:layout_width="wrap_content"
                      android:layout_height="wrap_content"
                      android:textColor="@color/red"
                      tools:ignore="RtlHardcoded"/>
                      
                </android.support.design.widget.CoordinatorLayout>
            """
                ).indented()
            )
            .issues(INVALID_DAX_BUTTON_PROPERTY)
            .run()
            .expect(
                """
                res/layout/buttons.xml:9: Error: textColor is defined by the DaxButton Component, you shouldn't change it [InvalidDaxButtonProperty]
                  <com.duckduckgo.common.ui.view.button.DaxButtonPrimary
                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
            """.trimMargin()
            )
    }

    @Test
    fun whenTextAppearanceChangedThenFail() {
        lint()
            .files(
                TestFiles.xml(
                    "res/layout/buttons.xml",
                    """
                <android.support.design.widget.CoordinatorLayout
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:app="http://schemas.android.com/apk/res-auto"
                    xmlns:tools="http://schemas.android.com/tools"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:background="#eeeeee">
                    
                  <com.duckduckgo.common.ui.view.button.DaxButtonPrimary
                      android:id="@+id/button"
                      android:layout_width="wrap_content"
                      android:layout_height="wrap_content"
                      android:textAppearance="@style/TextAppearance.MaterialComponents.Button"
                      tools:ignore="RtlHardcoded"/>
                      
                </android.support.design.widget.CoordinatorLayout>
            """
                ).indented()
            )
            .issues(INVALID_DAX_BUTTON_PROPERTY)
            .run()
            .expect(
                """
                res/layout/buttons.xml:9: Error: textAppearance is defined by the DaxButton Component, you shouldn't change it [InvalidDaxButtonProperty]
                  <com.duckduckgo.common.ui.view.button.DaxButtonPrimary
                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
            """.trimMargin()
            )
    }

    @Test
    fun whenTextAllCapsThenFail() {
        lint()
            .files(
                TestFiles.xml(
                    "res/layout/buttons.xml",
                    """
                <android.support.design.widget.CoordinatorLayout
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:app="http://schemas.android.com/apk/res-auto"
                    xmlns:tools="http://schemas.android.com/tools"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:background="#eeeeee">
                    
                  <com.duckduckgo.common.ui.view.button.DaxButtonPrimary
                      android:id="@+id/button"
                      android:layout_width="wrap_content"
                      android:layout_height="wrap_content"
                      android:textAllCaps="true"
                      tools:ignore="RtlHardcoded"/>
                      
                </android.support.design.widget.CoordinatorLayout>
            """
                ).indented()
            )
            .issues(INVALID_DAX_BUTTON_PROPERTY)
            .run()
            .expect(
                """
                res/layout/buttons.xml:9: Error: textAllCaps is defined by the DaxButton Component, you shouldn't change it [InvalidDaxButtonProperty]
                  <com.duckduckgo.common.ui.view.button.DaxButtonPrimary
                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
            """.trimMargin()
            )
    }

    @Test
    fun whenButtonTintedThenFail() {
        lint()
            .files(
                TestFiles.xml(
                    "res/layout/buttons.xml",
                    """
                <android.support.design.widget.CoordinatorLayout
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:app="http://schemas.android.com/apk/res-auto"
                    xmlns:tools="http://schemas.android.com/tools"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:background="#eeeeee">
                    
                  <com.duckduckgo.common.ui.view.button.DaxButtonPrimary
                      android:id="@+id/button"
                      android:layout_width="wrap_content"
                      android:layout_height="wrap_content"
                      android:tint="@color/red"
                      tools:ignore="RtlHardcoded"/>
                      
                </android.support.design.widget.CoordinatorLayout>
            """
                ).indented()
            )
            .issues(INVALID_DAX_BUTTON_PROPERTY)
            .run()
            .expect(
                """
                res/layout/buttons.xml:9: Error: tint is defined by the DaxButton Component, you shouldn't change it [InvalidDaxButtonProperty]
                  <com.duckduckgo.common.ui.view.button.DaxButtonPrimary
                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
            """.trimMargin()
            )
    }

    @Test
    fun whenNoStylingFoundThenSucceed() {
        lint()
            .files(
                TestFiles.xml(
                    "res/layout/buttons.xml",
                    """
                <android.support.design.widget.CoordinatorLayout
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:app="http://schemas.android.com/apk/res-auto"
                    xmlns:tools="http://schemas.android.com/tools"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:background="#eeeeee">
                    
                  <com.duckduckgo.common.ui.view.button.DaxButtonPrimary
                      android:id="@+id/button"
                      android:layout_width="wrap_content"
                      android:layout_height="wrap_content"
                      tools:ignore="RtlHardcoded"/>
                      
                </android.support.design.widget.CoordinatorLayout>
            """
                ).indented()
            )
            .allowCompilationErrors()
            .issues(INVALID_DAX_BUTTON_PROPERTY)
            .run()
            .expectClean()
    }

}
