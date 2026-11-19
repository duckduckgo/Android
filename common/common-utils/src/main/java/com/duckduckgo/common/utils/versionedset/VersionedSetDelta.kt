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

package com.duckduckgo.common.utils.versionedset

data class VersionedSetDelta<T>(
    val insert: Set<T>,
    val delete: Set<T>,
    val revision: Int,
    val replace: Boolean,
)

sealed interface VersionedSetUpdate<out T> {
    data object Ignore : VersionedSetUpdate<Nothing>

    data class Replace<T>(
        val insert: Set<T>,
        val revision: Int,
    ) : VersionedSetUpdate<T>

    data class Delta<T>(
        val insert: Set<T>,
        val delete: Set<T>,
        val revision: Int,
    ) : VersionedSetUpdate<T>
}

fun <T> VersionedSetDelta<T>.updateFrom(currentRevision: Int): VersionedSetUpdate<T> {
    if (revision <= currentRevision) return VersionedSetUpdate.Ignore

    return if (replace) {
        VersionedSetUpdate.Replace(insert, revision)
    } else {
        VersionedSetUpdate.Delta(insert, delete, revision)
    }
}
