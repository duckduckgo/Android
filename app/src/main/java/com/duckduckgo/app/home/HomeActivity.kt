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
import android.view.View
import android.webkit.CookieManager
import com.duckduckgo.app.about.AboutDuckDuckGoActivity
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
            consumeSharedText(intent)
        }
    }

    private fun configureToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
    }

    private fun configurePopupMenu() {
        popupMenu = BrowserPopupMenu(layoutInflater)
        popupMenu.contentView.backPopupMenuItem.isEnabled = false
        popupMenu.contentView.forwardPopupMenuItem.isEnabled = false
        popupMenu.contentView.refreshPopupMenuItem.isEnabled = false
        popupMenu.contentView.addBookmarksPopupMenuItem.isEnabled = false
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
        val anchorView = findViewById(R.id.browser_popup_menu_item) as View
        popupMenu.show(rootView, anchorView)
    }

    fun onBookmarksClicked(view: View) {
        startActivityForResult(BookmarksActivity.intent(this), BOOKMARKS_REQUEST_CODE)
        popupMenu.dismiss()
    }

    fun onSettingsClicked(view: View) {
        startActivityForResult(SettingsActivity.intent(this), SETTINGS_REQUEST_CODE)
        popupMenu.dismiss()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            SETTINGS_REQUEST_CODE -> onHandleSettingsResult(resultCode)
            BOOKMARKS_REQUEST_CODE -> onHandleBookmarksResult(resultCode, data)
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun onHandleBookmarksResult(resultCode: Int, data: Intent?) {
        when (resultCode) {
            BookmarksActivity.OPEN_URL_RESULT_CODE -> {
                openUrl(data?.action ?: return)
            }
        }
    }

    private fun onHandleSettingsResult(resultCode: Int) {
        when (resultCode) {
            AboutDuckDuckGoActivity.RESULT_CODE_LOAD_ABOUT_DDG_WEB_PAGE -> {
                openUrl(getString(R.string.aboutUrl))
            }
        }
    }

    private fun openUrl(url: String) {
        startActivity(BrowserActivity.intent(this, url))
    }

    override fun onDestroy() {
        popupMenu.dismiss()
        super.onDestroy()
    }

    companion object {

        private const val SETTINGS_REQUEST_CODE = 100
        private const val BOOKMARKS_REQUEST_CODE = 101

        fun intent(context: Context): Intent {
            return Intent(context, HomeActivity::class.java)
        }

    }
}
