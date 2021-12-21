/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.model

import java.text.NumberFormat
import javax.inject.Inject
import javax.inject.Named

class DataSizeFormatter
@Inject
constructor(@Named("numberFormatterWithSeparator") val numberFormatter: NumberFormat) {

    fun format(bytes: Long): String {

        return when {
            (bytes >= BYTES_PER_GIGABYTE) -> {
                val formatted = (bytes.toDouble() / BYTES_PER_GIGABYTE)
                "${numberFormatter.format(formatted)} GB"
            }
            (bytes >= BYTES_PER_MEGABYTE) -> {
                val formatted = bytes.toDouble() / BYTES_PER_MEGABYTE
                "${numberFormatter.format(formatted)} MB"
            }
            (bytes >= BYTES_PER_KILOBYTE) -> {
                val formatted = bytes.toDouble() / BYTES_PER_KILOBYTE
                "${numberFormatter.format(formatted)} KB"
            }
            else -> "${numberFormatter.format(bytes)} bytes"
        }
    }

    companion object {
        private const val BYTES_PER_KILOBYTE = 1_000
        private const val BYTES_PER_MEGABYTE = 1_000_000
        private const val BYTES_PER_GIGABYTE = 1_000_000_000
    }
}
