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
import com.duckduckgo.lint.ui.DaxTextViewStylingDetector.Companion.INVALID_DAX_TEXT_VIEW_PROPERTY
import com.duckduckgo.lint.ui.NoStyleAppliedToDesignSystemComponentDetector.Companion.STYLE_IN_DESIGN_SYSTEM_COMPONENT
import org.junit.Test

@Suppress("UnstableApiUsage")
class DaxTextViewStylingDetectorTest {

    @Test
    fun whenDaxTextViewTextStyleChangedThenFail() {
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
                    android:textStyle="@style/Typography.DuckDuckGo.Body1"
                    app:textType="secondary"
                    app:typography="body1" />
 
                </android.support.design.widget.CoordinatorLayout>
            """
                ).indented()
            )
            .issues(INVALID_DAX_TEXT_VIEW_PROPERTY)
            .run()
            .expect(
                """
                res/layout/buttons.xml:9: Error: textStyle is defined by the DaxTextView Component, you shouldn't change it [InvalidDaxTextViewProperty]
                <com.duckduckgo.mobile.android.ui.view.text.DaxTextView
                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
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
                    
                <com.duckduckgo.mobile.android.ui.view.text.DaxTextView
                    android:id="@+id/text"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="@dimen/keyline_2"
                    app:layout_constraintEnd_toEndOf="parent"
                    app:layout_constraintStart_toStartOf="parent"
                    app:layout_constraintTop_toBottomOf="@+id/customDialogTitle" 
                    android:textAppearance="@style/Typography.DuckDuckGo.Body1"
                    app:textType="secondary"
                    app:typography="body1" />
 
                </android.support.design.widget.CoordinatorLayout>
            """
                ).indented()
            )
            .issues(INVALID_DAX_TEXT_VIEW_PROPERTY)
            .run()
            .expect(
                """
                res/layout/buttons.xml:9: Error: textAppearance is defined by the DaxTextView Component, you shouldn't change it [InvalidDaxTextViewProperty]
                <com.duckduckgo.mobile.android.ui.view.text.DaxTextView
                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
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
                    
                <com.duckduckgo.mobile.android.ui.view.text.DaxTextView
                    android:id="@+id/text"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="@dimen/keyline_2"
                    app:layout_constraintEnd_toEndOf="parent"
                    app:layout_constraintStart_toStartOf="parent"
                    app:layout_constraintTop_toBottomOf="@+id/customDialogTitle" 
                    android:textAllCaps="true"
                    app:textType="secondary"
                    app:typography="body1" />
 
                </android.support.design.widget.CoordinatorLayout>
            """
                ).indented()
            )
            .issues(INVALID_DAX_TEXT_VIEW_PROPERTY)
            .run()
            .expect(
                """
                res/layout/buttons.xml:9: Error: textAllCaps is defined by the DaxTextView Component, you shouldn't change it [InvalidDaxTextViewProperty]
                <com.duckduckgo.mobile.android.ui.view.text.DaxTextView
                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
            """.trimMargin()
            )
    }

    @Test
    fun whenTintThenFail() {
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
                    android:tint="@color/red"
                    app:textType="secondary"
                    app:typography="body1" />
 
                </android.support.design.widget.CoordinatorLayout>
            """
                ).indented()
            )
            .issues(INVALID_DAX_TEXT_VIEW_PROPERTY)
            .run()
            .expect(
                """
                res/layout/buttons.xml:9: Error: tint is defined by the DaxTextView Component, you shouldn't change it [InvalidDaxTextViewProperty]
                <com.duckduckgo.mobile.android.ui.view.text.DaxTextView
                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
            """.trimMargin()
            )
    }

    @Test
    fun whenTextSizeChangedThenFail() {
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
                    android:textSize="12sp"
                    app:textType="secondary"
                    app:typography="body1" />
                      
                </android.support.design.widget.CoordinatorLayout>
            """
                ).indented()
            )
            .issues(INVALID_DAX_TEXT_VIEW_PROPERTY)
            .run()
            .expect(
                """
                res/layout/buttons.xml:9: Error: textSize is defined by the DaxTextView Component, you shouldn't change it [InvalidDaxTextViewProperty]
                <com.duckduckgo.mobile.android.ui.view.text.DaxTextView
                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
            """.trimMargin()
            )
    }

    @Test
    fun whenFontChangedThenFail() {
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
                    android:fontFamily="@font/roboto_mono"
                    app:textType="secondary"
                    app:typography="body1" />
                      
                </android.support.design.widget.CoordinatorLayout>
            """
                ).indented()
            )
            .issues(INVALID_DAX_TEXT_VIEW_PROPERTY)
            .run()
            .expect(
                """
                res/layout/buttons.xml:9: Error: fontFamily is defined by the DaxTextView Component, you shouldn't change it [InvalidDaxTextViewProperty]
                <com.duckduckgo.mobile.android.ui.view.text.DaxTextView
                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
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
                    
                <com.duckduckgo.mobile.android.ui.view.text.DaxTextView
                    android:id="@+id/text"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="@dimen/keyline_2"
                    app:layout_constraintEnd_toEndOf="parent"
                    app:layout_constraintStart_toStartOf="parent"
                    app:layout_constraintTop_toBottomOf="@+id/customDialogTitle"
                    app:textType="secondary"
                    app:typography="body1" />
                      
                </android.support.design.widget.CoordinatorLayout>
            """
                ).indented()
            )
            .allowCompilationErrors()
            .issues(INVALID_DAX_TEXT_VIEW_PROPERTY)
            .run()
            .expectClean()
    }

}
