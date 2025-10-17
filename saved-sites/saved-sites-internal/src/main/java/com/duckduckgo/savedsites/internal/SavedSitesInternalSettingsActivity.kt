/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.savedsites.internal

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.BundleCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.tabs.BrowserNav
import com.duckduckgo.autofill.api.ImportFromGoogle
import com.duckduckgo.autofill.api.ImportFromGoogle.ImportFromGoogleResult
import com.duckduckgo.browser.api.ui.BrowserScreens
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.savedsites.api.service.ImportSavedSitesResult
import com.duckduckgo.savedsites.api.service.SavedSitesImporter.ImportFolder
import com.duckduckgo.savedsites.impl.importing.ImportFromGoogleBookmarksPreImportDialog
import com.duckduckgo.savedsites.impl.importing.ImportFromGoogleBookmarksPreImportDialog.ImportBookmarksPreImportResult
import com.duckduckgo.savedsites.impl.importing.TakeoutBookmarkImporter
import com.duckduckgo.savedsites.internal.databinding.ActivitySavedSitesInternalSettingsBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.logcat
import javax.inject.Inject

@InjectWith(ActivityScope::class)
class SavedSitesInternalSettingsActivity : DuckDuckGoActivity() {
    private val binding: ActivitySavedSitesInternalSettingsBinding by viewBinding()

    @Inject
    lateinit var browserNav: BrowserNav

    @Inject
    lateinit var dispatchers: DispatcherProvider

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var takeoutBookmarkImporter: TakeoutBookmarkImporter

    @Inject
    lateinit var importFromGoogle: ImportFromGoogle

    private val importBookmarksFileLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val selectedFile = result.data?.data
                if (selectedFile != null) {
                    hidePreImportDialog()
                    processSelectedBookmarkFile(selectedFile)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)
        configureImportBookmarksEventHandlers()
        setupBookmarksPreImportDialogResultListener()
    }

    private fun hidePreImportDialog() {
        supportFragmentManager.findFragmentByTag(TAG_PRE_IMPORT_BOOKMARKS)?.let { fragment ->
            (fragment as? DialogFragment)?.dismiss()
        }
    }

    private fun configureImportBookmarksEventHandlers() {
        binding.importBookmarksLaunchGoogleTakeoutWebpage.setClickListener {
            lifecycleScope.launch(dispatchers.io()) {
                val url = "https://takeout.google.com"
                startActivity(browserNav.openInNewTab(this@SavedSitesInternalSettingsActivity, url))
            }
        }
        binding.importBookmarksLaunchGoogleTakeoutCustomFlowWithPreImportDialog.setClickListener {
            val dialog = ImportFromGoogleBookmarksPreImportDialog.instance()
            dialog.show(supportFragmentManager, TAG_PRE_IMPORT_BOOKMARKS)
        }
        binding.importBookmarksLaunchGoogleTakeoutCustomFlow.setClickListener {
            launchImportBookmarksFlow()
        }

        binding.googleLogoutButton.setClickListener {
            clearGoogleCookies()
        }

        binding.viewBookmarks.setClickListener {
            globalActivityStarter.start(this, BrowserScreens.BookmarksScreenNoParams)
        }
    }

    private fun launchBookmarkImportChooseFile() {
        val intent = Intent()
            .setType("text/html")
            .setAction(Intent.ACTION_GET_CONTENT)
            .addCategory(Intent.CATEGORY_OPENABLE)

        importBookmarksFileLauncher.launch(
            Intent.createChooser(intent, getString(R.string.savedSitesInternalSettingsSelectBookmarksFile)),
        )
    }

    private fun processSelectedBookmarkFile(fileUri: Uri) {
        lifecycleScope.launch(dispatchers.io()) {
            logcat { "Processing bookmark file: $fileUri" }
            processHtmlFile(fileUri)
        }
    }

    private suspend fun processHtmlFile(htmlUri: Uri) {
        importBookmarksFromTempFile(htmlUri)
    }

    private suspend fun importBookmarksFromTempFile(fileUri: Uri) {
        val destination = ImportFolder.Root

        when (val importResult = takeoutBookmarkImporter.importBookmarks(fileUri, destination)) {
            is ImportSavedSitesResult.Success -> {
                val count = importResult.savedSites.size
                logcat { "Successfully imported $count bookmarks" }
                withContext(dispatchers.main()) {
                    "Successfully imported $count bookmarks".showSnackbar()
                }
            }
            is ImportSavedSitesResult.Error -> {
                logcat { "Failed to import bookmarks: ${importResult.exception.message}" }
                withContext(dispatchers.main()) {
                    "Failed to import bookmarks: ${importResult.exception.message}".showSnackbar()
                }
            }
        }
    }

    private fun setupBookmarksPreImportDialogResultListener() {
        val fragmentResultKey = ImportFromGoogleBookmarksPreImportDialog.FRAGMENT_RESULT_KEY
        val bundleResultKey = ImportFromGoogleBookmarksPreImportDialog.BUNDLE_RESULT_KEY
        supportFragmentManager.setFragmentResultListener(fragmentResultKey, this) { _, bundle ->
            val result = BundleCompat.getParcelable(bundle, bundleResultKey, ImportBookmarksPreImportResult::class.java)

            when (result) {
                ImportBookmarksPreImportResult.ImportBookmarksFromGoogle -> launchImportBookmarksFlow()
                ImportBookmarksPreImportResult.SelectBookmarksFile -> launchBookmarkImportChooseFile()
                ImportBookmarksPreImportResult.Cancel, null -> hidePreImportDialog()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun launchImportBookmarksFlow() {
        lifecycleScope.launch {
            val intent = importFromGoogle.getBookmarksImportLaunchIntent()
            if (intent != null) {
                startActivityForResult(intent, GOOGLE_BOOKMARK_IMPORT_REQUEST_CODE)
            } else {
                "Import feature not available".showSnackbar()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            GOOGLE_BOOKMARK_IMPORT_REQUEST_CODE -> {
                lifecycleScope.launch {
                    val result = importFromGoogle.parseResult(data)

                    logcat { "Bookmark-import: onActivityResult for Google Bookmark import flow. result=$result" }

                    when (result) {
                        is ImportFromGoogleResult.Success -> hidePreImportDialog()
                        is ImportFromGoogleResult.Error -> hidePreImportDialog()
                        is ImportFromGoogleResult.UserCancelled -> {
                            // User cancelled, no action needed
                        }
                    }
                }
            }
        }
    }

    private fun clearGoogleCookies() {
        val cookieManager = CookieManager.getInstance()
        val domain = ".google.com"

        cookieManager.getCookie(domain)?.let { cookies ->
            cookies.split(";").forEach { cookie ->
                val cookieName = cookie.substringBefore("=").trim()
                cookieManager.setCookie(domain, "$cookieName=; Max-Age=0; Path=/")
            }
        }
        cookieManager.flush()

        Toast.makeText(this, R.string.savedSitesInternalSettingsGoogleLogoutSuccess, Toast.LENGTH_SHORT).show()
    }

    private fun String.showSnackbar(duration: Int = Snackbar.LENGTH_LONG) {
        Snackbar.make(binding.root, this, duration).show()
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, SavedSitesInternalSettingsActivity::class.java)

        private const val TAG_PRE_IMPORT_BOOKMARKS = "ImportBookmarksPreImportDialog"
        private const val GOOGLE_BOOKMARK_IMPORT_REQUEST_CODE = 200
    }
}
