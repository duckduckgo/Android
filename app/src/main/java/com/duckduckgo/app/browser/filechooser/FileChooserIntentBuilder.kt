/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.browser.filechooser

import android.content.Intent
import timber.log.Timber
import javax.inject.Inject


class FileChooserIntentBuilder @Inject constructor() {

    fun intent(acceptTypes: Array<String>, canChooseMultiple: Boolean = false): Intent {
        return Intent(Intent.ACTION_GET_CONTENT).also {
            it.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            configureSelectableFileTypes(it, acceptTypes)
            configureAllowMultipleFile(it, canChooseMultiple)
        }
    }

    private fun configureSelectableFileTypes(intent: Intent, acceptTypes: Array<String>) {
        intent.type = "*/*"

        val acceptedMimeTypes = mutableSetOf<String>()

        acceptTypes
                .filter { it.isNotBlank() }
                .forEach { acceptedMimeTypes.add(it.toLowerCase()) }

        if (acceptedMimeTypes.isNotEmpty()) {
            Timber.d("Selectable file types limited to $acceptedMimeTypes")
            intent.putExtra(Intent.EXTRA_MIME_TYPES, acceptedMimeTypes.toTypedArray())
        } else {
            Timber.d("No selectable file type filters applied")
        }
    }

    private fun configureAllowMultipleFile(intent: Intent, canChooseMultiple: Boolean) {
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, canChooseMultiple)
    }
}