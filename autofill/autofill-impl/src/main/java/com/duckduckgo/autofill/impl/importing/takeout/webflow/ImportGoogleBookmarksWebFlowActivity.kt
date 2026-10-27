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
import androidx.activity.viewModels
import androidx.core.os.BundleCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.impl.databinding.ActivityImportGoogleBookmarksWebflowBinding
import com.duckduckgo.autofill.impl.importing.AutofillImportRedesignFeature
import com.duckduckgo.autofill.impl.importing.takeout.webflow.ImportGoogleBookmarksViewModel.Command
import com.duckduckgo.autofill.impl.importing.takeout.webflow.ImportGoogleBookmarksViewModel.ViewMode
import com.duckduckgo.autofill.impl.importing.takeout.webflow.ImportGoogleBookmarksViewModel.ViewState
import com.duckduckgo.codegen.annotations.ContributeToActivityStarter
import com.duckduckgo.codegen.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeHandler
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.navigation.api.getActivityParams
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import logcat.logcat
import javax.inject.Inject

/**
 * Launch the Google Bookmarks import flow
 */
data class ImportBookmarksViaGoogleTakeoutScreen(val launchSource: String) : GlobalActivityStarter.ActivityParams

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(ImportBookmarksViaGoogleTakeoutScreen::class, screenName = "importGoogleBookmarks")
class ImportGoogleBookmarksWebFlowActivity :
    DuckDuckGoActivity(),
    ImportGoogleBookmarksWebFlowFragment.WebViewVisibilityListener {

    @Inject
    lateinit var edgeToEdgeHandler: EdgeToEdgeHandler

    @Inject
    lateinit var importViewModelFactory: ImportGoogleBookmarksViewModel.Factory

    @Inject
    lateinit var importRedesignFeature: AutofillImportRedesignFeature

    val binding: ActivityImportGoogleBookmarksWebflowBinding by viewBinding()

    private val viewModel by viewModels<ImportGoogleBookmarksViewModel> {
        ImportGoogleBookmarksViewModel.Factory.Provider(importViewModelFactory, getLaunchSource(), isRedesignEnabled)
    }

    private var isRedesignEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // we keep the design the flow started with, so a remote flag update can't switch the UI mid-import after recreation
        isRedesignEnabled = if (savedInstanceState?.containsKey(KEY_IS_REDESIGN_ENABLED) == true) {
            savedInstanceState.getBoolean(KEY_IS_REDESIGN_ENABLED)
        } else {
            importRedesignFeature.self().isEnabled()
        }
        enableTransparentEdgeToEdge()
        setContentView(binding.root)
        configureToolbar()
        configureEdgeToEdgeInsets()
        configureResultListeners()
        observeViewModel()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_IS_REDESIGN_ENABLED, isRedesignEnabled)
    }

    private fun configureEdgeToEdgeInsets() {
        if (isRedesignEnabled) {
            edgeToEdgeHandler.applyStatusBarAndHorizontalInsets(binding.root)
        } else {
            edgeToEdgeHandler.applyHorizontalSystemBarInsets(binding.root)
            edgeToEdgeHandler.applyStatusBarInsets(binding.includeToolbar.appBarLayout)
        }
        edgeToEdgeHandler.applyNavigationBarInsets(binding.fragmentContainer, drawBehindGestureNav = true)
    }

    private fun observeViewModel() {
        viewModel.viewState
            .flowWithLifecycle(lifecycle)
            .onEach { renderViewState(it) }
            .launchIn(lifecycleScope)

        viewModel.commands
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { command -> processCommand(command) }
            .launchIn(lifecycleScope)
    }

    private fun renderViewState(viewState: ViewState) {
        when (viewState.viewMode) {
            is ViewMode.WebFlowLoading, is ViewMode.WebFlow -> {
                showWebFlowFragment()
                hideProgressOverlay()
            }

            is ViewMode.WaitingForExport -> {
                showWebFlowFragment()
                showProgressOverlay()
            }

            is ViewMode.Success -> showResultFragment {
                if (isRedesignEnabled) {
                    ImportFinishedRedesignFragment.newInstanceSuccess(
                        importedCount = viewState.viewMode.importedCount,
                        launchSource = getLaunchSource(),
                    )
                } else {
                    ImportFinishedFragment.newInstanceSuccess(
                        bookmarksImported = viewState.viewMode.importedCount,
                    )
                }
            }

            is ViewMode.Error -> showResultFragment {
                if (isRedesignEnabled) {
                    ImportFinishedRedesignFragment.newInstanceError(
                        launchSource = getLaunchSource(),
                    )
                } else {
                    ImportFinishedFragment.newInstanceFailure(getString(R.string.importBookmarksErrorGenericMessage))
                }
            }
        }
        binding.includeToolbar.appBarLayout.isVisible = !isRedesignEnabled || viewState.viewMode is ViewMode.WebFlow
        updateToolbarTitle(viewState.viewMode)
    }

    private fun showWebFlowFragment() {
        if (supportFragmentManager.findFragmentByTag(WEB_FLOW_TAG) != null) return
        logcat { "Bookmark-import: Starting webflow" }
        supportFragmentManager.commit {
            replace(R.id.fragment_container, ImportGoogleBookmarksWebFlowFragment(), WEB_FLOW_TAG)
        }
    }

    private fun showResultFragment(createFragment: () -> Fragment) {
        if (supportFragmentManager.findFragmentByTag(RESULT_TAG) != null) return
        logcat { "Bookmark-import: Showing result fragment" }
        supportFragmentManager.commit {
            replace(R.id.fragment_container, createFragment(), RESULT_TAG)
        }
    }

    private fun showProgressOverlay() {
        if (supportFragmentManager.findFragmentByTag(PROGRESS_OVERLAY_TAG) != null) return
        val progressFragment = if (isRedesignEnabled) {
            ImportGoogleBookmarksInProgressRedesignFragment.newInstance()
        } else {
            ImportGoogleBookmarksAutomationInProgressViewFragment.newInstance()
        }
        supportFragmentManager.commit {
            add(R.id.fragment_container, progressFragment, PROGRESS_OVERLAY_TAG)
        }
    }

    private fun hideProgressOverlay() {
        val progressFragment = supportFragmentManager.findFragmentByTag(PROGRESS_OVERLAY_TAG) ?: return
        supportFragmentManager.commit {
            remove(progressFragment)
        }
    }

    private fun processCommand(command: Command) {
        when (command) {
            is Command.Exit -> exitWithResult(command.result)
        }
    }

    private fun configureResultListeners() {
        supportFragmentManager.setFragmentResultListener(ImportGoogleBookmarkResult.RESULT_KEY, this) { _, result ->
            val importResult = BundleCompat.getParcelable(
                result,
                ImportGoogleBookmarkResult.RESULT_KEY_DETAILS,
                ImportGoogleBookmarkResult::class.java,
            )
            viewModel.onWebFlowFinished(importResult)
        }
        supportFragmentManager.setFragmentResultListener(ImportFinishedFragment.RESULT_KEY_DONE, this) { _, _ ->
            viewModel.onResultScreenDoneClicked()
        }
    }

    private fun exitWithResult(result: ImportGoogleBookmarkResult) {
        val resultBundle = Bundle().apply {
            putParcelable(ImportGoogleBookmarkResult.RESULT_KEY_DETAILS, result)
        }
        setResult(RESULT_OK, Intent().putExtras(resultBundle))
        finish()
    }

    private fun configureToolbar() {
        with(binding.includeToolbar.toolbar) {
            setupToolbar(this)
            setNavigationIcon(com.duckduckgo.mobile.android.R.drawable.ic_close_24)
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        // the redesign starts without a toolbar, and we can't wait for the first view state because it may arrive after the first frame
        binding.includeToolbar.appBarLayout.isVisible = !isRedesignEnabled
        binding.includeToolbar.topBrowserOutlineStroke.isVisible = !isRedesignEnabled
    }

    private fun updateToolbarTitle(viewMode: ViewMode) {
        val title = if (viewMode == ViewMode.WebFlowLoading || viewMode == ViewMode.WebFlow) {
            getString(R.string.importBookmarksFromGoogleWebFlowTitle)
        } else {
            ""
        }
        setTitle(title)
    }

    override fun showLoadingState() {
        viewModel.onWaitingForExportStarted()
    }

    override fun hideLoadingState() {
        viewModel.onWaitingForExportStopped()
    }

    private fun getLaunchSource(): String {
        return intent.getActivityParams(ImportBookmarksViaGoogleTakeoutScreen::class.java)?.launchSource ?: UNKNOWN_LAUNCH_SOURCE
    }

    companion object {
        private const val WEB_FLOW_TAG = "web_flow"
        private const val RESULT_TAG = "result"
        private const val PROGRESS_OVERLAY_TAG = "progress_overlay"
        private const val KEY_IS_REDESIGN_ENABLED = "is_redesign_enabled"
        private const val UNKNOWN_LAUNCH_SOURCE = "unknown"
    }
}
