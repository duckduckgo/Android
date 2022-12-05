/*
 * Copyright © 2017-2021 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.wireguard.config

import java.lang.StringBuilder
import java.util.regex.Pattern

class Attribute private constructor(
    val key: String,
    val value: String,
) {

    companion object {
        private val LINE_PATTERN = Pattern.compile("(\\w+)\\s*=\\s*([^\\s#][^#]*)")
        private val LIST_SEPARATOR = Pattern.compile("\\s*,\\s*")

        @JvmStatic
        fun join(values: Iterable<*>): String {
            val it = values.iterator()
            if (!it.hasNext()) {
                return ""
            }
            val sb = StringBuilder()
            sb.append(it.next())
            while (it.hasNext()) {
                sb.append(", ")
                sb.append(it.next())
            }
            return sb.toString()
        }

        @JvmStatic
        fun parse(line: CharSequence): Attribute? {
            val matcher = LINE_PATTERN.matcher(line)
            return if (!matcher.matches()) null else Attribute(matcher.group(1)!!, matcher.group(2)!!)
        }

        @JvmStatic
        fun split(value: CharSequence): Array<String> {
            return LIST_SEPARATOR.split(value)
        }
    }
}
