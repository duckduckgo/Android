/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.dev.settings.customtabs

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.view.setPadding
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ActivityCustomTabsInternalSettingsBinding
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserSystemSettings
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import logcat.LogPriority.WARN
import logcat.logcat

@InjectWith(ActivityScope::class)
class CustomTabsInternalSettingsActivity : DuckDuckGoActivity() {

    private val binding: ActivityCustomTabsInternalSettingsBinding by viewBinding()
    private lateinit var defaultAppActivityResultLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.toolbar)
        registerActivityResultLauncher()
        configureListeners()
        updateDefaultBrowserLabel()
    }

    private fun registerActivityResultLauncher() {
        defaultAppActivityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            updateDefaultBrowserLabel()
        }
    }

    private fun configureListeners() {
        binding.load.setOnClickListener {
            val url = binding.urlInput.text
            if (url.isNotBlank()) {
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    openCustomTab("http://$url")
                } else {
                    openCustomTab(url)
                }
            } else {
                Toast.makeText(this, getString(R.string.customTabsEmptyUrl), Toast.LENGTH_SHORT).show()
            }
        }

        binding.colorPicker.setOnClickListener {
            showColorPicker()
        }

        binding.clearColor.setOnClickListener {
            clearColor()
        }

        binding.defaultBrowser.setOnClickListener {
            launchDefaultAppActivityForResult(defaultAppActivityResultLauncher)
        }
    }

    private fun updateDefaultBrowserLabel() {
        binding.defaultBrowser.setSecondaryText(getDefaultBrowserLabel())
    }

    private fun getDefaultBrowserLabel(): String? {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://duckduckgo.com/"))
        val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)

        return resolveInfo?.let {
            try {
                val appInfo = packageManager.getApplicationInfo(it.activityInfo.packageName, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                e.printStackTrace()
                null
            }
        }
    }

    private fun clearColor() {
        binding.toolbarColorInput.text = ""
        Toast.makeText(this, "Custom color cleared", Toast.LENGTH_SHORT).show()
    }

    private fun validateColorFormat(colorText: String): Boolean {
        return try {
            if (!colorText.startsWith("#")) {
                false
            } else {
                Color.parseColor(colorText)
                true
            }
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    private fun showColorPicker() {
        val presetColors = listOf(
            "#DE5833" to "DuckDuckGo Orange",
            "#F44336" to "Red",
            "#E91E63" to "Pink",
            "#9C27B0" to "Purple",
            "#673AB7" to "Deep Purple",
            "#3F51B5" to "Indigo",
            "#2196F3" to "Blue",
            "#03A9F4" to "Light Blue",
            "#00BCD4" to "Cyan",
            "#009688" to "Teal",
            "#4CAF50" to "Green",
            "#8BC34A" to "Light Green",
            "#CDDC39" to "Lime",
            "#FFEB3B" to "Yellow",
            "#FFC107" to "Amber",
            "#FF9800" to "Orange",
            "#FFFFFF" to "White",
            "#795548" to "Brown",
            "#607D8B" to "Blue Grey",
            "#000000" to "Black",
        )

        val gridLayout = GridLayout(this).apply {
            columnCount = 4
            setPadding(32)
        }

        val colorSize = resources.displayMetrics.density * 56

        val dialog = AlertDialog.Builder(this)
            .setTitle("Pick a Color")
            .setView(gridLayout)
            .setNegativeButton("Cancel", null)
            .create()

        presetColors.forEach { (colorHex, colorName) ->
            val colorView = android.view.View(this).apply {
                layoutParams = ViewGroup.MarginLayoutParams(colorSize.toInt(), colorSize.toInt()).apply {
                    setMargins(8, 8, 8, 8)
                }
                background = ColorDrawable(Color.parseColor(colorHex))
                contentDescription = colorName
                setOnClickListener {
                    binding.toolbarColorInput.text = colorHex
                    Toast.makeText(this@CustomTabsInternalSettingsActivity, "Selected: $colorName", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            }
            gridLayout.addView(colorView)
        }

        dialog.show()
    }

    private fun openCustomTab(url: String) {
        val builder = CustomTabsIntent.Builder()

        // Apply custom toolbar color if available and valid
        val color = binding.toolbarColorInput.text
        if (color.isNotEmpty() && validateColorFormat(color)) {
            try {
                val color = Color.parseColor(color)
                val colorSchemeParams = CustomTabColorSchemeParams.Builder()
                    .setToolbarColor(color)
                    .build()
                builder.setDefaultColorSchemeParams(colorSchemeParams)
            } catch (e: IllegalArgumentException) {
                logcat(WARN) { "Failed to parse color: $color" }
            }
        }

        val customTabsIntent = builder.build()
        kotlin.runCatching {
            customTabsIntent.launchUrl(this, Uri.parse(url))
        }.onFailure {
            Toast.makeText(this, getString(R.string.customTabsInvalidUrl), Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchDefaultAppActivityForResult(activityLauncher: ActivityResultLauncher<Intent>) {
        try {
            val intent = DefaultBrowserSystemSettings.intent()
            activityLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            val errorMessage = getString(R.string.cannotLaunchDefaultAppSettings)
            logcat(WARN) { errorMessage }
            Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, CustomTabsInternalSettingsActivity::class.java)
        }
    }
}
