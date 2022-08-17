/*
 * Copyright (c) 2022 DuckDuckGo
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

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import java.io.File
import javax.inject.Inject

interface MediaScanner {
    /**
     * Triggers the Media Store scanner so that the newly added [File] is immediately visible to the user.
     */
    fun scan(file: File)
}

@ContributesBinding(AppScope::class)
class MediaScannerImpl @Inject constructor(
    private val context: Context,
) : MediaScanner {
    override fun scan(file: File) {
        val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
        mediaScanIntent.data = Uri.fromFile(file)
        context.sendBroadcast(mediaScanIntent)
    }
}
