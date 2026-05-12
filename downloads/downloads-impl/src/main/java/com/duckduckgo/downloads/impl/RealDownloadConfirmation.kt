/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.downloads.impl

import android.os.Bundle
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.downloads.api.DownloadConfirmation
import com.duckduckgo.downloads.api.FileDownloader.PendingFileDownload
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class RealDownloadConfirmation @Inject constructor() : DownloadConfirmation {
    override fun instance(pendingDownload: PendingFileDownload): BottomSheetDialogFragment {
        val fragment = DownloadConfirmationFragment()
        fragment.isCancelable = false
        val key = PendingDownloadStore.put(pendingDownload)
        fragment.arguments = Bundle().apply {
            putString(PENDING_DOWNLOAD_KEY, key)
        }
        return fragment
    }

    companion object {
        const val PENDING_DOWNLOAD_KEY = "PENDING_DOWNLOAD_KEY"
    }
}
