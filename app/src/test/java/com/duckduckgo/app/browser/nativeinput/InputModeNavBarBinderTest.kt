/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.app.browser.nativeinput

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InputModeNavBarBinderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private var back = 0
    private var fire = 0
    private var tabs = 0
    private var menu = 0

    private fun bind(navBar: View) = bindInputModeNavBar(
        navBarView = navBar,
        onBack = { back++ },
        onFire = { fire++ },
        onTabs = { tabs++ },
        onBrowserMenu = { menu++ },
    )

    private fun navBarWithAllButtons(): View = FrameLayout(context).apply {
        addView(View(context).apply { id = R.id.inputModeWidgetNavBack })
        addView(View(context).apply { id = R.id.inputFieldFireButton })
        addView(View(context).apply { id = R.id.inputFieldTabsMenu })
        addView(View(context).apply { id = R.id.inputFieldBrowserMenu })
    }

    @Test
    fun whenBackClickedThenOnlyOnBackInvoked() {
        val navBar = navBarWithAllButtons()
        bind(navBar)

        navBar.findViewById<View>(R.id.inputModeWidgetNavBack).performClick()

        assertEquals(1, back)
        assertEquals(0, fire)
        assertEquals(0, tabs)
        assertEquals(0, menu)
    }

    @Test
    fun whenFireClickedThenOnlyOnFireInvoked() {
        val navBar = navBarWithAllButtons()
        bind(navBar)

        navBar.findViewById<View>(R.id.inputFieldFireButton).performClick()

        assertEquals(0, back)
        assertEquals(1, fire)
        assertEquals(0, tabs)
        assertEquals(0, menu)
    }

    @Test
    fun whenTabsClickedThenOnlyOnTabsInvoked() {
        val navBar = navBarWithAllButtons()
        bind(navBar)

        navBar.findViewById<View>(R.id.inputFieldTabsMenu).performClick()

        assertEquals(0, back)
        assertEquals(0, fire)
        assertEquals(1, tabs)
        assertEquals(0, menu)
    }

    @Test
    fun whenBrowserMenuClickedThenOnlyOnBrowserMenuInvoked() {
        val navBar = navBarWithAllButtons()
        bind(navBar)

        navBar.findViewById<View>(R.id.inputFieldBrowserMenu).performClick()

        assertEquals(0, back)
        assertEquals(0, fire)
        assertEquals(0, tabs)
        assertEquals(1, menu)
    }

    @Test
    fun whenButtonsMissingThenBindingIsNullSafe() {
        // Only the back button present; the binder must skip the absent views without crashing.
        val navBar = FrameLayout(context).apply {
            addView(View(context).apply { id = R.id.inputModeWidgetNavBack })
        }

        bind(navBar)
        navBar.findViewById<View>(R.id.inputModeWidgetNavBack).performClick()

        assertEquals(1, back)
    }
}
