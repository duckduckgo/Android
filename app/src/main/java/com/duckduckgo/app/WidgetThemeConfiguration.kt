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

package com.duckduckgo.app

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ActivityWidgetConfigurationBinding
import com.duckduckgo.app.global.DuckDuckGoActivity

class WidgetThemeConfiguration : DuckDuckGoActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityWidgetConfigurationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val extras = intent.extras
        extras?.let {
            appWidgetId = it.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }

        val resultValue = Intent()
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_CANCELED, resultValue)

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
        }

        binding.widgetConfigAddWidgetButton.setOnClickListener {
            val themeSelected = binding.widgetConfigThemeRadioGroup.checkedRadioButtonId
            when (themeSelected) {
                R.id.widgetConfigThemeLight -> {
                    Toast.makeText(this, "Light", Toast.LENGTH_LONG).show()
                }
                R.id.widgetConfigThemeDark -> {
                    Toast.makeText(this, "Dark", Toast.LENGTH_LONG).show()
                }
            }
            storeAndSubmitConfiguration()
        }
    }

    private fun storeAndSubmitConfiguration() {

        val resultValue = Intent()
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_OK, resultValue)
        finish()
    }
}