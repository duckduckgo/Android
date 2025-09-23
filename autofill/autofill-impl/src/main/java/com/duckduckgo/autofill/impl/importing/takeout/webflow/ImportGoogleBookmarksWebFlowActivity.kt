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

package com.duckduckgo.autofill.impl.importing.takeout.webflow

import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import androidx.core.os.BundleCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.autofill.api.AutofillImportBookmarksLaunchSource
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.impl.databinding.ActivityImportGoogleBookmarksWebflowBinding
import com.duckduckgo.autofill.impl.importing.takeout.webflow.ImportGoogleBookmark.AutofillImportViaGoogleTakeoutScreen
import com.duckduckgo.autofill.impl.importing.takeout.webflow.ImportGoogleBookmark.AutofillImportViaGoogleTakeoutScreenResultError
import com.duckduckgo.autofill.impl.importing.takeout.webflow.ImportGoogleBookmark.AutofillImportViaGoogleTakeoutScreenResultSuccess
import com.duckduckgo.autofill.impl.importing.takeout.webflow.ImportGoogleBookmark.ImportViaGoogleTakeoutScreen
import com.duckduckgo.autofill.impl.importing.takeout.webflow.UserCannotImportReason.Unknown
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams
import com.duckduckgo.navigation.api.getActivityParams
import kotlinx.parcelize.Parcelize
import logcat.logcat

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(AutofillImportViaGoogleTakeoutScreen::class)
@ContributeToActivityStarter(AutofillImportViaGoogleTakeoutScreenResultSuccess::class)
@ContributeToActivityStarter(AutofillImportViaGoogleTakeoutScreenResultError::class)
class ImportGoogleBookmarksWebFlowActivity : DuckDuckGoActivity(), ImportGoogleBookmarksWebFlowFragment.WebViewVisibilityListener {
    val binding: ActivityImportGoogleBookmarksWebflowBinding by viewBinding()

    private var isInResultState = false

    private val launchSource: AutofillImportBookmarksLaunchSource by lazy {
        intent.getActivityParams(ImportViaGoogleTakeoutScreen::class.java)?.launchSource
            ?: AutofillImportBookmarksLaunchSource.Unknown
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        configureToolbar()
        configureResultListeners()

        // Check if we should show the results screen immediately
        val successResult = intent.getActivityParams(AutofillImportViaGoogleTakeoutScreenResultSuccess::class.java)
        val errorResult = intent.getActivityParams(AutofillImportViaGoogleTakeoutScreenResultError::class.java)

        when {
            successResult != null -> showSuccessFragment(successResult.bookmarkCount)
            errorResult != null -> showErrorFragment(errorResult.errorReason)
            else -> launchWebFlow()
        }
    }

    private fun launchWebFlow() {
        logcat { "Bookmark-import: Starting webflow" }
        isInResultState = false
        replaceFragment(ImportGoogleBookmarksWebFlowFragment())
    }

    override fun onWebViewVisibilityChanged(showWebView: Boolean) {
        handleWebFlowStateChange(showWebView)
    }

    private fun showSuccessFragment(bookmarkCount: Int) {
        logcat { "Bookmark-import: Showing success fragment with $bookmarkCount bookmarks" }
        val successFragment = ImportFinishedFragment.newInstanceSuccess(bookmarksImported = bookmarkCount)

        successFragment.setOnDoneCallback {
            finishWithSuccess(bookmarkCount)
        }

        replaceFragment(successFragment)
        isInResultState = true
    }

    private fun showErrorFragment(errorReason: UserCannotImportReason) {
        logcat { "Bookmark-import: Showing error fragment with reason: $errorReason" }

        val messageId =
            when (errorReason) {
                UserCannotImportReason.DownloadError -> R.string.importBookmarksErrorDownloadMessage
                UserCannotImportReason.ErrorParsingBookmarks -> R.string.importBookmarksErrorParsingMessage
                Unknown -> R.string.importBookmarksErrorGenericMessage
            }
        val errorFragment = ImportFinishedFragment.newInstanceFailure(getString(messageId))

        errorFragment.setOnDoneCallback {
            finishWithFailure(errorReason)
        }

        replaceFragment(errorFragment)
        isInResultState = true
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.commit {
            replace(R.id.fragment_container, fragment)
        }
    }

    private fun configureResultListeners() {
        supportFragmentManager.setFragmentResultListener(ImportGoogleBookmarkResult.Companion.RESULT_KEY, this) { _, result ->
            val importResult =
                BundleCompat.getParcelable(
                    result,
                    ImportGoogleBookmarkResult.RESULT_KEY_DETAILS,
                    ImportGoogleBookmarkResult::class.java,
                )
            handleWebFlowResult(importResult)
        }
    }

