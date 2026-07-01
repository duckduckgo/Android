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

package com.duckduckgo.downloads.impl.location

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.downloads.api.DownloadFileAccessor
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.io.File
import javax.inject.Inject

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealDownloadFileAccessor @Inject constructor(
    private val context: Context,
    private val safDownloadStorage: SafDownloadStorage,
) : DownloadFileAccessor {

    override fun exists(filePath: String): Boolean {
        return if (isContentUri(filePath)) {
            DocumentFile.fromSingleUri(context, Uri.parse(filePath))?.exists() == true
        } else {
            File(filePath).exists()
        }
    }

    override fun delete(filePath: String): Boolean {
        return if (isContentUri(filePath)) {
            safDownloadStorage.deleteFile(Uri.parse(filePath))
        } else {
            File(filePath).delete()
        }
    }

    override fun isContentUri(filePath: String): Boolean {
        return filePath.startsWith(CONTENT_URI_SCHEME)
    }

    companion object {
        private const val CONTENT_URI_SCHEME = "content://"
    }
}
