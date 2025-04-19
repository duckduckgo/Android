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

package com.duckduckgo.library.loader

import android.content.Context
import com.getkeepsafe.relinker.ReLinker
import com.getkeepsafe.relinker.ReLinker.LoadListener

class LibraryLoader {
    companion object {
        fun loadLibrary(context: Context, name: String) {
            ReLinker.loadLibrary(context, name)
        }

        fun loadLibrary(context: Context, name: String, listener: LibraryLoaderListener) {
            ReLinker.loadLibrary(context, name, listener)
        }
    }

    interface LibraryLoaderListener : LoadListener
}
