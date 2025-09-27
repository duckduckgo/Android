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
import androidx.fragment.app.commit
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.impl.databinding.ActivityImportGoogleBookmarksWebflowBinding
import com.duckduckgo.autofill.impl.importing.takeout.webflow.ImportGoogleBookmark.AutofillImportViaGoogleTakeoutScreen
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(AutofillImportViaGoogleTakeoutScreen::class)
class ImportGoogleBookmarksWebFlowActivity : DuckDuckGoActivity() {
    val binding: ActivityImportGoogleBookmarksWebflowBinding by viewBinding()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        configureResultListeners()
        launchImportFragment()
    }

    private fun launchImportFragment() {
        supportFragmentManager.commit {
            replace(R.id.fragment_container, ImportGoogleBookmarksWebFlowFragment())
        }
    }

    private fun configureResultListeners() {
        supportFragmentManager.setFragmentResultListener(ImportGoogleBookmarkResult.Companion.RESULT_KEY, this) { _, result ->
            exitWithResult(result)
        }
    }

    private fun exitWithResult(resultBundle: Bundle) {
        setResult(RESULT_OK, Intent().putExtras(resultBundle))
        finish()
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
}

object ImportGoogleBookmark {
    data object AutofillImportViaGoogleTakeoutScreen : ActivityParams {
        private fun readResolve(): Any = AutofillImportViaGoogleTakeoutScreen
    }
}
