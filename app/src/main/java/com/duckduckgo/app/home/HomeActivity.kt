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

package com.duckduckgo.app.home

import android.content.Intent
import android.os.Bundle
import android.support.v4.app.ActivityOptionsCompat
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import com.duckduckgo.app.about.AboutDuckDuckGoActivity
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.intentText
import com.duckduckgo.app.global.view.FireDialog
import com.duckduckgo.app.settings.SettingsActivity
import kotlinx.android.synthetic.main.activity_home.*
import kotlinx.android.synthetic.main.content_home.*

class HomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        configureToolbar()

        searchInputBox.setOnClickListener { showSearchActivity() }

        if (savedInstanceState == null) {
            consumeSharedText(intent)
        }
    }

    private fun configureToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        consumeSharedText(intent)
    }

    private fun consumeSharedText(intent: Intent?) {
        val sharedText = intent?.intentText ?: return
        val browserIntent = BrowserActivity.intent(this, sharedText)
        startActivity(browserIntent)
    }

    private fun showSearchActivity() {
        val intent = BrowserActivity.intent(this)
        val options = ActivityOptionsCompat.makeSceneTransitionAnimation(this, searchInputBox, getString(R.string.transition_url_input))
        startActivity(intent, options.toBundle())
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_home_activity, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.settings_menu_item -> {
                startActivityForResult(SettingsActivity.intent(this), SETTINGS_REQUEST_CODE)
                true
            }
            R.id.fire_menu_item -> {
                launchFire()
                true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    private fun launchFire() {
        FireDialog(this, {}, {
            Toast.makeText(this, R.string.fireDataCleared, Toast.LENGTH_SHORT).show()
        }).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            SETTINGS_REQUEST_CODE -> onHandleSettingsResult(resultCode)
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun onHandleSettingsResult(resultCode: Int) {
        when (resultCode) {
            AboutDuckDuckGoActivity.RESULT_CODE_LOAD_ABOUT_DDG_WEB_PAGE -> {
                startActivity(BrowserActivity.intent(this, getString(R.string.aboutUrl)))
            }
        }
    }

    companion object {
        private const val SETTINGS_REQUEST_CODE = 100
    }

}
