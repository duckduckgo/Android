/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.global

import android.annotation.SuppressLint
import android.app.UiModeManager
import android.content.BroadcastReceiver
import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.duckduckgo.mobile.android.R
import com.duckduckgo.mobile.android.ui.DuckDuckGoTheme
import com.duckduckgo.mobile.android.ui.DuckDuckGoTheme.DARK
import com.duckduckgo.mobile.android.ui.applyTheme
import com.duckduckgo.mobile.android.ui.store.ThemingDataStore
import dagger.android.AndroidInjection
import dagger.android.DaggerActivity
import javax.inject.Inject

abstract class DuckDuckGoActivity : DaggerActivity() {

    @Inject lateinit var viewModelFactory: ActivityViewModelFactory

    @Inject lateinit var themingDataStore: ThemingDataStore

    private var themeChangeReceiver: BroadcastReceiver? = null

    @SuppressLint("MissingSuperCall")
    override fun onCreate(savedInstanceState: Bundle?) {
        onCreate(savedInstanceState, true)
    }

    /**
     * We need to conditionally defer the Dagger initialization in certain places. So if this method
     * is called from an Activity with daggerInject=false, you'll probably need to call
     * daggerInject() directly.
     */
    fun onCreate(
        savedInstanceState: Bundle?,
        daggerInject: Boolean = true
    ) {
        if (daggerInject) daggerInject()
        themeChangeReceiver = applyTheme(themingDataStore.theme)
        super.onCreate(savedInstanceState)
    }

    protected fun daggerInject() {
        AndroidInjection.inject(this)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        themeChangeReceiver?.let {
            LocalBroadcastManager.getInstance(applicationContext).unregisterReceiver(it)
        }
        super.onDestroy()
    }

    fun setupToolbar(toolbar: Toolbar) {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationIcon(R.drawable.ic_back_24)
    }

    fun isDarkThemeEnabled(): Boolean {
        return when (themingDataStore.theme) {
            DuckDuckGoTheme.SYSTEM_DEFAULT -> {
                val uiManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
                when (uiManager.nightMode) {
                    UiModeManager.MODE_NIGHT_YES -> true
                    else -> false
                }
            }
            DARK -> true
            else -> false
        }
    }

    protected inline fun <reified V : ViewModel> bindViewModel() = lazy {
        ViewModelProvider(this, viewModelFactory).get(V::class.java)
    }
}
