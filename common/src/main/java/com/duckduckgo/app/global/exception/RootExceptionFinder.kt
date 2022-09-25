/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.global.exception

class RootExceptionFinder {

    fun findRootException(throwable: Throwable?): Throwable? {
        var possibleRoot: Throwable? = throwable ?: return null
        var count = 0

        while (count < NESTED_EXCEPTION_THRESHOLD && possibleRoot?.cause != null) {
            possibleRoot = possibleRoot.cause
            count++
        }

        return possibleRoot
    }

    companion object {

        // arbitrary limit to protect against recursive nested Exceptions
        private const val NESTED_EXCEPTION_THRESHOLD = 20
    }
}
