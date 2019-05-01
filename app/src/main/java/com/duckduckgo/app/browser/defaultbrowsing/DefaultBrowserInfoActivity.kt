/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.browser.defaultbrowsing

import android.annotation.TargetApi
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.DuckDuckGoActivity
import kotlinx.android.synthetic.main.activity_default_browser_info.*
import timber.log.Timber
import javax.inject.Inject

class DefaultBrowserInfoActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var defaultBrowserDetector: DefaultBrowserDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_default_browser_info)
        configureUiEventHandlers()
    }

    @TargetApi(Build.VERSION_CODES.N)
    private fun configureUiEventHandlers() {
        dismissButton.setOnClickListener { exitActivity() }
        launchSettingsButton.setOnClickListener { launchDefaultAppActivityForResult() }
        defaultBrowserIllustration.setOnClickListener { launchDefaultAppActivityForResult() }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun launchDefaultAppActivityForResult() {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            startActivityForResult(intent, DEFAULT_BROWSER_REQUEST_CODE)
        } catch (e: ActivityNotFoundException) {
            val errorMessage = getString(R.string.cannotLaunchDefaultAppSettings)
            Timber.w(e, errorMessage)
            Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()

        if (defaultBrowserDetector.isDefaultBrowser()) {
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            DEFAULT_BROWSER_REQUEST_CODE -> {
                val wasSet = if (defaultBrowserDetector.isDefaultBrowser()) "was" else "was not"
                Timber.i("User returned from default settings; DDG $wasSet set as the default")
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onBackPressed() {
        exitActivity()
    }

    private fun exitActivity() {
        launchSettingsButton.text = ""
        finishAfterTransition()
    }

    companion object {

        private const val DEFAULT_BROWSER_REQUEST_CODE = 100

        fun intent(context: Context): Intent {
            return Intent(context, DefaultBrowserInfoActivity::class.java)
        }
    }
}
