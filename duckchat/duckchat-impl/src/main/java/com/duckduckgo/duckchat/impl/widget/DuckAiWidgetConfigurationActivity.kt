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
package com.duckduckgo.duckchat.impl.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.codegen.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeHandler
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckchat.impl.databinding.ActivityDuckAiWidgetConfigurationBinding
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Launched by the launcher when a Duck.ai widget is placed and, on Android 12+, when the user reconfigures it.
 * Below Android 12 a cancelled configuration makes the launcher discard the widget, so the result is OK from the start.
 */
@InjectWith(ActivityScope::class)
class DuckAiWidgetConfigurationActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var widgetPreferences: DuckAiWidgetPreferences

    @Inject
    lateinit var edgeToEdgeHandler: EdgeToEdgeHandler

    private val binding: ActivityDuckAiWidgetConfigurationBinding by viewBinding()

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var showRecentChats = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        setResult(RESULT_OK, resultIntent())

        enableTransparentEdgeToEdge()
        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)
        edgeToEdgeHandler.applyHorizontalSystemBarInsets(binding.root)
        edgeToEdgeHandler.applyStatusBarInsets(binding.includeToolbar.appBarLayout)
        edgeToEdgeHandler.applyNavigationBarInsets(binding.contentScrollView, drawBehindGestureNav = true)

        lifecycleScope.launch {
            showRecentChats = widgetPreferences.showRecentChats(appWidgetId)
            binding.showRecentChatsToggle.setIsChecked(showRecentChats)
            binding.showRecentChatsToggle.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked != showRecentChats) {
                    showRecentChats = isChecked
                    apply()
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun apply() {
        lifecycleScope.launch {
            widgetPreferences.setShowRecentChats(appWidgetId, showRecentChats)

            val updateIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                component = ComponentName(this@DuckAiWidgetConfigurationActivity, DuckAiWidgetProvider::class.java)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
            }
            sendBroadcast(updateIntent)
        }
    }

    private fun resultIntent() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}
