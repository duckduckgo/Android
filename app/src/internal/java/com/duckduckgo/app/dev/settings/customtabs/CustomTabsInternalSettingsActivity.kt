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

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
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

    private fun openCustomTab(url: String) {
        val customTabsIntent = CustomTabsIntent.Builder().build()
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