    private fun handleWebFlowStateChange(showWebView: Boolean) {
        if (showWebView) {
            logcat { "ImportGoogleBookmarksWebFlowActivity: WebFlow requests webview visibility" }
            hideProgressOverlay()
        } else {
            logcat { "ImportGoogleBookmarksWebFlowActivity: WebFlow requests progress visibility" }
            showProgressOverlay()
        }
    }

    private fun showProgressOverlay() {
        val progressFragment = supportFragmentManager.findFragmentByTag("progress_overlay")
        if (progressFragment == null) {
            logcat { "ImportGoogleBookmarksWebFlowActivity: Adding progress overlay" }
            supportFragmentManager
                .beginTransaction()
                .add(R.id.fragment_container, ImportGoogleBookmarksAutomationInProgressViewFragment.newInstance(), "progress_overlay")
                .commit()
        }
    }

    private fun hideProgressOverlay() {
        val progressFragment = supportFragmentManager.findFragmentByTag("progress_overlay")
        if (progressFragment != null) {
            logcat { "ImportGoogleBookmarksWebFlowActivity: Removing progress overlay" }
            supportFragmentManager
                .beginTransaction()
                .remove(progressFragment)
                .commit()
        }
    }

    private fun handleWebFlowResult(result: ImportGoogleBookmarkResult?) {
        when (result) {
            is ImportGoogleBookmarkResult.Success -> {
                logcat { "ImportGoogleBookmarksWebFlowActivity: WebFlow succeeded with ${result.importedCount} bookmarks" }
                showSuccessFragment(result.importedCount)
            }

            is ImportGoogleBookmarkResult.UserCancelled -> {
                logcat { "ImportGoogleBookmarksWebFlowActivity: User cancelled at ${result.stage}" }
                exitUserCancelled(result.stage)
            }

            is ImportGoogleBookmarkResult.Error -> {
                logcat { "ImportGoogleBookmarksWebFlowActivity: Import failed with reason: ${result.reason}" }
                showErrorFragment(result.reason)
            }

            null -> {
                logcat { "ImportGoogleBookmarksWebFlowActivity: Received null result" }
                showErrorFragment(Unknown)
            }
        }
    }

    private fun exitWithResult(resultBundle: Bundle) {
        setResult(RESULT_OK, Intent().putExtras(resultBundle))
        finish()
    }

    private fun finishWithSuccess(bookmarkCount: Int) {
        val result =
            Bundle().apply {
                putParcelable(
                    ImportGoogleBookmarkResult.Companion.RESULT_KEY_DETAILS,
                    ImportGoogleBookmarkResult.Success(bookmarkCount),
                )
            }
        exitWithResult(result)
    }

    private fun finishWithFailure(reason: UserCannotImportReason) {
        val result =
            Bundle().apply {
                putParcelable(
                    ImportGoogleBookmarkResult.Companion.RESULT_KEY_DETAILS,
                    ImportGoogleBookmarkResult.Error(reason),
                )
            }
        exitWithResult(result)
    }

    fun exitUserCancelled(stage: String) {
        val result =
            Bundle().apply {
                putParcelable(
                    ImportGoogleBookmarkResult.Companion.RESULT_KEY_DETAILS,
                    ImportGoogleBookmarkResult.UserCancelled(stage),
                )
            }
        exitWithResult(result)
    }

    private fun configureToolbar() {
        with(binding.includeToolbar.toolbar) {
            setupToolbar(this)
            setNavigationIcon(com.duckduckgo.mobile.android.R.drawable.ic_close_24)
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        setTitle(R.string.importBookmarksFromGoogleWebFlowTitle)
    }
}

object ImportGoogleBookmark {
    @Parcelize
    sealed class ImportViaGoogleTakeoutScreen(
        val launchSource: AutofillImportBookmarksLaunchSource,
    ) : ActivityParams,
        Parcelable

    @Parcelize
    data class AutofillImportViaGoogleTakeoutScreen(
        private val source: AutofillImportBookmarksLaunchSource,
    ) : ImportViaGoogleTakeoutScreen(source)

    @Parcelize
    data class AutofillImportViaGoogleTakeoutScreenResultSuccess(
        private val source: AutofillImportBookmarksLaunchSource,
        val bookmarkCount: Int,
    ) : ImportViaGoogleTakeoutScreen(source)

    @Parcelize
    data class AutofillImportViaGoogleTakeoutScreenResultError(
        private val source: AutofillImportBookmarksLaunchSource,
        val errorReason: UserCannotImportReason,
    ) : ImportViaGoogleTakeoutScreen(source)
}
