/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.browser.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.appbar.AppBarLayout

class BottomNavigationToolbarBehavior(context: Context, attrs: AttributeSet) : CoordinatorLayout.Behavior<BottomNavigationBar>(context, attrs) {

    override fun layoutDependsOn(parent: CoordinatorLayout, child: BottomNavigationBar, dependency: View): Boolean {
        return dependency is Toolbar
    }

    override fun onDependentViewChanged(parent: CoordinatorLayout, child: BottomNavigationBar, dependency: View): Boolean {
        val translationY: Float = getFabTranslationYForSnackbar(parent, child)
        child.translationY = translationY
        return false
    }

    private fun getFabTranslationYForSnackbar(parent: CoordinatorLayout, view: View): Float {
        var maxOffset = 0f
        val dependencies: List<*> = parent.getDependencies(view)
        dependencies.forEach {dependency ->
            if (dependency is Toolbar){
                maxOffset = Math.max(maxOffset, (dependency.translationY - dependency.height))
            }

        }

        return maxOffset
    }
}