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
import com.duckduckgo.lint.ui.DaxButtonStylingDetector.Companion
import com.duckduckgo.lint.ui.NoStyleAppliedToDesignSystemComponentDetector.Companion.STYLE_IN_DESIGN_SYSTEM_COMPONENT
import org.junit.Test

@Suppress("UnstableApiUsage")
class NoStyleAppliedToDesignSystemComponentDetectorTest {
    @Test
    fun whenAndroidButtonFoundThenFailWithError() {
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

                  <android.support.design.bottomappbar.BottomAppBar
                      android:id="@+id/bottom_app_bar"
                      style="@style/Widget.MaterialComponents.BottomAppBar"
                      android:layout_width="match_parent"
                      android:layout_height="wrap_content"
                      android:layout_gravity="bottom"
                      app:navigationIcon="@drawable/ic_menu_black_24dp"/>

                  <com.duckduckgo.mobile.android.ui.view.button.ButtonPrimaryLarge
                      android:id="@+id/fab"
                      android:layout_width="wrap_content"
                      android:layout_height="wrap_content"
                      android:tint="@android:color/white"
style="@style/Widget.DuckDuckGo.Button.Primary"
                      tools:ignore="RtlHardcoded"/>
                </android.support.design.widget.CoordinatorLayout>
            """
                ).indented()
            )
            .issues(STYLE_IN_DESIGN_SYSTEM_COMPONENT)
            .run()
            .expect(
                """
                res/layout/buttons.xml:17: Error: Design System Components should not be styled. Consider creating a new Component or use one of the Components already created [StyleInDesignSystemComponent]
                                  <com.duckduckgo.mobile.android.ui.view.button.ButtonPrimaryLarge
                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings 
            """.trimMargin()
            )
    }

    @Test
    fun whenAndroidButtonNotFoundThenSucceed() {
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

                  <android.support.design.bottomappbar.BottomAppBar
                      android:id="@+id/bottom_app_bar"
                      style="@style/Widget.MaterialComponents.BottomAppBar"
                      android:layout_width="match_parent"
                      android:layout_height="wrap_content"
                      android:layout_gravity="bottom"
                      app:navigationIcon="@drawable/ic_menu_black_24dp"/>

                  <com.duckduckgo.mobile.android.ui.view.button.ButtonPrimaryLarge
                      android:id="@+id/fab"
                      android:layout_width="wrap_content"
                      android:layout_height="wrap_content"
                      android:tint="@android:color/white"
                      tools:ignore="RtlHardcoded"/>
                </android.support.design.widget.CoordinatorLayout>
            """
                ).indented()
            )
            .allowCompilationErrors()
            .issues(STYLE_IN_DESIGN_SYSTEM_COMPONENT)
            .run()
            .expectClean()
    }

    @Test
    fun whenSwitchViewFoundThenFailWithError() {
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

                  <android.support.design.bottomappbar.BottomAppBar
                      android:id="@+id/bottom_app_bar"
                      style="@style/Widget.MaterialComponents.BottomAppBar"
                      android:layout_width="match_parent"
                      android:layout_height="wrap_content"
                      android:layout_gravity="bottom"
                      app:navigationIcon="@drawable/ic_menu_black_24dp"/>

                  <com.duckduckgo.mobile.android.ui.view.DaxSwitch
                      android:id="@+id/fab"
                      android:layout_width="wrap_content"
                      android:layout_height="wrap_content"
                      android:tint="@android:color/white"
                      style="@style/Widget.DuckDuckGo.Button.Primary"
                      tools:ignore="RtlHardcoded"/>
                </android.support.design.widget.CoordinatorLayout>
            """
                ).indented()
            )
            .issues(STYLE_IN_DESIGN_SYSTEM_COMPONENT)
            .run()
            .expect(
                """
                res/layout/buttons.xml:17: Error: Design System Components should not be styled. Consider creating a new Component or use one of the Components already created [StyleInDesignSystemComponent]
                  <com.duckduckgo.mobile.android.ui.view.DaxSwitch
                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
            """.trimMargin()
            )
    }

    @Test
    fun whenDaxTextViewStyleChangedThenFail() {
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

                  <com.duckduckgo.mobile.android.ui.view.text.DaxTextView
                    android:id="@+id/text"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="@dimen/keyline_2"
                    app:layout_constraintEnd_toEndOf="parent"
                    app:layout_constraintStart_toStartOf="parent"
                    app:layout_constraintTop_toBottomOf="@+id/customDialogTitle" 
                    style="@style/Widget.DuckDuckGo.Button.Primary"
                    app:textType="secondary"
                    app:typography="body1" />
                      
                </android.support.design.widget.CoordinatorLayout>
            """
                ).indented()
            )
            .issues(STYLE_IN_DESIGN_SYSTEM_COMPONENT)
            .run()
            .expect(
                """
                res/layout/buttons.xml:9: Error: Design System Components should not be styled. Consider creating a new Component or use one of the Components already created [StyleInDesignSystemComponent]
                  <com.duckduckgo.mobile.android.ui.view.text.DaxTextView
                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
            """.trimMargin()
            )
    }
}
