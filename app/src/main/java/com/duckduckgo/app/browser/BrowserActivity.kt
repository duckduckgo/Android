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

package com.duckduckgo.app.browser

import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.app.DownloadManager
import android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.content.Intent
import android.content.Intent.EXTRA_TEXT
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.support.design.widget.Snackbar
import android.support.v4.app.ActivityCompat.requestPermissions
import android.support.v4.content.ContextCompat.checkSelfPermission
import android.webkit.URLUtil
import android.widget.Toast
import com.duckduckgo.app.bookmarks.ui.BookmarksActivity
import com.duckduckgo.app.browser.BrowserViewModel.Command
import com.duckduckgo.app.browser.BrowserViewModel.Command.*
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.ViewModelFactory
import com.duckduckgo.app.global.intentText
import com.duckduckgo.app.global.view.FireDialog
import com.duckduckgo.app.privacy.ui.PrivacyDashboardActivity
import com.duckduckgo.app.settings.SettingsActivity
import com.duckduckgo.app.tabs.TabDataRepository
import com.duckduckgo.app.tabs.TabSwitcherActivity
import kotlinx.android.synthetic.main.fragment_browser_tab.*
import org.jetbrains.anko.toast
import timber.log.Timber
import javax.inject.Inject


class BrowserActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    @Inject
    lateinit var repository: TabDataRepository

    private var currentTab: BrowserTabFragment? = null

    private val viewModel: BrowserViewModel by lazy {
        ViewModelProviders.of(this, viewModelFactory).get(BrowserViewModel::class.java)
    }

    // Used to represent a file to download, but may first require permission
    private var pendingFileDownload: PendingFileDownload? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browser)
        configureObservers()
        if (savedInstanceState == null && launchNewSearchOrQuery(intent)) {
            return
        }
        launchWithLastSavedTab()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        launchNewSearchOrQuery(intent)
    }

    private fun launchWithLastSavedTab() {
        var fragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer) as? BrowserTabFragment
        if (fragment == null) {
            val tabId = viewModel.loadInitialTab()
            fragment = BrowserTabFragment.newInstance(tabId)
            val transaction = supportFragmentManager.beginTransaction()
            transaction.replace(R.id.fragmentContainer, fragment, tabId)
            transaction.commit()
        }
        currentTab = fragment
    }

    private fun openNewTab(tabId: String, userQuery: String? = null) {
        var previousFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer) as? BrowserTabFragment
        val fragment = BrowserTabFragment.newInstance(tabId, userQuery)
        val transaction = supportFragmentManager.beginTransaction()
        if(previousFragment == null) {
            transaction.replace(R.id.fragmentContainer, fragment, tabId)
        } else {
            transaction.hide(currentTab)
            transaction.add(R.id.fragmentContainer, fragment, tabId)
        }
        transaction.commit()
        currentTab = fragment
    }

    private fun selectTab(tabId: String) {

        if (currentTab?.tabId == tabId) {
            return
        }

        val fragment = supportFragmentManager.findFragmentByTag(tabId) as? BrowserTabFragment
        if (fragment == null) {
            openNewTab(tabId)
            return
        }
        val transaction = supportFragmentManager.beginTransaction()
        transaction.hide(currentTab)
        transaction.show(fragment)
        transaction.commit()
        currentTab = fragment
    }


    private fun launchNewSearchOrQuery(intent: Intent?) : Boolean {
        if (intent == null) {
            return false
        }

        if (launchNewSearch(intent)) {
            viewModel.newSearchRequested()
            return true
        }

        val sharedText = intent.intentText
        if (sharedText != null) {
            viewModel.onSharedTextReceived(sharedText)
            return true
        }

        return false
    }

    private fun configureObservers() {
        viewModel.command.observe(this, Observer {
            processCommand(it)
        })
        repository.liveSelectedTab.observe(this, Observer {
            it?.tabId?.let {
                selectTab(it)
            }
        })
    }

    private fun processCommand(it: Command?) {
        when (it) {
            is NewTab -> launchNewTab(it.query)
            is Query -> currentTab?.submitQuery(it.query)
            is Refresh -> currentTab?.refresh()
        }
    }

    private fun launchNewSearch(intent: Intent): Boolean {
        return intent.getBooleanExtra(NEW_SEARCH_EXTRA, false) || intent.action == Intent.ACTION_ASSIST
    }

    fun launchPrivacyDashboard() {
        currentTab?.tabId?.let {
            startActivityForResult(PrivacyDashboardActivity.intent(this, it), DASHBOARD_REQUEST_CODE)
        }
    }

    fun launchFire() {
        FireDialog(context = this,
            clearStarted = { resetActivityState() },
            clearComplete = { applicationContext.toast(R.string.fireDataCleared) }
        ).show()
    }

    fun launchTabSwitcher() {
        startActivity(TabSwitcherActivity.intent(this))
    }

    fun launchNewTab(query: String? = null) {
        val tabId = repository.addNew()
        openNewTab(tabId, query)
        repository.select(tabId)
    }

    fun launchSettings() {
        startActivity(SettingsActivity.intent(this))
    }

    fun launchBookmarks() {
        startActivity(BookmarksActivity.intent(this))
    }

    fun downloadFile(url: String) {
        pendingFileDownload = PendingFileDownload(url, Environment.DIRECTORY_DOWNLOADS)
        downloadFileWithPermissionCheck()
    }

    fun downloadImage(url: String) {
        pendingFileDownload = PendingFileDownload(url, Environment.DIRECTORY_PICTURES)
        downloadFileWithPermissionCheck()
    }

    private fun downloadFileWithPermissionCheck() {
        if (hasWriteStoragePermission()) {
            downloadFile()
        } else {
            requestStoragePermission()
        }
    }

    private fun downloadFile() {
        val pending = pendingFileDownload
        pending?.let {
            val uri = Uri.parse(pending.url)
            val guessedFileName = URLUtil.guessFileName(pending.url, null, null)
            Timber.i("Guessed filename of $guessedFileName for url ${pending.url}")
            val request = DownloadManager.Request(uri).apply {
                allowScanningByMediaScanner()
                setDestinationInExternalPublicDir(pending.directory, guessedFileName)
                setNotificationVisibility(VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            }
            val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
            pendingFileDownload = null
            Toast.makeText(applicationContext, getString(R.string.webviewDownload), Toast.LENGTH_LONG).show()
        }
    }

    private fun hasWriteStoragePermission(): Boolean {
        return checkSelfPermission(this, WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestStoragePermission() {
        requestPermissions(this, arrayOf(WRITE_EXTERNAL_STORAGE), PERMISSION_REQUEST_EXTERNAL_STORAGE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == PERMISSION_REQUEST_EXTERNAL_STORAGE) {
            if ((grantResults.isNotEmpty()) && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Timber.i("Permission granted")
                downloadFile()
            } else {
                Timber.i("Permission refused")
                Snackbar.make(toolbar, R.string.permissionRequiredToDownload, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == DASHBOARD_REQUEST_CODE) {
            viewModel.receivedDashboardResult(resultCode)
        }
    }

    override fun onBackPressed() {
        if (!(currentTab?.onBackPressed() ?: false)) {
            super.onBackPressed()
        }
    }

    private fun resetActivityState() {
        //TODO clear repo and all tabs
    }

    private data class PendingFileDownload(
        val url: String,
        val directory: String
    )

    companion object {

        fun intent(context: Context, queryExtra: String? = null, newSearch: Boolean = false): Intent {
            val intent = Intent(context, BrowserActivity::class.java)
            intent.putExtra(EXTRA_TEXT, queryExtra)
            intent.putExtra(NEW_SEARCH_EXTRA, newSearch)
            return intent
        }

        private const val NEW_SEARCH_EXTRA = "NEW_SEARCH_EXTRA"
        private const val DASHBOARD_REQUEST_CODE = 100
        private const val PERMISSION_REQUEST_EXTERNAL_STORAGE = 200
    }
}