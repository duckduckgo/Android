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

package com.duckduckgo.url.impl

import android.icu.text.IDNA

// Nontransitional to match Chromium: transitional processing maps ß→ss and ς→σ, which turns a host into a
// different registered domain than the one the WebView navigates to. STD3 rules stay off so underscores pass.
private val idna: IDNA = IDNA.getUTS46Instance(IDNA.NONTRANSITIONAL_TO_ASCII or IDNA.NONTRANSITIONAL_TO_UNICODE)

/**
 * Returns this host lowercased and, if internationalized, in its ASCII (Punycode) form, so `Example.com`,
 * `ça.com` and `xn--a-5fa.com` each compare equal to their canonical spelling. All-ASCII hosts, which are
 * almost all of them, skip IDNA. A host IDNA rejects is kept (lowercased) rather than dropped.
 */
internal fun String.normalizedHost(): String {
    if (all { it.code < 0x80 }) return lowercase()
    val ascii = StringBuilder()
    val info = IDNA.Info()
    idna.nameToASCII(this, ascii, info)
    return if (info.hasErrors()) lowercase() else ascii.toString()
}
