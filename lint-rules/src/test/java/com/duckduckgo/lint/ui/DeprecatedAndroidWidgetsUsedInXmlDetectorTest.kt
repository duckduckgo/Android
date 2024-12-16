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
class DeprecatedAndroidWidgetsUsedInXmlDetectorTest {
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

                  <Button
                      android:id="@+id/fab"
                      android:layout_width="wrap_content"
                      android:layout_height="wrap_content"
                      android:tint="@android:color/white"
                      app:layout_anchor="@id/bottom_app_bar"
                      app:srcCompat="@drawable/ic_add_black_24dp"
                      tools:ignore="RtlHardcoded"/>
                </android.support.design.widget.CoordinatorLayout>
            """
                ).indented()
            )
            .issues(DeprecatedAndroidWidgetsUsedInXmlDetector.DEPRECATED_WIDGET_IN_XML)
            .run()
            .expect(
                """
                res/layout/buttons.xml:17: Error: Always favor the use of the Design System Component [DeprecatedWidgetInXml]
                  <Button
                   ~~~~~~
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
                      app:layout_anchor="@id/bottom_app_bar"
                      app:srcCompat="@drawable/ic_add_black_24dp"
                      tools:ignore="RtlHardcoded"/>
                </android.support.design.widget.CoordinatorLayout>
            """
                ).indented()
            )
            .allowCompilationErrors()
            .issues(DeprecatedAndroidWidgetsUsedInXmlDetector.DEPRECATED_WIDGET_IN_XML)
            .run()
            .expectClean()
    }

    @Test
    fun whenAppCompatSwitchFoundThenFailWithError() {
        lint()
            .files(
                TestFiles.xml(
                    "res/layout/switch.xml",
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

                <androidx.appcompat.widget.SwitchCompat
                    android:id="@+id/setAsDefaultBrowserSetting"
                    style="@style/SettingsSwitch"
                    android:paddingTop="14dp"
                    android:paddingBottom="14dp"
                    android:text="@string/setAsDefaultBrowser"
                    android:theme="@style/SettingsSwitchTheme" />


                </android.support.design.widget.CoordinatorLayout>
            """
                ).indented()
            )
            .issues(DeprecatedAndroidWidgetsUsedInXmlDetector.DEPRECATED_WIDGET_IN_XML)
            .run()
            .expect(
                """
                res/layout/switch.xml:17: Error: Always favor the use of the Design System Component [DeprecatedWidgetInXml]
                <androidx.appcompat.widget.SwitchCompat
                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
            """.trimMargin()
            )
    }

    @Test
    fun whenMaterialSwitchFoundThenFailWithError() {
        lint()
            .files(
                TestFiles.xml(
                    "res/layout/switch.xml",
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

                    <com.google.android.material.switchmaterial.SwitchMaterial
                        android:id="@+id/enabledToggle"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_marginStart="@dimen/keyline_4"
                        android:layout_marginEnd="@dimen/keyline_4"
                        android:gravity="center_vertical"
                        app:layout_constraintBottom_toBottomOf="@id/enabledToggleLabel"
                        app:layout_constraintEnd_toEndOf="parent"
                        app:layout_constraintTop_toTopOf="@id/enabledToggleLabel" />

                </android.support.design.widget.CoordinatorLayout>
            """
                ).indented()
            )
            .issues(DeprecatedAndroidWidgetsUsedInXmlDetector.DEPRECATED_WIDGET_IN_XML)
            .run()
            .expect(
                """
                res/layout/switch.xml:17: Error: Always favor the use of the Design System Component [DeprecatedWidgetInXml]
                    <com.google.android.material.switchmaterial.SwitchMaterial
                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
            """.trimMargin()
            )
    }

    @Test
    fun whenLegacySwitchFoundThenFailWithError() {
        lint()
            .files(
                TestFiles.xml(
                    "res/layout/switch.xml",
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

                <Switch
                    android:id="@+id/setAsDefaultBrowserSetting"
                    style="@style/SettingsSwitch"
                    android:paddingTop="14dp"
                    android:paddingBottom="14dp"
                    android:text="@string/setAsDefaultBrowser"
                    android:theme="@style/SettingsSwitchTheme" />


                </android.support.design.widget.CoordinatorLayout>
            """
                ).indented()
            )
            .issues(DeprecatedAndroidWidgetsUsedInXmlDetector.DEPRECATED_WIDGET_IN_XML)
            .run()
            .expect(
                """
                res/layout/switch.xml:17: Error: Always favor the use of the Design System Component [DeprecatedWidgetInXml]
                <Switch
                 ~~~~~~
                1 errors, 0 warnings
            """.trimMargin()
            )
    }

    @Test
    fun whenDesignSystemComponentSwitchFoundThenSucceed() {
        lint()
            .files(
                TestFiles.xml(
                    "res/layout/switch.xml",
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
                      
                <com.duckduckgo.mobile.android.ui.view.listitem.OneLineListItemSwitch
                      android:id="@+id/bottom_app_bar"
                      android:layout_width="match_parent"
                      android:layout_height="wrap_content"
                      android:layout_gravity="bottom"
                      app:navigationIcon="@drawable/ic_menu_black_24dp"/>

                </android.support.design.widget.CoordinatorLayout>
            """
                ).indented()
            )
            .allowCompilationErrors()
            .issues(DeprecatedAndroidWidgetsUsedInXmlDetector.DEPRECATED_WIDGET_IN_XML)
            .run()
            .expectClean()
    }
}
