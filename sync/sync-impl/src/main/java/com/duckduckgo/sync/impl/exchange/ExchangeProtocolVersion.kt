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

package com.duckduckgo.sync.impl.exchange

sealed class ExchangeProtocolVersion : Comparable<ExchangeProtocolVersion> {

    sealed class Supported : ExchangeProtocolVersion() {
        abstract val major: Int
        abstract val minor: Int

        final override fun toString() = if (minor == 0) "$major" else "$major.$minor"
    }

    data class V1(
        override val minor: Int,
    ) : Supported() {
        override val major get() = MAJOR

        companion object {
            const val MAJOR = 1
        }
    }

    data class V2(
        override val minor: Int,
    ) : Supported() {
        override val major get() = MAJOR

        companion object {
            const val MAJOR = 2
        }
    }

    data class Unsupported(
        val rawVersion: String,
    ) : ExchangeProtocolVersion() {
        override fun toString() = rawVersion
    }

    override fun compareTo(other: ExchangeProtocolVersion): Int = when {
        this is Supported && other is Supported -> compareValuesBy(this, other, Supported::major, Supported::minor)
        this is Unsupported && other is Unsupported -> rawVersion.compareTo(other.rawVersion)
        this is Supported -> -1
        else -> 1
    }

    companion object {
        val V1_0 = V1(minor = 0)
        val V2_0 = V2(minor = 0)
        val V2_1 = V2(minor = 1)

        fun parse(rawVersion: String): Result<ExchangeProtocolVersion> = runCatching {
            val components = rawVersion.split('.').map { it.toInt() }
            val major = components[0]
            val minor = components.getOrNull(1) ?: 0
            when (major) {
                V1.MAJOR -> V1(minor)
                V2.MAJOR -> V2(minor)
                else -> Unsupported(rawVersion)
            }
        }

        fun parseOrUnsupported(rawVersion: String): ExchangeProtocolVersion = parse(rawVersion).getOrElse { Unsupported(rawVersion) }
    }
}
