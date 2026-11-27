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
package com.duckduckgo.nextsteps.impl.ui

import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.signature.ObjectKey
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import java.io.File

/**
 * Shows the item image from the file downloaded with the message, falling back to [imageUrl] when the download
 * failed, and hides the view when the item has no image. Rebinding the same source is a no-op.
 */
internal fun ImageView.loadNextStepsItemImage(
    imageUrl: String?,
    imageFilePath: String?,
) {
    if (imageUrl.isNullOrEmpty()) {
        Glide.with(this).clear(this)
        tag = null
        gone()
        return
    }
    val source = imageFilePath ?: imageUrl
    if (tag == source) return
    tag = source
    show()
    val localFile = imageFilePath?.let { File(it) }
    Glide.with(this)
        .load(localFile ?: imageUrl)
        .fitCenter()
        .let {
            if (localFile != null) {
                it.signature(ObjectKey(localFile.lastModified()))
            } else {
                it
            }
        }
        .into(this)
}
