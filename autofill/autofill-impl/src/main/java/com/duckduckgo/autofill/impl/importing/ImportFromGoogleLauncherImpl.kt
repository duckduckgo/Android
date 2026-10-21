/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.autofill.impl.importing

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.os.BundleCompat
import com.duckduckgo.autofill.api.ImportFromGoogleAvailability
import com.duckduckgo.autofill.api.ImportFromGoogleLauncher
import com.duckduckgo.autofill.api.ImportFromGoogleLauncher.BookmarksImportRequest
import com.duckduckgo.autofill.impl.importing.takeout.webflow.ImportBookmarksViaGoogleTakeoutScreen
import com.duckduckgo.autofill.impl.importing.takeout.webflow.ImportGoogleBookmarkResult
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import dev.zacsweers.metro.ContributesBinding
import javax.inject.Inject
import com.duckduckgo.autofill.api.ImportFromGoogleLauncher.ImportFromGoogleResult as PublicResult

@ContributesBinding(AppScope::class)
class ImportFromGoogleLauncherImpl @Inject constructor(
    private val importFromGoogleAvailability: ImportFromGoogleAvailability,
    globalActivityStarter: GlobalActivityStarter,
) : ImportFromGoogleLauncher {
    private val contract = ImportBookmarksFromGoogleContract(globalActivityStarter)

    override fun bookmarksImportContract(): ActivityResultContract<BookmarksImportRequest, PublicResult> = contract

    override suspend fun launchBookmarksImport(launcher: ActivityResultLauncher<BookmarksImportRequest>) {
        launcher.launch(AvailabilityCheckedRequest(isAvailable = importFromGoogleAvailability.isBookmarksImportAvailable()))
    }
}

private class ImportBookmarksFromGoogleContract(
    private val globalActivityStarter: GlobalActivityStarter,
) : ActivityResultContract<BookmarksImportRequest, PublicResult>() {
    override fun getSynchronousResult(
        context: Context,
        input: BookmarksImportRequest
    ): SynchronousResult<PublicResult>? {
        val isAvailable = (input as? AvailabilityCheckedRequest)?.isAvailable == true
        return when {
            !isAvailable -> SynchronousResult(PublicResult.Unavailable)
            buildIntent(context) == null -> SynchronousResult(PublicResult.Unavailable)
            else -> null
        }
    }

    override fun createIntent(
        context: Context,
        input: BookmarksImportRequest
    ): Intent {
        return checkNotNull(buildIntent(context)) {
            "getSynchronousResult should return a result when there is no intent"
        }
    }

    override fun parseResult(
        resultCode: Int,
        intent: Intent?
    ): PublicResult {
        val data = intent?.extras ?: return PublicResult.UserCancelled

        val result = BundleCompat.getParcelable(
            data,
            ImportGoogleBookmarkResult.RESULT_KEY_DETAILS,
            ImportGoogleBookmarkResult::class.java,
        )

        return when (result) {
            is ImportGoogleBookmarkResult.Success -> PublicResult.Success(result.importedCount)
            is ImportGoogleBookmarkResult.UserCancelled, null -> PublicResult.UserCancelled
            is ImportGoogleBookmarkResult.Error -> PublicResult.Error
        }
    }

    private fun buildIntent(context: Context): Intent? {
        return globalActivityStarter.startIntent(context, ImportBookmarksViaGoogleTakeoutScreen(getLaunchSource()))
    }

    private fun getLaunchSource(): String {
        // launchSource can only be bookmarks screen or dev settings currently, so hardcoding to bookmarks screen for now
        return "bookmarks_screen"
    }
}

private data class AvailabilityCheckedRequest(val isAvailable: Boolean) : BookmarksImportRequest
