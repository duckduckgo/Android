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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v4.app.ActivityOptionsCompat
import android.view.Menu
import android.view.MenuItem
import android.webkit.CookieManager
import com.duckduckgo.app.bookmarks.ui.BookmarksActivity
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.BrowserPopupMenu
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.intentText
import com.duckduckgo.app.global.view.FireDialog
import com.duckduckgo.app.settings.SettingsActivity
import kotlinx.android.synthetic.main.activity_home.*
import kotlinx.android.synthetic.main.content_home.*
import kotlinx.android.synthetic.main.popup_window_browser_menu.view.*
import org.jetbrains.anko.toast
import javax.inject.Inject
import javax.inject.Provider

class HomeActivity : DuckDuckGoActivity() {

    private lateinit var popupMenu: BrowserPopupMenu

    @Inject
    lateinit var cookieManagerProvider: Provider<CookieManager>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        configureToolbar()
        configurePopupMenu()

        searchInputBox.setOnClickListener { showSearchActivity() }

        if (savedInstanceState == null) {
            consumeIntentAction(intent)
        }
    }

    private fun configureToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
    }

    private fun configurePopupMenu() {
        popupMenu = BrowserPopupMenu(layoutInflater)
        val view = popupMenu.contentView
        popupMenu.apply {
            enableMenuOption(view.bookmarksPopupMenuItem) { launchBookmarks() }
            enableMenuOption(view.settingsPopupMenuItem) { launchSettings() }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        consumeIntentAction(intent)
    }

    private fun consumeIntentAction(intent: Intent?) {

        if (intent == null) return

        if (shouldSkipHomeActivity(intent)) {
            startActivity(BrowserActivity.intent(this))
            return
        }

        val query = intent.intentText ?: return
        startActivity(BrowserActivity.intent(this, query))
    }

    private fun shouldSkipHomeActivity(intent: Intent) : Boolean {
        return intent.hasExtra(KEY_SKIP_HOME) || intent.action == Intent.ACTION_ASSIST
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
            R.id.fire_menu_item -> {
                launchFire()
                true
            }
            R.id.browser_popup_menu_item -> {
                launchPopupMenu()
                true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    private fun launchFire() {
        FireDialog(context = this,
            cookieManager = cookieManagerProvider.get(),
            clearStarted = {},
            clearComplete = { toast(R.string.fireDataCleared) }).show()
    }

    private fun launchPopupMenu() {
        popupMenu.show(rootView, toolbar)
    }

    fun launchBookmarks() {
        startActivity(BookmarksActivity.intent(this))
    }

    fun launchSettings() {
        startActivity(SettingsActivity.intent(this))
    }

    override fun onDestroy() {
        popupMenu.dismiss()
        super.onDestroy()
    }

    companion object {

        fun intent(context: Context, query: String? = null): Intent {
            val intent = Intent(context, HomeActivity::class.java)
            query?.let {
                intent.putExtra(Intent.EXTRA_TEXT, query)
            }
            return intent
        }

        fun launchSkipHome(context: Context) : Intent {
            val intent = intent(context)
            intent.putExtra(KEY_SKIP_HOME, true)
            return intent
        }

        const val KEY_SKIP_HOME: String = "KEY_SKIP_HOME"
    }
}
