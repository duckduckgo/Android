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

package com.duckduckgo.autofill.impl.importing

import android.content.Context
import android.content.Intent
import androidx.core.os.BundleCompat
import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.api.ImportFromGoogle
import com.duckduckgo.autofill.impl.importing.takeout.webflow.ImportBookmarksViaGoogleTakeoutScreen
import com.duckduckgo.autofill.impl.importing.takeout.webflow.ImportGoogleBookmarkResult
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import javax.inject.Inject
import com.duckduckgo.autofill.api.ImportFromGoogle.ImportFromGoogleResult as PublicResult

@ContributesBinding(AppScope::class)
class ImportFromGoogleImpl @Inject constructor(
    private val autofillFeature: AutofillFeature,
    private val dispatchers: DispatcherProvider,
    private val globalActivityStarter: GlobalActivityStarter,
    private val context: Context,
) : ImportFromGoogle {

    override suspend fun getBookmarksImportLaunchIntent(): Intent? {
        return withContext(dispatchers.io()) {
            if (autofillFeature.canImportBookmarksFromGoogleTakeout().isEnabled()) {
                globalActivityStarter.startIntent(context, ImportBookmarksViaGoogleTakeoutScreen)
            } else {
                null
            }
        }
    }

    override suspend fun parseResult(intent: Intent?): PublicResult {
        return withContext(dispatchers.io()) {
            val data = intent?.extras
            if (data == null) {
                return@withContext PublicResult.UserCancelled
            }

            val result = BundleCompat.getParcelable(
                data,
                ImportGoogleBookmarkResult.RESULT_KEY_DETAILS,
                ImportGoogleBookmarkResult::class.java,
            )

            when (result) {
                is ImportGoogleBookmarkResult.Success -> PublicResult.Success(result.importedCount)
                is ImportGoogleBookmarkResult.UserCancelled, null -> PublicResult.UserCancelled
                is ImportGoogleBookmarkResult.Error -> PublicResult.Error
            }
        }
    }
}
