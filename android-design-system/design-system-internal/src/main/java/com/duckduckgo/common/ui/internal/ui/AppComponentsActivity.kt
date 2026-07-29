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

package com.duckduckgo.common.ui.internal.ui

import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.viewpager.widget.ViewPager
import com.duckduckgo.common.ui.DuckDuckGoTheme
import com.duckduckgo.common.ui.applyTheme
import com.duckduckgo.common.ui.internal.R
import com.duckduckgo.common.ui.internal.ui.store.AppComponentsPrefsDataStore
import com.duckduckgo.common.ui.internal.ui.store.appComponentsDataStore
import com.duckduckgo.common.ui.store.ThemingSharedPreferences
import com.duckduckgo.common.ui.view.listitem.OneLineListItem
import com.duckduckgo.common.utils.DefaultDispatcherProvider
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeHandler
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class AppComponentsActivity : AppCompatActivity() {

    // TODO when ADS is all compose we should start using DI for AppComponentsActivity and AppComponentsViewModel
    private val appComponentsViewModel: AppComponentsViewModel by lazy {
        ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return AppComponentsViewModel(
                        AppComponentsPrefsDataStore(
                            dispatcherProvider = DefaultDispatcherProvider(),
                            context = this@AppComponentsActivity,
                            store = appComponentsDataStore,
                            themePrefMapper = ThemingSharedPreferences.ThemePrefsMapper(),
                        ),
                    ) as T
                }
            },
        )[AppComponentsViewModel::class.java]
    }

    private lateinit var viewPager: ViewPager
    private lateinit var tabLayout: TabLayout
    private lateinit var darkThemeSwitch: OneLineListItem
    private lateinit var brandDesignSwitch: OneLineListItem
    private val edgeToEdgeHandler = EdgeToEdgeHandler()

    @Suppress("DenyListedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        val (selectedTheme, brandDesignUpdateEnabled) = runBlocking {
            val selectedTheme = appComponentsViewModel.themeFlow.first()
            val brandDesignUpdateEnabled = appComponentsViewModel.brandDesignUpdateFlow.first()
            applyTheme(selectedTheme, applyBrandDesignUpdate = brandDesignUpdateEnabled)
            selectedTheme to brandDesignUpdateEnabled
        }
        super.onCreate(savedInstanceState)
        enableTransparentEdgeToEdge(isDarkTheme = isDarkThemeEnabled(selectedTheme))
        setContentView(R.layout.activity_app_components)
        viewPager = findViewById(R.id.view_pager)
        tabLayout = findViewById(R.id.tab_layout)
        darkThemeSwitch = findViewById(R.id.dark_theme_switch)
        brandDesignSwitch = findViewById(R.id.brand_design_switch)

        configureEdgeToEdgeInsets()

        tabLayout.setupWithViewPager(viewPager)
        val adapter = AppComponentsPagerAdapter(this, supportFragmentManager)
        viewPager.adapter = adapter

        darkThemeSwitch.quietlySetIsChecked(selectedTheme == DuckDuckGoTheme.DARK) { _, enabled ->
            // TODO if we add another theme (e.g. true black) we would change the toggle and move this logic to the VM
            val newTheme = if (enabled) {
                DuckDuckGoTheme.DARK
            } else {
                DuckDuckGoTheme.LIGHT
            }
            lifecycleScope.launch {
                appComponentsViewModel.setTheme(newTheme)
                startActivity(intent(this@AppComponentsActivity))
                finish()
            }
        }

        brandDesignSwitch.quietlySetIsChecked(brandDesignUpdateEnabled) { _, enabled ->
            lifecycleScope.launch {
                appComponentsViewModel.setBrandDesignUpdate(enabled)
                startActivity(intent(this@AppComponentsActivity))
                finish()
            }
        }
    }

    private fun isDarkThemeEnabled(selectedTheme: DuckDuckGoTheme): Boolean {
        return when (selectedTheme) {
            DuckDuckGoTheme.SYSTEM_DEFAULT -> {
                val uiManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
                uiManager.nightMode == UiModeManager.MODE_NIGHT_YES
            }
            DuckDuckGoTheme.DARK -> true
            else -> false
        }
    }

    private fun enableTransparentEdgeToEdge(isDarkTheme: Boolean) {
        val barStyle = if (isDarkTheme) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
                } else {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                }
            }
        }
    }

    private fun configureEdgeToEdgeInsets() {
        edgeToEdgeHandler.applyHorizontalSystemBarInsets(findViewById<View>(R.id.app_components_root))
        edgeToEdgeHandler.applyStatusBarInsets(findViewById<View>(R.id.app_bar_layout))
        edgeToEdgeHandler.applyNavigationBarInsets(viewPager, drawBehindGestureNav = true)
        installNavigationBarScrim()
    }

    private fun installNavigationBarScrim() {
        val typedValue = TypedValue()
        if (!theme.resolveAttribute(android.R.attr.navigationBarColor, typedValue, true)) return
        val content = findViewById<ViewGroup>(android.R.id.content)
        val scrim = View(this).apply {
            setBackgroundColor(typedValue.data)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 0, Gravity.BOTTOM)
        }
        content.addView(scrim)
        ViewCompat.setOnApplyWindowInsetsListener(scrim) { v, insets ->
            v.updateLayoutParams { height = insets.getInsets(WindowInsetsCompat.Type.tappableElement()).bottom }
            insets
        }
        ViewCompat.requestApplyInsets(scrim)
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, AppComponentsActivity::class.java)
        }
    }
}
