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

package com.duckduckgo.app.global.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.text.Html.*
import android.text.Spanned
import androidx.core.content.ContextCompat

@Suppress("deprecation")
fun String.html(context: Context): Spanned {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        return fromHtml(this, FROM_HTML_MODE_COMPACT, ImageGetter { htmlDrawable(context, it.toInt()) }, null)
    }
    return fromHtml(this, ImageGetter { htmlDrawable(context, it.toInt()) }, null)
}

private fun htmlDrawable(context: Context, resource: Int): Drawable? {
    return ContextCompat.getDrawable(context, resource)?.also {
        it.setBounds(0, 0, it.intrinsicWidth, it.intrinsicHeight)
    }
}

private const val HTTPS_PREFIX = "https://"
private const val WWW_PREFIX = "www."
private const val WWW_SUFFIX = "/"

fun String.websiteFromGeoLocationsApiOrigin(): String {
    val uri = Uri.parse(this)
    val host = uri.host ?: return this

    return host.takeIf { it.startsWith(WWW_PREFIX, ignoreCase = true) }
        ?.drop(WWW_PREFIX.length) ?: host

}

fun String.asLocationPermissionOrigin(): String {
    return HTTPS_PREFIX + this + WWW_SUFFIX
}
