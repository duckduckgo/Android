/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.mobile.android.themepreview.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.viewpager.widget.ViewPager
import com.duckduckgo.mobile.android.R
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.tabs.TabLayout

class AppComponentsActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager
    private lateinit var tabLayout: TabLayout
    private lateinit var darkThemeSwitch: SwitchMaterial

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_components)
        viewPager = findViewById(R.id.view_pager)
        tabLayout = findViewById(R.id.tab_layout)
        darkThemeSwitch = findViewById(R.id.dark_theme_switch)

        tabLayout.setupWithViewPager(viewPager)
        val adapter = AppComponentsPagerAdapter(this, supportFragmentManager)
        viewPager.adapter = adapter

        darkThemeSwitch.setOnCheckedChangeListener { _, checked ->
           if (checked){
               delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_YES
           } else {
               delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_NO
           }
        }
    }

}