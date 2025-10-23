package com.duckduckgo.autofill.impl.importing.takeout.webflow

import androidx.core.net.toUri
import com.duckduckgo.autofill.impl.importing.takeout.processor.BookmarkImportProcessor.ImportResult
import com.duckduckgo.autofill.impl.importing.takeout.processor.BookmarkImportProcessor.ImportResult.Error
import com.duckduckgo.autofill.impl.importing.takeout.processor.BookmarkImportProcessor.ImportResult.Success
import com.duckduckgo.autofill.impl.importing.takeout.webflow.BookmarkImportWebFlowStepObserver.Step
import com.duckduckgo.di.scopes.FragmentScope
import com.squareup.anvil.annotations.ContributesBinding
import logcat.logcat
import javax.inject.Inject

interface BookmarkImportWebFlowStepObserver {
    fun getCurrentStep(): String
    fun startFlow()
    fun updateStep(step: Step)

    sealed interface Step {
        data class UrlVisited(val url: String?) : Step
        data class JavascriptStep(val name: String) : Step
        data object DownloadDetected : Step
        data class ImportFinished(val result: ImportResult) : Step
    }
}

@ContributesBinding(FragmentScope::class)
class BookmarkImportWebFlowStepObserverImpl @Inject constructor() : BookmarkImportWebFlowStepObserver {

    private var latestStepInWebFlow: String = STEP_UNINITIALIZED
    private var hasVisitedLogin: Boolean = false
    private var hasVisitedTakeout: Boolean = false

    override fun getCurrentStep(): String = latestStepInWebFlow

    override fun startFlow() {
        latestStepInWebFlow = STEP_UNINITIALIZED
        hasVisitedLogin = false
        hasVisitedTakeout = false
        logcat { "Bookmark-import: flow started, flags reset" }
    }

    override fun updateStep(step: Step) {
        when (step) {
            is Step.DownloadDetected -> updateStepToDownloadDetected()
            is Step.ImportFinished -> updateStepFromImportResult(step.result)
            is Step.JavascriptStep -> updateLatestStepSpecificStage(step.name)
            is Step.UrlVisited -> updateStepFromUrl(step.url)
        }
    }

    private fun updateLatestStepSpecificStage(step: String) {
        latestStepInWebFlow = step
        logcat { "Bookmark-import: latest step is: $step" }
    }

    private fun updateStepFromUrl(url: String?) {
        val host = url?.toUri()?.host ?: return

        when {
            host.contains(TAKEOUT_ADDRESS, ignoreCase = true) -> updateLatestStepTakeoutReached()
            host.contains(ACCOUNTS_ADDRESS, ignoreCase = true) -> updateLatestStepLoginPage()
            else -> updateLatestStepSpecificStage(STEP_UNKNOWN_URL)
        }
    }

    private fun updateStepToDownloadDetected() {
        updateLatestStepSpecificStage(STEP_DOWNLOAD_DETECTED)
    }

    private fun updateStepFromImportResult(importResult: ImportResult) {
        val step = when (importResult) {
            is Success -> STEP_IMPORT_SUCCESS
            is Error.DownloadError -> STEP_IMPORT_ERROR_DOWNLOAD
            is Error.ParseError -> STEP_IMPORT_ERROR_PARSE
            is Error.ImportError -> STEP_IMPORT_ERROR_WHILE_IMPORTING
        }
        updateLatestStepSpecificStage(step)
    }

    private fun updateLatestStepTakeoutReached() {
        if (latestStepInWebFlow == STEP_GOOGLE_TAKEOUT_PAGE_FIRST || latestStepInWebFlow == STEP_GOOGLE_TAKEOUT_PAGE_REPEATED) {
            return
        }

        if (!hasVisitedTakeout) {
            hasVisitedTakeout = true
            updateLatestStepSpecificStage(STEP_GOOGLE_TAKEOUT_PAGE_FIRST)
        } else {
            updateLatestStepSpecificStage(STEP_GOOGLE_TAKEOUT_PAGE_REPEATED)
        }
    }

    private fun updateLatestStepLoginPage() {
        if (latestStepInWebFlow == STEP_GOOGLE_ACCOUNTS_PAGE_FIRST || latestStepInWebFlow == STEP_GOOGLE_ACCOUNTS_REPEATED) {
            return
        }

        if (!hasVisitedLogin) {
            hasVisitedLogin = true
            updateLatestStepSpecificStage(STEP_GOOGLE_ACCOUNTS_PAGE_FIRST)
        } else {
            updateLatestStepSpecificStage(STEP_GOOGLE_ACCOUNTS_REPEATED)
        }
    }

    companion object {
        private const val STEP_GOOGLE_TAKEOUT_PAGE_FIRST = "takeout-first"
        private const val STEP_GOOGLE_TAKEOUT_PAGE_REPEATED = "takeout-repeat"
        private const val STEP_GOOGLE_ACCOUNTS_PAGE_FIRST = "login-first"
        private const val STEP_GOOGLE_ACCOUNTS_REPEATED = "login-repeat"
        private const val STEP_UNINITIALIZED = "uninitialized"
        private const val STEP_IMPORT_SUCCESS = "completed-successful"
        private const val STEP_IMPORT_ERROR_PARSE = "completed-failure-parse"
        private const val STEP_IMPORT_ERROR_DOWNLOAD = "completed-failure-download"
        private const val STEP_IMPORT_ERROR_WHILE_IMPORTING = "completed-failure-import"
        private const val STEP_DOWNLOAD_DETECTED = "download-detected"
        private const val STEP_UNKNOWN_URL = "unknown-url"
        private const val TAKEOUT_ADDRESS = "takeout.google.com"
        private const val ACCOUNTS_ADDRESS = "accounts.google.com"
    }
}
