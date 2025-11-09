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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
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

    @Suppress("DenyListedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        val selectedTheme = runBlocking {
            val selectedTheme = appComponentsViewModel.themeFlow.first()
            applyTheme(selectedTheme)
            selectedTheme
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_components)
        viewPager = findViewById(R.id.view_pager)
        tabLayout = findViewById(R.id.tab_layout)
        darkThemeSwitch = findViewById(R.id.dark_theme_switch)

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
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, AppComponentsActivity::class.java)
        }
    }
}
